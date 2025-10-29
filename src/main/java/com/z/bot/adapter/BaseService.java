package com.z.bot.adapter;

import com.z.bot.adapter.model.chat.AppInfoResponse;
import com.z.bot.adapter.model.chat.AppParametersResponse;
import com.z.bot.adapter.model.file.FileUploadRequest;
import com.z.bot.adapter.model.file.FileUploadResponse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Dify 基础客户端接口
 * 包含所有客户端共用的基础功能
 * 参考: https://github.com/imfangs/dify-java-client
 */
public interface BaseService extends AutoCloseable {

    /**
     * 上传文件
     *
     * @param file 文件
     * @param user 用户标识
     * @return 上传响应
     * @throws IOException IO异常
     */
    FileUploadResponse uploadFile(File file, String user) throws IOException;

    /**
     * 上传文件
     *
     * @param request 请求
     * @param file    文件
     * @return 响应
     * @throws IOException IO异常
     */
    FileUploadResponse uploadFile(FileUploadRequest request, File file) throws IOException;

    /**
     * 上传文件
     *
     * @param request    请求
     * @param inputStream 输入流
     * @param fileName   文件名
     * @return 响应
     * @throws IOException IO异常
     */
    FileUploadResponse uploadFile(FileUploadRequest request, InputStream inputStream, String fileName) throws IOException;

    /**
     * 获取应用基本信息
     *
     * @return 响应
     * @throws IOException      IO异常
     */
    AppInfoResponse getAppInfo() throws IOException;

    /**
     * 获取应用参数
     *
     * @return 响应
     * @throws IOException      IO异常
     */
    AppParametersResponse getAppParameters() throws IOException;

    /**
     * 关闭客户端资源
     * 重写AutoCloseable.close()方法，确保不抛出受检异常
     */
    @Override
    void close();
}
