package com.z.bot.service;

import com.z.bot.adapter.model.chat.ChatMessage;
import com.z.bot.platform.AbstractAIService;
import com.z.bot.repository.StreamMapRepository;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * //                智能机器人msgtype得用stream
 * //                String replyMsg = "{\n" +
 * //                        "    \"msgtype\": \"stream\",\n" +
 * //                        "    \"stream\": {\n" +
 * //                        "        \"id\": \"STREAMID\",\n" +
 * //                        "        \"finish\": false,\n" +
 * //                        "        \"content\": \"**广州**今日天气：29度，大部分多云，降雨概率：60%\",\n" +
 * //                        "        \"msg_item\": [\n" +
 * //                        "            {\n" +
 * //                        "                \"msgtype\": \"image\",\n" +
 * //                        "                \"image\": {\n" +
 * //                        "                    \"base64\": \"BASE64\",\n" +
 * //                        "                    \"md5\": \"MD5\"\n" +
 * //                        "                }\n" +
 * //                        "            }\n" +
 * //                        "        ]\n" +
 * //                        "    }\n" +
 * //                        "}";
 */
@Slf4j
@Service
public class ReplyMessageStream implements ReplyMessage {

    @Autowired
    private StreamMapRepository streamMapRepository;

    @Autowired
    private AbstractAIService abstractAIService;

    /**
     * @Title: ReplyMessageStream
     * @Package com/z/bot/service/ReplyMessageStream.java
     * @Description: 1. 立即回复，保证企业微信不会一直重复发msgtype=text请求
     * 2. 异步继续接收流式数据， 保证后续msgtype=stream可以收到消息
     * 3. 因为第三方ai服务返回的会话id无法保证实时性能，所以最好用自己生成的id
     * @author zhaozhiwei
     * @date 2025/11/26 15:43
     * @version V1.0
     */
    @Override
    public String reply(JSONObject originalMsg) {
        String userId = originalMsg.getJSONObject("from").getString("userid");
        String chatType = originalMsg.getString("chattype");

        JSONObject reply = new JSONObject();

        // 根据聊天类型设置接收方
        if ("group".equals(chatType)) {
            String chatId = originalMsg.getString("chatid");
            reply.put("chatid", chatId);
        } else {
            reply.put("touser", userId);
        }

        JSONObject textObj = originalMsg.getJSONObject("text");
        String content = textObj.getString("content");
        log.info("收到来自用户{}的消息：{}", userId, content);

        // 根据流式返回一直构建输出，前端也会一直请求, 测试效果
//        List<JSONObject> msgItems = new ArrayList<>();
//        msgItems.add(buildImageMessage("BASE64_DATA", "MD5_HASH"));
//        String replyMsg = buildStreamMessage(
//                "你好呀！我是智能机器人，有什么可以帮您的吗？ 当前时间是" + new Date(),
//                "STREAM123",
//                true,
//                msgItems
//        );
//
//        return replyMsg;

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUser("admin");
        chatMessage.setQuery(content);
        chatMessage.setConversationId("");
        chatMessage.setSendTime(System.currentTimeMillis());
        Flux<Map<String, Object>> eventStream = null;
        try {
            eventStream = abstractAIService.sendChatFlowMessageStream(chatMessage);
        } catch (IOException e) {
            if (e.getMessage().contains("MUST_DISPATCH")) {
                log.error("异步请求已结束，忽略后续错误推送（通常由客户端提前断开连接引起）");
            } else {
                throw new RuntimeException(e);
            }
        }

        // 使用streamMapRepository不断收集流式响应数据
        AtomicReference<String> conversationId = new AtomicReference<>(this.generateLocalConversationId(userId));
        Flux<Map<String, Object>> rMap = eventStream
                .subscribeOn(Schedulers.boundedElastic())
                .map(event -> {
                    Map<String, Object> r = new HashMap<>();
//                    自定义会话id保证企业微信下的响应效果
//                    conversationId.set(event.get("conversation_id") + "");
                    if ("message".equals(event.get("event"))) {
                        if (event.get("answer") != null) {
                            // 保存流式结果
                            streamMapRepository.add(conversationId.get(), event.get("answer") + "");
                        }
                    } else if ("message_end".equals(event.get("event"))) {
                        // 会话结束，打标记保存日志
                        streamMapRepository.add(conversationId.get(), "messageend");
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("自定义会话id: {}, event返回: {}", conversationId.get(), event);
                        log.debug("返回前端结果：{}", r);
                    }
                    return r;
                }).doOnError(error -> log.error("在处理AI事件流时发生异常", error));

        rMap.subscribe();

        // 立即返回一个请求保证及时相应
        return buildStreamMessage(
                "",
                conversationId.get(),
                false,
                null
        );
    }

    public String buildStreamMessage(String content, String streamId, boolean finish,
                                     List<JSONObject> msgItems) {
        JSONObject message = new JSONObject();
        message.put("msgtype", "stream");

        JSONObject stream = new JSONObject();
        stream.put("id", streamId);
        stream.put("finish", finish);
        stream.put("content", content);

        if (msgItems != null && !msgItems.isEmpty()) {
            stream.put("msg_item", msgItems);
        }

        message.put("stream", stream);
        return message.toString();
    }

    // 构建消息项
    private JSONObject buildImageMessage(String base64, String md5) {
        JSONObject imageMsg = new JSONObject();
        imageMsg.put("msgtype", "image");

        JSONObject image = new JSONObject();
        image.put("base64", base64);
        image.put("md5", md5);

        imageMsg.put("image", image);
        return imageMsg;
    }

    private String generateLocalConversationId(String userId) {
        String userPrefix = userId.length() > 4 ? userId.substring(0, 4) : userId;
        return "conv_" + userPrefix + "_" + System.currentTimeMillis() + "_" + new Random().nextInt(1000);
    }
}
