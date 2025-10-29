package com.z.bot.adapter.model.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.z.bot.adapter.model.common.Metadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话消息响应（阻塞模式）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessageResponse {
    /**
     * 消息唯一 ID
     */
    private String messageId;

    /**
     * 会话 ID
     */
    private String conversationId;

    /**
     * App 模式，固定为 chat
     */
    private String mode;

    /**
     * 完整回复内容
     */
    private String answer;

    /**
     * 元数据
     */
    private Metadata metadata;

    /**
     * 消息创建时间戳
     */
    private Long createdAt;

    /**
     * 模型用量信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        /**
         * 提示 tokens
         */
        private Integer promptTokens;

        /**
         * 完成 tokens
         */
        private Integer completionTokens;

        /**
         * 总 tokens
         */
        private Integer totalTokens;

        /**
         * 提示单价
         */
        private String promptUnitPrice;

        /**
         * 提示价格单位
         */
        private String promptPriceUnit;

        /**
         * 提示价格
         */
        private String promptPrice;

        /**
         * 完成单价
         */
        private String completionUnitPrice;

        /**
         * 完成价格单位
         */
        private String completionPriceUnit;

        /**
         * 完成价格
         */
        private String completionPrice;

        /**
         * 总价格
         */
        private String totalPrice;

        /**
         * 货币
         */
        private String currency;

        /**
         * 延迟
         */
        private Double latency;
    }
}
