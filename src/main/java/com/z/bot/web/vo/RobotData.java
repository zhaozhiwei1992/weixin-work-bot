package com.z.bot.web.vo;

import lombok.Data;

@Data
public class RobotData {
    private String tousername; // 接收方企业微信ID
    private String encrypt;    // 加密的消息内容
    private String agentid;    // 应用ID
}