package com.z.bot.platform.bailian;

import com.alibaba.dashscope.app.Application;
import com.alibaba.dashscope.app.ApplicationParam;
import com.alibaba.dashscope.app.ApplicationResult;
import com.alibaba.dashscope.app.FlowStreamMode;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.z.bot.adapter.callback.CompletionStreamCallback;
import com.z.bot.adapter.model.chat.*;
import com.z.bot.adapter.model.completion.CompletionRequest;
import com.z.bot.adapter.model.completion.CompletionResponse;
import com.z.bot.adapter.model.datasets.CreateDatasetRequest;
import com.z.bot.adapter.model.datasets.CreateDocumentByFileRequest;
import com.z.bot.adapter.model.datasets.DatasetResponse;
import com.z.bot.adapter.model.datasets.DocumentResponse;
import com.z.bot.adapter.model.file.FileUploadRequest;
import com.z.bot.adapter.model.file.FileUploadResponse;
import com.z.bot.adapter.model.workflow.WorkflowRunRequest;
import com.z.bot.adapter.model.workflow.WorkflowRunResponse;
import com.z.bot.platform.AbstractAIService;
import com.z.bot.platform.bailian.config.BaiLianProperties;
import io.github.imfangs.dify.client.exception.DifyApiException;
import io.reactivex.Flowable;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;

@Component
@ConditionalOnProperty(name = "llm.type", havingValue = "bailian")
@Slf4j
@Data
public class BaiLianServiceAdapter extends AbstractAIService {

    @Autowired
    private BaiLianProperties baiLianProperties;

    @Autowired
    private RestTemplate restTemplate;

    private Map<String, Object> convertToStandardFormat(ApplicationResult result) {

        Map<String, Object> response = new LinkedHashMap<>();

        if (result.getOutput().getFinishReason().equals("stop")){
            log.info("task is finished ,text = {}\n", result.getOutput().getText());
            response.put("event", "message_end");
            response.put("answer", result.getOutput().getText());
        }else {
            log.info("{} \n", result.getOutput().getWorkflowMessage().getMessage().getContent());
            response.put("event", "message");
            response.put("answer", result.getOutput().getWorkflowMessage().getMessage().getContent());
        }

        response.put("timestamp", System.currentTimeMillis());
        response.put("conversation_id", result.getOutput().getSessionId());

        // 元数据
        Map<String, Object> meta = new HashMap<>();
        meta.put("token_usage", result.getUsage());
        response.put("metadata", meta);

        return response;
    }

    private Flux<Map<String, Object>> errorToMap(Throwable e) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("type", "system_error");
        map.put("message", e.getMessage());
        map.put("is_end", true);
        return Flux.just(map);
    }

    @Override
    public AppParametersResponse getAppParameters(ChatMessage msg) throws IOException {
        AppParametersResponse response = new AppParametersResponse();
        return response;
    }

    @Override
    public Flux<Map<String, Object>> sendChatFlowMessageStream(ChatMessage message) throws IOException {
        return sendChatMessageStream(message);
    }

    @Override
    public Flux<Map<String, Object>> sendChatMessageStream(ChatMessage message) {
        try {
            // 构建SDK参数
            ApplicationParam param = ApplicationParam.builder()
                    .apiKey(baiLianProperties.getApiKey())
                    .appId(baiLianProperties.getAppId())
                    .prompt(message.getQuery())
                    .sessionId(message.getConversationId())
                    .flowStreamMode(FlowStreamMode.MESSAGE_FORMAT)
                    .build();

            // 获取SDK流并直接转换
            Flowable<ApplicationResult> sdkStream = new Application(baiLianProperties.getBaseUrl()).streamCall(param);

            // 直接转换，不包装Flux.create
            return Flux.from(sdkStream)
                    .map(this::convertToStandardFormat)
                    .doOnNext(data -> log.debug("发送数据: {}", data))
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(Duration.ofSeconds(30))
                    .onErrorResume(this::errorToMap);

        } catch (NoApiKeyException | InputRequiredException e) {
            return Flux.error(new RuntimeException("认证参数异常", e));
        } catch (Exception e) {
            return Flux.error(new RuntimeException("系统错误", e));
        }
    }

    @Override
    public MessageListResponse getMessages(String conversationId, String user, String firstId, Integer limit, String apiKey) {
        // curl -X GET
        // 'http://10.10.115.9/v1/conversations?user=abc-123&last_id=&limit=20'\
        // --header 'Authorization: Bearer {api_key}'
        String url = getBaiLianProperties().getBaseUrl();
//                + CONVERSATIONS_PATH;
        // 这里参数需要按照认证提供用户给出
        url += String.format("?user=%s&last_id=%s&limit=%s", user, firstId, limit);
        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Authorization", "Bearer " + apiKey);

        final HttpEntity<List<Map<String, Object>>> httpEntity = new HttpEntity<>(headers);
        ResponseEntity<MessageListResponse> exchange = restTemplate.getForEntity(url, MessageListResponse.class, httpEntity);
        return exchange.getBody();
    }

    @Override
    public AudioToTextResponse audioToText(File file, String user, String apiKey) throws IOException, DifyApiException {
        if (Objects.isNull(file)) {
            throw new RuntimeException("音频文件不存在，请检查");
        }
        return null;
    }

    @Override
    public ChatMessageResponse sendChatMessage(ChatMessage msg){
        return null;
    }

    @Override
    public WorkflowRunResponse runWorkflow(WorkflowRunRequest r) throws IOException {
        return null;
    }

    @Override
    public Flux<Map<String, Object>> runWorkflowStream(WorkflowRunRequest r) throws IOException {
        return null;
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
       return null;
    }

    @Override
    public FileUploadResponse uploadFile(FileUploadRequest request, File file) throws IOException {
        return null;
    }

    @Override
    public FileUploadResponse uploadFile(FileUploadRequest request, InputStream inputStream, String fileName) throws IOException {
        return null;
    }

    @Override
    public CompletionResponse sendCompletionMessage(CompletionRequest request) throws IOException {
        return null;
    }

    @Override
    public void sendCompletionMessageStream(CompletionRequest request, CompletionStreamCallback callback) throws IOException {

    }

    @Override
    public DocumentResponse createDocumentByFile(String datasetId, CreateDocumentByFileRequest request, File file) throws IOException {
        return null;
    }

    @Override
    public DocumentResponse createDocumentByFile(String datasetId, CreateDocumentByFileRequest request, InputStream inputStream, String fileName) throws IOException {
        return null;
    }
}
