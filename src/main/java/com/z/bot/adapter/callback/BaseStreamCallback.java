package com.z.bot.adapter.callback;


import com.z.bot.adapter.event.ErrorEvent;
import com.z.bot.adapter.event.PingEvent;

/**
 * 对话流式回调接口
 */
public interface BaseStreamCallback {

    /**
     * 错误事件
     *
     * @param event 事件
     */
    default void onError(ErrorEvent event) {
    }

    /**
     * 心跳
     *
     * @param event 事件
     */
    default void onPing(PingEvent event) {
    }

    /**
     * 异常处理
     * 用于处理非ErrorEvent类型的异常，如网络异常、解析异常等
     *
     * @param throwable 异常
     */
    default void onException(Throwable throwable) {
    }

    /**
     * 完成事件
     */
    default void onComplete() {
    }
}
