package com.z.bot.adapter.callback;


import com.z.bot.adapter.event.*;

/**
 * 文本生成流式响应回调接口
 */
public interface CompletionStreamCallback extends BaseStreamCallback {
    /**
     * 消息事件
     *
     * @param event 事件数据
     */
    default void onMessage(MessageEvent event) {
    }

    /**
     * 消息结束事件
     *
     * @param event 事件数据
     */
    default void onMessageEnd(MessageEndEvent event) {
    }

    /**
     * TTS 消息事件
     *
     * @param event 事件数据
     */
    default void onTtsMessage(TtsMessageEvent event) {
    }

    /**
     * TTS 消息结束事件
     *
     * @param event 事件数据
     */
    default void onTtsMessageEnd(TtsMessageEndEvent event) {
    }

    /**
     * 消息替换事件
     *
     * @param event 事件数据
     */
    default void onMessageReplace(MessageReplaceEvent event) {
    }

}
