package com.z.bot.platform.dify;

import com.z.bot.adapter.callback.CompletionStreamCallback;
import com.z.bot.adapter.model.chat.*;
import com.z.bot.adapter.model.common.Metadata;
import com.z.bot.adapter.model.common.RetrieverResource;
import com.z.bot.adapter.model.common.SimpleResponse;
import com.z.bot.adapter.model.completion.CompletionRequest;
import com.z.bot.adapter.model.completion.CompletionResponse;
import com.z.bot.adapter.model.datasets.*;
import com.z.bot.adapter.model.file.FileUploadRequest;
import com.z.bot.adapter.model.file.FileUploadResponse;
import com.z.bot.adapter.model.workflow.WorkflowRunRequest;
import com.z.bot.adapter.model.workflow.WorkflowRunResponse;
import com.z.bot.adapter.util.JsonUtils;
import com.z.bot.platform.AbstractAIService;
import com.z.bot.platform.dify.config.DifyProperties;
import io.github.imfangs.dify.client.*;
import io.github.imfangs.dify.client.callback.ChatStreamCallback;
import io.github.imfangs.dify.client.callback.ChatflowStreamCallback;
import io.github.imfangs.dify.client.callback.WorkflowStreamCallback;
import io.github.imfangs.dify.client.enums.ResponseMode;
import io.github.imfangs.dify.client.event.*;
import io.github.imfangs.dify.client.exception.DifyApiException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;


@Component
@ConditionalOnProperty(name = "llm.type", havingValue = "dify")
@Slf4j
@Data
public class DifyServiceAdapter extends AbstractAIService {

    @Autowired
    private DifyProperties appConfig;

    @Autowired
    OkHttpClient okHttpClient;

    @Override
    public Flux<Map<String, Object>> sendChatFlowMessageStream(ChatMessage msg) {
        // 创建聊天消息
        io.github.imfangs.dify.client.model.chat.ChatMessage message = io.github.imfangs.dify.client.model.chat.ChatMessage.builder()
                .query(msg.getQuery())
                .user(msg.getUser())
                .conversationId(msg.getConversationId())
                .inputs(msg.getInputs())
                .responseMode(io.github.imfangs.dify.client.enums.ResponseMode.STREAMING)
                .build();
        return Flux.create((FluxSink<Map<String, Object>> emitter) -> {
                    // 创建客户端实例
                    DifyChatflowClient chatClient = DifyClientFactory.createChatWorkflowClient(getAppConfig().getBaseUrl(), msg.getApiKey());

                    // 注册回调
                    ChatflowStreamCallback callback = new ChatflowStreamCallback() {
                        @Override
                        public void onMessage(MessageEvent event) {
                            log.debug("收到消息片段: {}", event);
                            Map map = JsonUtils.jsonToMap(JsonUtils.toJson(event));
                            map.put("message_id", map.get("id"));
                            emitter.next(map);
                        }
                        @Override
                        public void onNodeFinished(NodeFinishedEvent event) {
                            log.debug("收到node结束片段: {}", event);
                            NodeFinishedEvent.NodeFinishedData nodeFinishedData = event.getData();
                            Map outputs = nodeFinishedData.getOutputs();
                            Map map = new HashMap();
                            map.put("outputs",outputs);
                            map.put("event","node_finished");
                            emitter.next(map);
                        }

                        @Override
                        public void onMessageEnd(MessageEndEvent event) {
                            log.debug("消息结束: {}", event);
                            Map map = JsonUtils.jsonToMap(JsonUtils.toJson(event));
                            map.put("message_id", map.get("id"));
                            emitter.next(map);
                            emitter.complete();
                        }

                        @Override
                        public void onError(ErrorEvent event) {
                            log.error("错误: {}", event.getMessage());
                            Map r = new HashMap();
                            r.put("status", "failed");
                            r.put("error",event);
                            emitter.next(r);
                            emitter.complete();
                        }

                        @Override
                        public void onException(Throwable throwable) {
                            log.error("异常: ", throwable);
                            emitter.error(throwable);
                        }
                    };

                    // 发起流式请求
                    try {
                        chatClient.sendChatMessageStream(message, callback);
                    } catch (IOException e) {
                        emitter.error(new RuntimeException("连接失败", e));
                    } catch (DifyApiException e) {
                        throw new RuntimeException(e);
                    }

                    // 取消订阅时清理资源
                    emitter.onCancel(() -> closeClient(chatClient));
                    emitter.onDispose(() -> closeClient(chatClient));
                })
                .subscribeOn(Schedulers.boundedElastic()) // 指定异步线程
                .timeout(Duration.ofSeconds(30))         // 超时控制
                .onErrorResume(this::handleError);
    }

    @Override
    public Flux<Map<String, Object>> sendChatMessageStream(ChatMessage msg) {
        // 创建聊天消息

        io.github.imfangs.dify.client.model.chat.ChatMessage message = io.github.imfangs.dify.client.model.chat.ChatMessage.builder()
                .query(msg.getQuery())
                .user(msg.getUser())
                .responseMode(io.github.imfangs.dify.client.enums.ResponseMode.STREAMING)
                .build();
        return Flux.create((FluxSink<Map<String, Object>> emitter) -> {
                    // 创建客户端实例
                    DifyChatClient chatClient = DifyClientFactory.createChatClient(getAppConfig().getBaseUrl(), msg.getApiKey());

                    // 注册回调
                    ChatStreamCallback callback = new ChatStreamCallback() {
                        @Override
                        public void onMessage(MessageEvent event) {
                            log.debug("收到消息片段: {}", event);
                            emitter.next(JsonUtils.jsonToMap(JsonUtils.toJson(event)));
                        }

                        @Override
                        public void onMessageEnd(MessageEndEvent event) {
                            log.debug("消息结束，完整消息ID: " + event);
                            emitter.next(JsonUtils.jsonToMap(JsonUtils.toJson(event)));
                            emitter.complete();
                        }

                        @Override
                        public void onError(ErrorEvent event) {
                            System.err.println("错误: " + event.getMessage());
                            emitter.next(JsonUtils.jsonToMap(JsonUtils.toJson(event)));
                            emitter.complete();
                        }

                        @Override
                        public void onException(Throwable throwable) {
                            System.err.println("异常: " + throwable.getMessage());
                            emitter.error(throwable);
                        }
                    };

                    // 发起流式请求
                    try {
                        chatClient.sendChatMessageStream(message, callback);
                    } catch (IOException e) {
                        emitter.error(new RuntimeException("连接失败", e));
                    } catch (DifyApiException e) {
                        throw new RuntimeException(e);
                    }

                    // 取消订阅时清理资源
                    emitter.onCancel(() -> closeClient(chatClient));
                    emitter.onDispose(() -> closeClient(chatClient));
                })
                .subscribeOn(Schedulers.boundedElastic()) // 指定异步线程
                .timeout(Duration.ofSeconds(30))         // 超时控制
                .onErrorResume(this::handleError);
    }

    // 资源清理方法
    private void closeClient(DifyChatClient client) {
        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception e) {
            log.error("关闭客户端失败", e);
        }
    }

    private void closeClient(DifyWorkflowClient client){
        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception e) {
            log.error("关闭客户端失败", e);
        }
    }
    // 错误统一处理
    private Flux<Map<String, Object>> handleError(Throwable throwable) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("type", "system_error");
        map.put("message", throwable.getMessage());
        map.put("is_end", true);
        return Flux.just(map);
    }

    @Override
    public MessageListResponse getMessages(String conversationId, String user, String firstId, Integer limit, String apiKey) {

        DifyChatClient chatClient = DifyClientFactory.createChatClient(getAppConfig().getBaseUrl(), apiKey);
        try {
            io.github.imfangs.dify.client.model.chat.MessageListResponse messages = chatClient.getMessages(conversationId, user, firstId, limit);
            MessageListResponse messageListResponse = new MessageListResponse();
            BeanUtils.copyProperties(messages, messageListResponse);
            return messageListResponse;
        } catch (IOException | DifyApiException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SimpleResponse deleteConversation(ChatMessage msg){
        DifyChatClient chatClient = DifyClientFactory.createChatClient(getAppConfig().getBaseUrl(), msg.getApiKey());
        try {
            io.github.imfangs.dify.client.model.common.SimpleResponse simpleResponse = chatClient.deleteConversation(msg.getConversationId(), msg.getUser());
            SimpleResponse response = new SimpleResponse();
            response.setResult(simpleResponse.getResult());
            return response;
        } catch (IOException | DifyApiException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public AudioToTextResponse audioToText(File file, String user, String apiKey) throws IOException, DifyApiException {
        if(Objects.isNull(file)){
            throw new RuntimeException("音频文件不存在，请检查");
        }
        DifyChatClient chatClient = DifyClientFactory.createChatClient(getAppConfig().getBaseUrl(), apiKey);
        io.github.imfangs.dify.client.model.chat.AudioToTextResponse audioToTextResponse = chatClient.audioToText(file, user);
        AudioToTextResponse audioToTextResponse1 = new AudioToTextResponse();
        BeanUtils.copyProperties(audioToTextResponse, audioToTextResponse1);
        return audioToTextResponse1;
    }

    @Override
    public ChatMessageResponse sendChatMessage(ChatMessage msg){
        io.github.imfangs.dify.client.model.chat.ChatMessage message = io.github.imfangs.dify.client.model.chat.ChatMessage.builder()
                .query(msg.getQuery())
                .user(msg.getUser())
                .conversationId(msg.getConversationId())
                .responseMode(ResponseMode.BLOCKING)
                .inputs(msg.getInputs())
                .build();

        DifyChatClient chatClient = DifyClientFactory.createChatClient(getAppConfig().getBaseUrl(), msg.getApiKey(), okHttpClient);

        try {
            io.github.imfangs.dify.client.model.chat.ChatMessageResponse data = chatClient.sendChatMessage(message);
            ChatMessageResponse chatMessageResponse = new ChatMessageResponse();
            chatMessageResponse.setMessageId(data.getMessageId());
            chatMessageResponse.setMode(data.getMode());
            chatMessageResponse.setAnswer(data.getAnswer());
            chatMessageResponse.setConversationId(data.getConversationId());
            chatMessageResponse.setCreatedAt(data.getCreatedAt());
            io.github.imfangs.dify.client.model.common.Metadata metadata = data.getMetadata();
            if(Objects.isNull(metadata)){
                log.info("com.z.bot.platform.dify.DifyServiceAdapter.sendChatMessage未返回metadata信息");
                return chatMessageResponse;
            }
            Metadata metadata1 = new Metadata();
            com.z.bot.adapter.model.common.Usage usage = new com.z.bot.adapter.model.common.Usage();
            BeanUtils.copyProperties(usage,metadata.getUsage());
            List<RetrieverResource> retrieverResources = new ArrayList<>();
//            BeanUtils.copyProperties(retrieverResources,metadata.getRetrieverResources());
            if(Objects.isNull(metadata.getRetrieverResources())){
                log.info("com.z.bot.platform.dify.DifyServiceAdapter.sendChatMessage未返回metadata.getRetrieverResources()信息");
                return chatMessageResponse;
            }
            for (io.github.imfangs.dify.client.model.common.RetrieverResource retrieverResource : metadata.getRetrieverResources()) {
                RetrieverResource rr = new RetrieverResource();
                rr.setContent(retrieverResource.getContent());
                rr.setScore(retrieverResource.getScore());
                rr.setPosition(retrieverResource.getPosition());
                rr.setDocumentId(retrieverResource.getDatasetId());
                rr.setDatasetId(retrieverResource.getDatasetId());
                rr.setDatasetName(retrieverResource.getDatasetName());
                rr.setDocumentName(retrieverResource.getDocumentName());
                rr.setSegmentId(retrieverResource.getSegmentId());
                retrieverResources.add(rr);
            }
            metadata1.setRetrieverResources(retrieverResources);
            chatMessageResponse.setMetadata(metadata1);
            return chatMessageResponse;
        } catch (IOException | DifyApiException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public WorkflowRunResponse runWorkflow(WorkflowRunRequest r) throws IOException {
        io.github.imfangs.dify.client.model.workflow.WorkflowRunRequest request = io.github.imfangs.dify.client.model.workflow.WorkflowRunRequest.builder()
                .inputs(r.getInputs())
                .responseMode(ResponseMode.BLOCKING)
                .user(r.getUser())
                .build();
        // 创建客户端实例
        DifyWorkflowClient workflowClient = DifyClientFactory.createWorkflowClient(getAppConfig().getBaseUrl(), r.getApiKey());
        try {
            io.github.imfangs.dify.client.model.workflow.WorkflowRunResponse response = workflowClient.runWorkflow(request);
            WorkflowRunResponse workflowRunResponse = new WorkflowRunResponse();
            workflowRunResponse.setWorkflowRunId(response.getWorkflowRunId());
            workflowRunResponse.setTaskId(response.getTaskId());
            io.github.imfangs.dify.client.model.workflow.WorkflowRunResponse.WorkflowRunData workflowRunData = response.getData();
            WorkflowRunResponse.WorkflowRunData data = new WorkflowRunResponse.WorkflowRunData();
            BeanUtils.copyProperties(workflowRunData, data);
            workflowRunResponse.setData(data);
            return workflowRunResponse;
        } catch (IOException | DifyApiException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Flux<Map<String, Object>> runWorkflowStream(WorkflowRunRequest r) throws IOException {
        io.github.imfangs.dify.client.model.workflow.WorkflowRunRequest request = io.github.imfangs.dify.client.model.workflow.WorkflowRunRequest.builder()
                .inputs(r.getInputs())
                .responseMode(ResponseMode.STREAMING)
                .user(r.getUser())
                .build();
        return Flux.create((FluxSink<Map<String, Object>> emitter) -> {
                    // 创建客户端实例
                    DifyWorkflowClient workflowClient = DifyClientFactory.createWorkflowClient(getAppConfig().getBaseUrl(), r.getApiKey());
                    // 注册回调
                        WorkflowStreamCallback callback = new WorkflowStreamCallback() {
                            @Override
                            public void onWorkflowStarted(WorkflowStartedEvent event) {
                                log.debug("工作流开始: " + event);
                            }

                            @Override
                            public void onNodeStarted(NodeStartedEvent event) {
                                log.debug("节点开始: " + event);
                            }

                            @Override
                            public void onNodeFinished(NodeFinishedEvent event) {
                                log.debug("节点完成: " + event);
                            }

                            @Override
                            public void onWorkflowFinished(WorkflowFinishedEvent event) {
                                log.debug("工作流完成: " + event);
                                emitter.next(JsonUtils.jsonToMap(JsonUtils.toJson(event)));
                                emitter.complete();
                            }

                            @Override
                            public void onWorkflowTextChunk(WorkflowTextChunkEvent event) {
                                log.debug("工作流DDL执行过程: " + event);
                                emitter.next(JsonUtils.jsonToMap(JsonUtils.toJson(event)));
                            }

                            @Override
                            public void onTtsMessage(TtsMessageEvent event) {
                                log.debug("收到TTS消息: " + event);
                            }

                            @Override
                            public void onTtsMessageEnd(TtsMessageEndEvent event) {
                                log.debug("TTS消息结束: " + event);
                            }

                            @Override
                            public void onError(ErrorEvent event) {
                                log.debug("错误事件: " + event);
                                System.err.println("错误: " + event.getMessage());
                                emitter.next(JsonUtils.jsonToMap(JsonUtils.toJson(event)));
                                emitter.complete();
                            }

                            @Override
                            public void onPing(PingEvent event) {
                                log.debug("心跳: " + event);
                            }

                            @Override
                            public void onException(Throwable throwable) {
                                log.debug("异常：" + throwable.getMessage());
                                System.err.println("异常: " + throwable.getMessage());
                                emitter.error(throwable);
                            }
                        };

                    // 发起流式请求
                    try {
                        workflowClient.runWorkflowStream(request, callback);
                    } catch (IOException e) {
                        emitter.error(new RuntimeException("连接失败", e));
                    } catch (DifyApiException e) {
                        throw new RuntimeException(e);
                    }
                    // 取消订阅时清理资源
                    emitter.onCancel(() -> closeClient(workflowClient));
                    emitter.onDispose(() -> closeClient(workflowClient));
                })
                .subscribeOn(Schedulers.boundedElastic()) // 指定异步线程
                .timeout(Duration.ofSeconds(30))         // 超时控制
                .onErrorResume(this::handleError);
    }
    @Override
    public FileUploadResponse uploadFile(File file, String user) throws IOException{

        return null;
    }

    @Override
    public FileUploadResponse uploadFile(FileUploadRequest request, File file) throws IOException {
        return null;
    }

    @Override
    public FileUploadResponse uploadFile(FileUploadRequest r, InputStream inputStream, String fileName) throws IOException {
        io.github.imfangs.dify.client.model.file.FileUploadRequest request = io.github.imfangs.dify.client.model.file.FileUploadRequest.builder()
                .user(r.getUser())
                .build();
        DifyBaseClient difyBaseClient = DifyClientFactory.createClient(getAppConfig().getBaseUrl(),r.getApiKey());
        try {
            io.github.imfangs.dify.client.model.file.FileUploadResponse fileUploadResponse = difyBaseClient.uploadFile(request,inputStream,fileName);
            FileUploadResponse response = new FileUploadResponse();
            response.setId(fileUploadResponse.getId());
            return response;
        } catch (DifyApiException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletionResponse sendCompletionMessage(CompletionRequest request) throws IOException {
        return null;
    }

    @Override
    public void sendCompletionMessageStream(CompletionRequest request, CompletionStreamCallback callback) throws IOException {

    }


    /**
     * 创建空知识库
     *
     * @param request 创建知识库请求
     * @return 知识库信息
     * @throws IOException IO异常
     */
    @Override
    public DatasetResponse createDataset(CreateDatasetRequest request) throws IOException {
        // 创建知识库请求
        io.github.imfangs.dify.client.model.datasets.CreateDatasetRequest datasetRequest = io.github.imfangs.dify.client.model.datasets.CreateDatasetRequest.builder()
                .name(request.getName())
                .description(request.getDescription())
                .indexingTechnique(request.getIndexingTechnique())
                .permission(request.getPermission())
                .provider(request.getProvider())
                .build();
        // 发送请求
        DifyDatasetsClient datasetsClient = DifyClientFactory.createDatasetsClient(appConfig.getBaseUrl(), appConfig.getDatasetApiKey());
        try {
            io.github.imfangs.dify.client.model.datasets.DatasetResponse datasetResponse = datasetsClient.createDataset(datasetRequest);
            DatasetResponse response = new DatasetResponse();
            BeanUtils.copyProperties(datasetResponse, response);
            return response;
        } catch (DifyApiException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 获取知识库列表
     *
     * @param page  页码
     * @param limit 每页数量
     * @return 知识库列表
     * @throws IOException IO异常
     */
    @Override
    public DatasetListResponse getDatasets(Integer page, Integer limit) throws IOException {
        DifyDatasetsClient datasetsClient = DifyClientFactory.createDatasetsClient(appConfig.getBaseUrl(), appConfig.getDatasetApiKey());
        try {
            io.github.imfangs.dify.client.model.datasets.DatasetListResponse datasets = datasetsClient.getDatasets(page, limit);
            DatasetListResponse response = new DatasetListResponse();
            List<io.github.imfangs.dify.client.model.datasets.DatasetListResponse.DatasetInfo> data = datasets.getData();
            List<DatasetListResponse.DatasetInfo> collect = data.stream().map(event -> {
                DatasetListResponse.DatasetInfo info = new DatasetListResponse.DatasetInfo();
                BeanUtils.copyProperties(event, info);
                return info;
            }).collect(Collectors.toList());
            response.setData(collect);
            response.setLimit(datasets.getLimit());
            response.setPage(datasets.getPage());
            response.setTotal(datasets.getTotal());
            response.setHasMore(datasets.getHasMore());
            return response;
        } catch (DifyApiException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public DatasetResponse getDataset(String datasetId) throws IOException{
        if (datasetId != null) {
            try {
                DifyDatasetsClient datasetsClient = DifyClientFactory.createDatasetsClient(appConfig.getBaseUrl(), appConfig.getDatasetApiKey());
                datasetsClient.deleteDataset(datasetId);
                System.out.println("删除测试知识库成功，ID: " + datasetId);
            } catch (Exception e) {
                System.err.println("删除测试知识库失败: " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * 删除知识库
     *
     * @param datasetId 知识库ID
     * @return 响应
     * @throws IOException IO异常
     */
    @Override
    public SimpleResponse deleteDataset(String datasetId) throws IOException {
        // 清理测试数据
        if (datasetId != null) {
            try {
                DifyDatasetsClient datasetsClient = DifyClientFactory.createDatasetsClient(appConfig.getBaseUrl(), appConfig.getDatasetApiKey());
                datasetsClient.deleteDataset(datasetId);
                System.out.println("删除测试知识库成功，ID: " + datasetId);
            } catch (Exception e) {
                System.err.println("删除测试知识库失败: " + e.getMessage());
            }
        }
        return new SimpleResponse();
    }

    /**
     * 通过文本创建文档
     *
     * @param datasetId 知识库ID
     * @param request   创建文档请求
     * @return 文档信息
     * @throws IOException IO异常
     */
    @Override
    public DocumentResponse createDocumentByText(String datasetId, CreateDocumentByTextRequest request) throws IOException {
        // 跳过,如果没有知识库
        if (datasetId == null) {
            System.out.println("跳过，因为没有知识库");
            return new DocumentResponse();
        }

        RetrievalModel retrievalModel = request.getRetrievalModel();
        io.github.imfangs.dify.client.model.datasets.RetrievalModel retrievalModelDiFy = new io.github.imfangs.dify.client.model.datasets.RetrievalModel();
        BeanUtils.copyProperties(retrievalModel, retrievalModelDiFy);

        // 创建文档请求
        io.github.imfangs.dify.client.model.datasets.CreateDocumentByTextRequest documentRequest = new io.github.imfangs.dify.client.model.datasets.CreateDocumentByTextRequest();
        BeanUtils.copyProperties(request, documentRequest);
        documentRequest.setRetrievalModel(retrievalModelDiFy);
        String mode = request.getProcessRule().getMode();
        if("custom".equals(mode)){
            ProcessRule.Rules rules = request.getProcessRule().getRules();
            io.github.imfangs.dify.client.model.datasets.ProcessRule.Rules rulesDiFy = new io.github.imfangs.dify.client.model.datasets.ProcessRule.Rules();
            BeanUtils.copyProperties(rules, rulesDiFy);
            List<io.github.imfangs.dify.client.model.datasets.ProcessRule.PreProcessingRule> collect = rules.getPreProcessingRules().stream().map(preProcessingRule -> {
                io.github.imfangs.dify.client.model.datasets.ProcessRule.PreProcessingRule preProcessingRuleDiFy = new io.github.imfangs.dify.client.model.datasets.ProcessRule.PreProcessingRule();
                BeanUtils.copyProperties(preProcessingRule, preProcessingRuleDiFy);
                return preProcessingRuleDiFy;
            }).collect(Collectors.toList());
            rulesDiFy.setPreProcessingRules(collect);
            io.github.imfangs.dify.client.model.datasets.ProcessRule.Segmentation segmentation = new io.github.imfangs.dify.client.model.datasets.ProcessRule.Segmentation();
            BeanUtils.copyProperties(rules.getSegmentation(), segmentation);
            rulesDiFy.setSegmentation(segmentation);
            documentRequest.setProcessRule(io.github.imfangs.dify.client.model.datasets.ProcessRule.builder()
                    .mode("custom")
                    .rules(rulesDiFy)
                    .build());
        }else{
            documentRequest.setProcessRule(io.github.imfangs.dify.client.model.datasets.ProcessRule.builder().mode("automatic").build());
        }

        DifyDatasetsClient datasetsClient = DifyClientFactory.createDatasetsClient(appConfig.getBaseUrl(), appConfig.getDatasetApiKey());
        try {
            // 发送请求
            io.github.imfangs.dify.client.model.datasets.DocumentResponse response = datasetsClient.createDocumentByText(datasetId, documentRequest);

            DocumentResponse documentResponse = new DocumentResponse();
            documentResponse.setBatch(response.getBatch());
            io.github.imfangs.dify.client.model.datasets.DocumentResponse.Document documentDiFy = response.getDocument();
            DocumentResponse.Document document = new DocumentResponse.Document();
            BeanUtils.copyProperties(documentDiFy, document);
            DocumentResponse.DataSourceInfo dataSourceInfo = new DocumentResponse.DataSourceInfo();
            io.github.imfangs.dify.client.model.datasets.DocumentResponse.DataSourceInfo dataSourceInfoDiFy = documentDiFy.getDataSourceInfo();
            dataSourceInfo.setUploadFileId(dataSourceInfoDiFy.getUploadFileId());
            return documentResponse;
        } catch (DifyApiException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 通过文件创建文档
     *
     * @param datasetId 知识库ID
     * @param request   创建文档请求
     * @param file      文件
     * @return 文档信息
     * @throws IOException IO异常
     */
    @Override
    public DocumentResponse createDocumentByFile(String datasetId, CreateDocumentByFileRequest request, File file) throws IOException {
        // 跳过,如果没有知识库
        if (datasetId == null) {
            System.out.println("跳过，因为没有知识库");
            return new DocumentResponse();
        }
        // 创建文档请求
        io.github.imfangs.dify.client.model.datasets.CreateDocumentByFileRequest documnetRequest = io.github.imfangs.dify.client.model.datasets.CreateDocumentByFileRequest.builder()
                .indexingTechnique(request.getIndexingTechnique())
                .docForm(request.getDocForm())
                // 1.1.3 invalid_param (400) - Must not be null! 【doc_language】
                .docLanguage(request.getDocLanguage())
                // 没有这里的设置，会500报错，服务器内部错误
                .processRule(io.github.imfangs.dify.client.model.datasets.ProcessRule.builder().mode("automatic").build())
                .build();

        DifyDatasetsClient datasetsClient = DifyClientFactory.createDatasetsClient(appConfig.getBaseUrl(), appConfig.getDatasetApiKey());
        try {
            // 发送请求
            io.github.imfangs.dify.client.model.datasets.DocumentResponse response = datasetsClient.createDocumentByFile(datasetId,documnetRequest,file);
            DocumentResponse documentResponse = new DocumentResponse();
            documentResponse.setBatch(response.getBatch());
            io.github.imfangs.dify.client.model.datasets.DocumentResponse.Document documentDiFy = response.getDocument();
            DocumentResponse.Document document = documentResponse.getDocument();
            BeanUtils.copyProperties(documentDiFy, document);
            DocumentResponse.DataSourceInfo dataSourceInfo = document.getDataSourceInfo();
            io.github.imfangs.dify.client.model.datasets.DocumentResponse.DataSourceInfo dataSourceInfoDiFy = documentDiFy.getDataSourceInfo();
            dataSourceInfo.setUploadFileId(dataSourceInfoDiFy.getUploadFileId());
            return documentResponse;
        } catch (DifyApiException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 通过文件创建文档
     *
     * @param datasetId   知识库ID
     * @param request     创建文档请求
     * @param inputStream 文件输入流
     * @param fileName    文件名
     * @return 文档信息
     * @throws IOException IO异常
     */
    @Override
    public DocumentResponse createDocumentByFile(String datasetId, CreateDocumentByFileRequest request, InputStream inputStream, String fileName) throws IOException {
        // 跳过,如果没有知识库
        if (datasetId == null) {
            System.out.println("跳过，因为没有知识库");
            return new DocumentResponse();
        }
        // 创建文档请求
        io.github.imfangs.dify.client.model.datasets.CreateDocumentByFileRequest documnetRequest = io.github.imfangs.dify.client.model.datasets.CreateDocumentByFileRequest.builder()
                .indexingTechnique(request.getIndexingTechnique())
                .docForm(request.getDocForm())
                // 1.1.3 invalid_param (400) - Must not be null! 【doc_language】
                .docLanguage(request.getDocLanguage())
                // 没有这里的设置，会500报错，服务器内部错误
                .processRule(io.github.imfangs.dify.client.model.datasets.ProcessRule.builder().mode("automatic").build())
                .build();

        DifyDatasetsClient datasetsClient = DifyClientFactory.createDatasetsClient(appConfig.getBaseUrl(), appConfig.getDatasetApiKey());
        try {
            // 发送请求
            io.github.imfangs.dify.client.model.datasets.DocumentResponse response = datasetsClient.createDocumentByFile(datasetId,documnetRequest,inputStream,fileName);
            DocumentResponse documentResponse = new DocumentResponse();
            documentResponse.setBatch(response.getBatch());
            io.github.imfangs.dify.client.model.datasets.DocumentResponse.Document documentDiFy = response.getDocument();
            DocumentResponse.Document document = documentResponse.getDocument();
            BeanUtils.copyProperties(documentDiFy, document);
            DocumentResponse.DataSourceInfo dataSourceInfo = document.getDataSourceInfo();
            io.github.imfangs.dify.client.model.datasets.DocumentResponse.DataSourceInfo dataSourceInfoDiFy = documentDiFy.getDataSourceInfo();
            dataSourceInfo.setUploadFileId(dataSourceInfoDiFy.getUploadFileId());
            return documentResponse;
        } catch (DifyApiException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 通过文本更新文档
     *
     * @param datasetId  知识库ID
     * @param documentId 文档ID
     * @param request    更新文档请求
     * @return 文档信息
     * @throws IOException IO异常
     */
    @Override
    public DocumentResponse updateDocumentByText(String datasetId, String documentId, UpdateDocumentByTextRequest request) throws IOException {
        io.github.imfangs.dify.client.model.datasets.UpdateDocumentByTextRequest documnetRequest = io.github.imfangs.dify.client.model.datasets.UpdateDocumentByTextRequest.builder()
                .text(request.getText())
                .name(request.getName())
                .docMetadata(request.getDocMetadata())
                .docType(request.getDocType())
                .processRule(io.github.imfangs.dify.client.model.datasets.ProcessRule.builder().mode("automatic").build())
                .build();
        DifyDatasetsClient datasetsClient = DifyClientFactory.createDatasetsClient(appConfig.getBaseUrl(), appConfig.getDatasetApiKey());
        try {
            io.github.imfangs.dify.client.model.datasets.DocumentResponse documentResponseDiFy = datasetsClient.updateDocumentByText(datasetId, documentId, documnetRequest);
            DocumentResponse documentResponse = new DocumentResponse();
            documentResponse.setBatch(documentResponseDiFy.getBatch());
            io.github.imfangs.dify.client.model.datasets.DocumentResponse.Document documentDiFy = documentResponseDiFy.getDocument();
            DocumentResponse.Document document = documentResponse.getDocument();
            BeanUtils.copyProperties(documentDiFy, document);
            DocumentResponse.DataSourceInfo dataSourceInfo = document.getDataSourceInfo();
            io.github.imfangs.dify.client.model.datasets.DocumentResponse.DataSourceInfo dataSourceInfoDiFy = documentDiFy.getDataSourceInfo();
            dataSourceInfo.setUploadFileId(dataSourceInfoDiFy.getUploadFileId());
            return documentResponse;
        } catch (DifyApiException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 通过文件更新文档
     *
     * @param datasetId  知识库ID
     * @param documentId 文档ID
     * @param request    更新文档请求
     * @param file       文件
     * @return 文档信息
     * @throws IOException IO异常
     */
    @Override
    public DocumentResponse updateDocumentByFile(String datasetId, String documentId, UpdateDocumentByFileRequest request, File file) throws IOException {
        io.github.imfangs.dify.client.model.datasets.UpdateDocumentByFileRequest documnetRequest = io.github.imfangs.dify.client.model.datasets.UpdateDocumentByFileRequest.builder()
                .name(request.getName())
                .docMetadata(request.getDocMetadata())
                .docType(request.getDocType())
                .processRule(io.github.imfangs.dify.client.model.datasets.ProcessRule.builder().mode("automatic").build())
                .build();
        DifyDatasetsClient datasetsClient = DifyClientFactory.createDatasetsClient(appConfig.getBaseUrl(), appConfig.getDatasetApiKey());
        try {
            io.github.imfangs.dify.client.model.datasets.DocumentResponse documentResponseDiFy = datasetsClient.updateDocumentByFile(datasetId, documentId, documnetRequest,file);
            DocumentResponse documentResponse = new DocumentResponse();
            documentResponse.setBatch(documentResponseDiFy.getBatch());
            io.github.imfangs.dify.client.model.datasets.DocumentResponse.Document documentDiFy = documentResponseDiFy.getDocument();
            DocumentResponse.Document document = documentResponse.getDocument();
            BeanUtils.copyProperties(documentDiFy, document);
            DocumentResponse.DataSourceInfo dataSourceInfo = document.getDataSourceInfo();
            io.github.imfangs.dify.client.model.datasets.DocumentResponse.DataSourceInfo dataSourceInfoDiFy = documentDiFy.getDataSourceInfo();
            dataSourceInfo.setUploadFileId(dataSourceInfoDiFy.getUploadFileId());
            return documentResponse;
        } catch (DifyApiException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 获取文档嵌入状态
     *
     * @param datasetId 知识库ID
     * @param batch     批次号
     * @return 文档嵌入状态
     * @throws IOException IO异常
     */
    @Override
    public IndexingStatusResponse getIndexingStatus(String datasetId, String batch) throws IOException {
        DifyDatasetsClient datasetsClient = DifyClientFactory.createDatasetsClient(appConfig.getBaseUrl(), appConfig.getDatasetApiKey());
        try {
            io.github.imfangs.dify.client.model.datasets.IndexingStatusResponse indexingStatus = datasetsClient.getIndexingStatus(datasetId, batch);
            List<io.github.imfangs.dify.client.model.datasets.IndexingStatusResponse.IndexingStatus> data = indexingStatus.getData();
            List<IndexingStatusResponse.IndexingStatus> collect = data.stream().map(event -> {
                IndexingStatusResponse.IndexingStatus indexingStatus1 = new IndexingStatusResponse.IndexingStatus();
                BeanUtils.copyProperties(event, indexingStatus1);
                return indexingStatus1;
            }).collect(Collectors.toList());
            IndexingStatusResponse indexingStatusResponse = new IndexingStatusResponse();
            indexingStatusResponse.setData(collect);
            return null;
        } catch (DifyApiException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 删除文档
     *
     * @param datasetId  知识库ID
     * @param documentId 文档ID
     * @return 响应
     * @throws IOException IO异常
     */
    @Override
    public void deleteDocument(String datasetId, String documentId) throws IOException {
        DifyDatasetsClient datasetsClient = DifyClientFactory.createDatasetsClient(appConfig.getBaseUrl(), appConfig.getDatasetApiKey());
        try {
            datasetsClient.deleteDocument(datasetId, documentId);
        } catch (DifyApiException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 获取知识库文档列表
     *
     * @param datasetId 知识库ID
     * @param keyword   关键词
     * @param page      页码
     * @param limit     每页数量
     * @return 文档列表
     * @throws IOException IO异常
     */
    @Override
    public DocumentListResponse getDocuments(String datasetId, String keyword, Integer page, Integer limit) throws IOException {
        DifyDatasetsClient datasetsClient = DifyClientFactory.createDatasetsClient(appConfig.getBaseUrl(), appConfig.getDatasetApiKey());
        try {
            io.github.imfangs.dify.client.model.datasets.DocumentListResponse documentsDiFy = datasetsClient.getDocuments(datasetId, keyword, page, limit);
            DocumentListResponse documentListResponse = new DocumentListResponse();
            documentListResponse.setPage(documentsDiFy.getPage());
            documentListResponse.setTotal(documentsDiFy.getTotal());
            documentListResponse.setLimit(documentsDiFy.getLimit());
            documentListResponse.setHasMore(documentsDiFy.getHasMore());
            List<io.github.imfangs.dify.client.model.datasets.DocumentListResponse.DocumentInfo> data = documentsDiFy.getData();
            List<DocumentListResponse.DocumentInfo> collect = data.stream().map(event -> {
                DocumentListResponse.DocumentInfo documentInfo = new DocumentListResponse.DocumentInfo();
                BeanUtils.copyProperties(event, documentInfo);
                return documentInfo;
            }).collect(Collectors.toList());
            documentListResponse.setData(collect);
            return documentListResponse;
        } catch (DifyApiException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 新增文档分段
     *
     * @param datasetId  知识库ID
     * @param documentId 文档ID
     * @param request    新增分段请求
     * @return 分段信息
     * @throws IOException IO异常
     */
    @Override
    public SegmentListResponse createSegments(String datasetId, String documentId, CreateSegmentsRequest request) throws IOException {
        List<CreateSegmentsRequest.SegmentInfo> segments = request.getSegments();
        List<io.github.imfangs.dify.client.model.datasets.CreateSegmentsRequest.SegmentInfo> collect = segments.stream().map(event -> {
            io.github.imfangs.dify.client.model.datasets.CreateSegmentsRequest.SegmentInfo info = new io.github.imfangs.dify.client.model.datasets.CreateSegmentsRequest.SegmentInfo();
            info.setAnswer(event.getAnswer());
            info.setContent(event.getContent());
            info.setKeywords(event.getKeywords());
            return info;
        }).collect(Collectors.toList());
        io.github.imfangs.dify.client.model.datasets.CreateSegmentsRequest requestDiFy = io.github.imfangs.dify.client.model.datasets.CreateSegmentsRequest.builder()
                        .segments(collect).build();

        DifyDatasetsClient datasetsClient = DifyClientFactory.createDatasetsClient(appConfig.getBaseUrl(), appConfig.getDatasetApiKey());
        try {
            io.github.imfangs.dify.client.model.datasets.SegmentListResponse segmentsDiFy = datasetsClient.createSegments(datasetId, documentId, requestDiFy);
            List<SegmentListResponse.SegmentInfo> collects = segmentsDiFy.getData().stream().map(event -> {
                SegmentListResponse.SegmentInfo info = new SegmentListResponse.SegmentInfo();
                BeanUtils.copyProperties(event, info);
                return info;
            }).collect(Collectors.toList());
            SegmentListResponse segmentListResponse = new SegmentListResponse();
            segmentListResponse.setDocForm(segmentsDiFy.getDocForm());
            segmentListResponse.setData(collects);
            return segmentListResponse;
        } catch (DifyApiException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 查询文档分段
     *
     * @param datasetId  知识库ID
     * @param documentId 文档ID
     * @param keyword    关键词
     * @param status     状态
     * @return 分段列表
     * @throws IOException IO异常
     */
    @Override
    public SegmentListResponse getSegments(String datasetId, String documentId, String keyword, String status) throws IOException {
        DifyDatasetsClient datasetsClient = DifyClientFactory.createDatasetsClient(appConfig.getBaseUrl(), appConfig.getDatasetApiKey());
        try {
            io.github.imfangs.dify.client.model.datasets.SegmentListResponse segmentsDiFy = datasetsClient.getSegments(datasetId, documentId, keyword, status);
            List<SegmentListResponse.SegmentInfo> collects = segmentsDiFy.getData().stream().map(event -> {
                SegmentListResponse.SegmentInfo info = new SegmentListResponse.SegmentInfo();
                BeanUtils.copyProperties(event, info);
                return info;
            }).collect(Collectors.toList());
            SegmentListResponse segmentListResponse = new SegmentListResponse();
            segmentListResponse.setDocForm(segmentsDiFy.getDocForm());
            segmentListResponse.setData(collects);
            return segmentListResponse;
        } catch (DifyApiException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 删除文档分段
     *
     * @param datasetId  知识库ID
     * @param documentId 文档ID
     * @param segmentId  分段ID
     * @return 响应
     * @throws IOException IO异常
     */
    @Override
    public void deleteSegment(String datasetId, String documentId, String segmentId) throws IOException {
        DifyDatasetsClient datasetsClient = DifyClientFactory.createDatasetsClient(appConfig.getBaseUrl(), appConfig.getDatasetApiKey());
        try {
            datasetsClient.deleteSegment(datasetId, documentId, segmentId);
        } catch (DifyApiException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 更新文档分段
     *
     * @param datasetId  知识库ID
     * @param documentId 文档ID
     * @param segmentId  分段ID
     * @param request    更新分段请求
     * @return 分段信息
     * @throws IOException IO异常
     */
    @Override
    public SegmentResponse updateSegment(String datasetId, String documentId, String segmentId, UpdateSegmentRequest request) throws IOException {
        UpdateSegmentRequest.SegmentInfo segment = request.getSegment();
        io.github.imfangs.dify.client.model.datasets.UpdateSegmentRequest.SegmentInfo segmentinfo = new io.github.imfangs.dify.client.model.datasets.UpdateSegmentRequest.SegmentInfo();
        segmentinfo.setAnswer(segment.getAnswer());
        segmentinfo.setContent(segment.getContent());
        segmentinfo.setEnabled(segment.getEnabled());
        segmentinfo.setKeywords(segment.getKeywords());
        segmentinfo.setRegenerateChildChunks(segment.getRegenerateChildChunks());

        io.github.imfangs.dify.client.model.datasets.UpdateSegmentRequest requestDiFy = io.github.imfangs.dify.client.model.datasets.UpdateSegmentRequest.builder()
                        .segment(segmentinfo).build();
        DifyDatasetsClient datasetsClient = DifyClientFactory.createDatasetsClient(appConfig.getBaseUrl(), appConfig.getDatasetApiKey());

        try {
            io.github.imfangs.dify.client.model.datasets.SegmentResponse segmentResponseDiFy = datasetsClient.updateSegment(datasetId, documentId, segmentId, requestDiFy);
            io.github.imfangs.dify.client.model.datasets.SegmentResponse.SegmentInfo segmentResponseDiFyData = segmentResponseDiFy.getData();
            SegmentResponse.SegmentInfo segmentInfoData = new SegmentResponse.SegmentInfo();
            BeanUtils.copyProperties(segmentResponseDiFyData,segmentInfoData);
            SegmentResponse segmentResponse = new SegmentResponse();
            segmentResponse.setDocForm(segmentResponseDiFy.getDocForm());
            segmentResponse.setData(segmentInfoData);
            return segmentResponse;
        } catch (DifyApiException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 获取上传文件
     *
     * @param datasetId  知识库ID
     * @param documentId 文档ID
     * @return 文件信息
     * @throws IOException IO异常
     */
    @Override
    public UploadFileResponse getUploadFile(String datasetId, String documentId) throws IOException {
        DifyDatasetsClient datasetsClient = DifyClientFactory.createDatasetsClient(appConfig.getBaseUrl(), appConfig.getDatasetApiKey());
        try {
            io.github.imfangs.dify.client.model.datasets.UploadFileResponse uploadFileDiFy = datasetsClient.getUploadFile(datasetId, documentId);
            UploadFileResponse uploadFileResponse = new UploadFileResponse();
            BeanUtils.copyProperties(uploadFileDiFy, uploadFileResponse);
            return uploadFileResponse;
        } catch (DifyApiException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 检索知识库
     *
     * @param datasetId 知识库ID
     * @param request   检索请求
     * @return 检索结果
     * @throws IOException IO异常
     */
    @Override
    public RetrieveResponse retrieveDataset(String datasetId, RetrieveRequest request) throws IOException {
        RetrievalModel retrievalModel = request.getRetrievalModel();
        RetrievalModel.RerankingModel rerankingModel = retrievalModel.getRerankingModel();

        io.github.imfangs.dify.client.model.datasets.RetrievalModel.RerankingModel rerankingModelDiFY = new io.github.imfangs.dify.client.model.datasets.RetrievalModel.RerankingModel();
        rerankingModelDiFY.setRerankingProviderName(rerankingModel.getRerankingProviderName());
        rerankingModelDiFY.setRerankingModelName(rerankingModel.getRerankingModelName());

        io.github.imfangs.dify.client.model.datasets.RetrievalModel retrievalModelDiFy= new io.github.imfangs.dify.client.model.datasets.RetrievalModel();
        retrievalModelDiFy.setScoreThreshold(retrievalModel.getScoreThreshold());
        retrievalModelDiFy.setSearchMethod(retrievalModel.getSearchMethod());
        retrievalModelDiFy.setTopK(retrievalModel.getTopK());
        retrievalModelDiFy.setRerankingEnable(retrievalModel.getRerankingEnable());
        retrievalModelDiFy.setRerankingModel(rerankingModelDiFY);

        io.github.imfangs.dify.client.model.datasets.RetrieveRequest requestDiFy = io.github.imfangs.dify.client.model.datasets.RetrieveRequest.builder()
                        .query(request.getQuery())
                        .retrievalModel(retrievalModelDiFy)
                        .externalRetrievalModel(request.getExternalRetrievalModel())
                        .build();
        DifyDatasetsClient datasetsClient = DifyClientFactory.createDatasetsClient(appConfig.getBaseUrl(), appConfig.getDatasetApiKey());
        try {
            io.github.imfangs.dify.client.model.datasets.RetrieveResponse retrieveResponseDiFy = datasetsClient.retrieveDataset(datasetId, requestDiFy);

            io.github.imfangs.dify.client.model.datasets.RetrieveResponse.QueryInfo queryDiFy = retrieveResponseDiFy.getQuery();
            RetrieveResponse.QueryInfo queryInfo = new RetrieveResponse.QueryInfo();
            queryInfo.setContent(queryDiFy.getContent());

            List<io.github.imfangs.dify.client.model.datasets.RetrieveResponse.Record> recordsDiFy = retrieveResponseDiFy.getRecords();
            List<RetrieveResponse.Record> collect = recordsDiFy.stream().map(event -> {
                io.github.imfangs.dify.client.model.datasets.RetrieveResponse.SegmentInfo segmentDiFy = event.getSegment();

                RetrieveResponse.SegmentInfo info = new RetrieveResponse.SegmentInfo();
                BeanUtils.copyProperties(segmentDiFy, info);
                RetrieveResponse.Record record = new RetrieveResponse.Record();
                record.setScore(event.getScore());
                record.setTsnePosition(event.getTsnePosition());
                record.setSegment(info);

                return record;
            }).collect(Collectors.toList());


            RetrieveResponse retrieveResponse = new RetrieveResponse();
            retrieveResponse.setQuery(queryInfo);
            retrieveResponse.setRecords(collect);
            return retrieveResponse;
        } catch (DifyApiException e) {
            throw new RuntimeException(e);
        }

    }
    @Override
    public AppParametersResponse getAppParameters(ChatMessage msg) throws IOException {
        DifyChatflowClient chatClient = DifyClientFactory.createChatWorkflowClient(getAppConfig().getBaseUrl(), msg.getApiKey());
        try {
            io.github.imfangs.dify.client.model.chat.AppParametersResponse appParametersResponse = chatClient.getAppParameters();
            AppParametersResponse response = new AppParametersResponse();

            io.github.imfangs.dify.client.model.chat.AppParametersResponse.FileUpload fileUpload = appParametersResponse.getFileUpload();
            io.github.imfangs.dify.client.model.chat.AppParametersResponse.FileUpload.Image image = fileUpload.getImage();
            AppParametersResponse.FileUpload.Image image1 = new AppParametersResponse.FileUpload.Image();
            image1.setEnabled(image.getEnabled());
            image1.setNumberLimits(image.getNumberLimits());
            image1.setEnabled(image.getEnabled());
            AppParametersResponse.FileUpload upload = new AppParametersResponse.FileUpload();
            upload.setImage(image1);
            response.setFileUpload(upload);

            io.github.imfangs.dify.client.model.chat.AppParametersResponse.SystemParameters systemParameters = appParametersResponse.getSystemParameters();
            AppParametersResponse.SystemParameters parameters = new AppParametersResponse.SystemParameters();
            parameters.setAudioFileSizeLimit(systemParameters.getAudioFileSizeLimit());
            parameters.setFileSizeLimit(systemParameters.getFileSizeLimit());
            parameters.setImageFileSizeLimit(systemParameters.getImageFileSizeLimit());
            parameters.setVideoFileSizeLimit(systemParameters.getVideoFileSizeLimit());
            response.setSystemParameters(parameters);

            io.github.imfangs.dify.client.model.chat.AppParametersResponse.AnnotationReply reply= appParametersResponse.getAnnotationReply();
            AppParametersResponse.AnnotationReply annotationReply = new AppParametersResponse.AnnotationReply();
            annotationReply.setEnabled(reply.getEnabled());
            response.setAnnotationReply(annotationReply);

            response.setOpeningStatement(appParametersResponse.getOpeningStatement());

            io.github.imfangs.dify.client.model.chat.AppParametersResponse.SpeechToText toText = appParametersResponse.getSpeechToText();
            AppParametersResponse.SpeechToText speechToText = new AppParametersResponse.SpeechToText();
            speechToText.setEnabled(toText.getEnabled());
            response.setSpeechToText(speechToText);

            io.github.imfangs.dify.client.model.chat.AppParametersResponse.RetrieverResource mode = appParametersResponse.getRetrieverResource();
            AppParametersResponse.RetrieverResource retrieverResource = new AppParametersResponse.RetrieverResource();
            retrieverResource.setEnabled(mode.getEnabled());
            response.setRetrieverResource(retrieverResource);
            response.setSuggestedQuestions(appParametersResponse.getSuggestedQuestions());

            io.github.imfangs.dify.client.model.chat.AppParametersResponse.SuggestedQuestionsAfterAnswer afterAnswer = appParametersResponse.getSuggestedQuestionsAfterAnswer();
            AppParametersResponse.SuggestedQuestionsAfterAnswer suggestedQuestionsAfterAnswer = new AppParametersResponse.SuggestedQuestionsAfterAnswer();
            suggestedQuestionsAfterAnswer.setEnabled(afterAnswer.getEnabled());
            response.setSuggestedQuestionsAfterAnswer(suggestedQuestionsAfterAnswer);
            response.setUserInputForm(appParametersResponse.getUserInputForm());
            return response;
        } catch (DifyApiException e) {
            throw new RuntimeException(e);
        }

    }
    @Override
    public String feedbackMessage(String messageId, String rating, String user, String content,String apiKey) throws IOException {
        DifyChatClient chatClient = DifyClientFactory.createChatClient(getAppConfig().getBaseUrl(), apiKey);
        try {
            io.github.imfangs.dify.client.model.common.SimpleResponse feedbackResponse = chatClient.feedbackMessage(messageId, rating,user,content);
            return feedbackResponse.getResult();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (DifyApiException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public String stopChatMessage(String taskId, String user,String apiKey) throws IOException {
        DifyChatClient chatClient = DifyClientFactory.createChatClient(getAppConfig().getBaseUrl(), apiKey);
        try {
            io.github.imfangs.dify.client.model.common.SimpleResponse stopResponse = chatClient.stopChatMessage(taskId,user);
            return stopResponse.getResult();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (DifyApiException e) {
            throw new RuntimeException(e);
        }
    }
}
