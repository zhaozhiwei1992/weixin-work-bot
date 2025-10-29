package com.z.bot.service;

import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 *
 //                String replyMsg = "{\n" +
 //                        "  \"msgtype\": \"text\",\n" +
 //                        "  \"text\": {\n" +
 //                        "    \"content\": \"hello\\nI'm RobotA\\n\"\n" +
 //                        "  }\n" +
 //                        "}";
 * 这种官方给出的格式步行，可以换个方式直接结束
 */
@Service
public class ReplyMessageText implements ReplyMessage{
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
            textContent.put("content", "你好呀！我是智能机器人，有什么可以帮您的吗？");
        } else if (content.contains("时间")) {
            textContent.put("content", "当前时间是: " + new Date());
        } else {
            textContent.put("content", "抱歉，我还不能理解这个问题。请尝试问我其他问题！");
        }

        reply.put("msgtype", "text");
        reply.put("text", textContent);

        return reply.toString();
    }
}
