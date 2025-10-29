package com.z.bot.service;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 *
 //                智能机器人msgtype得用stream
 //                String replyMsg = "{\n" +
 //                        "    \"msgtype\": \"stream\",\n" +
 //                        "    \"stream\": {\n" +
 //                        "        \"id\": \"STREAMID\",\n" +
 //                        "        \"finish\": false,\n" +
 //                        "        \"content\": \"**广州**今日天气：29度，大部分多云，降雨概率：60%\",\n" +
 //                        "        \"msg_item\": [\n" +
 //                        "            {\n" +
 //                        "                \"msgtype\": \"image\",\n" +
 //                        "                \"image\": {\n" +
 //                        "                    \"base64\": \"BASE64\",\n" +
 //                        "                    \"md5\": \"MD5\"\n" +
 //                        "                }\n" +
 //                        "            }\n" +
 //                        "        ]\n" +
 //                        "    }\n" +
 //                        "}";
 */
@Slf4j
@Service
public class ReplyMessageStream implements ReplyMessage{
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

        // 设置消息类型和内容
        JSONObject textContent = new JSONObject();

        JSONObject textObj = originalMsg.getJSONObject("text");
        String content = textObj.getString("content");
        // 简单回复逻辑示例
        if (content.contains("你好")) {
            log.info("收到来自用户{}的消息：{}", userId, content);
        }

        reply.put("msgtype", "text");
        reply.put("text", textContent);

        // 使用示例
        List<JSONObject> msgItems = new ArrayList<>();
        msgItems.add(buildImageMessage("BASE64_DATA", "MD5_HASH"));

        // 根据流式返回一直构建输出，前端也会一直请求
        String replyMsg = buildStreamMessage(
                "你好呀！我是智能机器人，有什么可以帮您的吗？ 当前时间是" + new Date(),
                "STREAM123",
                true,
                msgItems
        );
        return replyMsg;

    }

    private String buildStreamMessage(String content, String streamId, boolean finish,
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
}
