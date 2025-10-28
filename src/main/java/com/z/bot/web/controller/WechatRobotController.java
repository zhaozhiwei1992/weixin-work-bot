package com.z.bot.web.controller;

import com.alibaba.fastjson.JSON;
import com.qq.weixin.mp.aes.WXBizJsonMsgCrypt;
import com.z.bot.web.vo.RobotData;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

/**
 * @Title: WechatRobotController
 * @Package com/z/bot/web/controller/WechatRobotController.java
 * @Description:
 * corpid参数：必须从URL参数中获取，不能使用固定值
 * 返回值：必须返回解密后的明文echostr，不能包含其他内容
 * 异常处理：必须捕获所有异常并记录日志，但对外返回简洁信息
 * @author zhaozhiwei
 * @date 2025/10/27 22:58
 * @version V1.0
 */
@Slf4j
@RestController
@RequestMapping("/wx/work/robot")
public class WechatRobotController {
    
    // 配置参数 - 需替换为实际值
    static final String sToken = "OvBIwwsQRvNsk7LUL0CJS2n7";
    // 您的企业ID，此处空着，官网有说明，内部用空着即可
    static final String sCorpID = "";
    static final String sEncodingAESKey = "a8QRrWEiEhcW1YyNWAg9M8EyqjEYiFEweUjl9CQuRHJ";

    @GetMapping("/")
    public String echo(){
        return "success";
    }
    
    /**
     * URL验证接口 (GET请求)
     * 企业微信会调用此接口验证URL有效性
     */
    @GetMapping("/push/wechat")
    public String wechatGet(
            @RequestParam("msg_signature") String msgSignature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("corpid") String corpid, // 从URL参数获取corpid
            @RequestParam("nonce") String nonce,
            @RequestParam("echostr") String echostr) {
        
        log.info("验证URL请求的corpid：{}, 签名: {}, 时间戳: {}, 随机数: {}", 
                corpid, msgSignature, timestamp, nonce);
        
        try {
            // 使用URL中的corpid初始化加解密工具
            WXBizJsonMsgCrypt wxcpt = new WXBizJsonMsgCrypt(sToken, sEncodingAESKey, corpid);
            
            // 验证URL并获取明文echostr
            String sEchoStr = wxcpt.VerifyURL(msgSignature, timestamp, nonce, echostr);
            log.info("验证URL成功, 返回明文: {}", sEchoStr);
            
            return sEchoStr;
        } catch (Exception e) {
            log.error("验证URL失败", e);
            return "error";
        }
    }


    /**
     * 接收消息接口 (POST请求)
     * 企业微信会将用户发送给机器人的消息推送到此接口
     * {
     *     "msgid": "CAIQ16HMjQYY/NGagIOAgAMgq4KM0AI=",
     *     "aibotid": "AIBOTID",
     *     "chatid": "CHATID",
     *     "chattype": "group",
     *     "from": {
     *         "userid": "USERID"
     *     },
     *     "msgtype": "text",
     *     "text": {
     *         "content": "@RobotA hello robot"
     *     }
     * }
     */
    @PostMapping("/push/wechat")
    public String wechatPost(
            @RequestParam("msg_signature") String msgSignature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestBody RobotData postData) {

        log.info("接收消息请求的签名: {}, 时间戳: {}, 随机数: {}", msgSignature, timestamp, nonce);
        log.info("接收到的加密消息: {}", JSON.toJSONString(postData));

        try {
            // 使用固定的企业ID初始化加解密工具
            WXBizJsonMsgCrypt wxcpt = new WXBizJsonMsgCrypt(sToken, sEncodingAESKey, sCorpID);

            // 解密消息
            String sMsg = wxcpt.DecryptMsg(msgSignature, timestamp, nonce, JSON.toJSONString(postData));
            log.info("消息解密后内容: {}", sMsg);

            // 解析消息内容
            JSONObject json = new JSONObject(sMsg);
            String msgType = json.getString("msgtype");
            String chatType = json.getString("chattype"); // 聊天类型: single/group

            // 处理文本消息
            if ("text".equals(msgType)) {
                JSONObject textObj = json.getJSONObject("text");
                String content = textObj.getString("content");
                String userId = json.getJSONObject("from").getString("userid");

                log.info("用户[{}]在[{}]聊天中发送消息: {}", userId, chatType, content);

                // 构建回复消息
                String replyMsg = buildReplyMessage(json, content);
                log.info("加密前的回复消息: {}", replyMsg);
                // 加密回复消息
                String encryptMsg = wxcpt.EncryptMsg(replyMsg, timestamp, nonce);
                log.info("加密后的回复消息: {}", encryptMsg);
                return encryptMsg;
            }

        } catch (Exception e) {
            log.error("处理消息失败", e);
        }

        return "success";
    }

    /**
     * 构建回复消息
     */
    private String buildReplyMessage(JSONObject originalMsg, String content) {
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
