package com.z.bot.adapter;

import com.z.bot.adapter.model.datasets.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Dify 知识库客户端接口
 * 提供知识库相关的操作
 * 参考: https://github.com/imfangs/dify-java-client
 */
public interface DatasetsService {

    /**
     * 创建空知识库
     *
     * @param request 创建知识库请求
     * @return 知识库信息
     * @throws IOException      IO异常
     */
    com.z.bot.adapter.model.datasets.DatasetResponse createDataset(CreateDatasetRequest request) throws IOException;

    /**
     * 获取知识库列表
     *
     * @param page  页码
     * @param limit 每页数量
     * @return 知识库列表
     * @throws IOException      IO异常
     */
    com.z.bot.adapter.model.datasets.DatasetListResponse getDatasets(Integer page, Integer limit) throws IOException;

    DatasetResponse getDataset(String datasetId) throws IOException;

    /**
     * 删除知识库
     *
     * @param datasetId 知识库ID
     * @return 响应
     * @throws IOException      IO异常
     */
    com.z.bot.adapter.model.common.SimpleResponse deleteDataset(String datasetId) throws IOException;

    /**
     * 通过文本创建文档
     *
     * @param datasetId 知识库ID
     * @param request   创建文档请求
     * @return 文档信息
     * @throws IOException IO异常
     */
    DocumentResponse createDocumentByText(String datasetId, CreateDocumentByTextRequest request) throws IOException;

    /**
     * 通过文件创建文档
     *
     * @param datasetId 知识库ID
     * @param request   创建文档请求
     * @param file      文件
     * @return 文档信息
     * @throws IOException IO异常
     */
    DocumentResponse createDocumentByFile(String datasetId, CreateDocumentByFileRequest request, File file) throws IOException;

    /**
     * 通过文件创建文档
     *
     * @param datasetId   知识库ID
     * @param request     创建文档请求
     * @param inputStream 文件输入流
     * @param fileName    文件名
     * @return 文档信息
     * @throws IOException IO异常
     */
    DocumentResponse createDocumentByFile(String datasetId, CreateDocumentByFileRequest request, InputStream inputStream, String fileName) throws IOException;

    /**
     * 通过文本更新文档
     *
     * @param datasetId  知识库ID
     * @param documentId 文档ID
     * @param request    更新文档请求
     * @return 文档信息
     * @throws IOException IO异常
     */
    DocumentResponse updateDocumentByText(String datasetId, String documentId, UpdateDocumentByTextRequest request) throws IOException;

    /**
     * 通过文件更新文档
     *
     * @param datasetId  知识库ID
     * @param documentId 文档ID
     * @param request    更新文档请求
     * @param file       文件
     * @return 文档信息
     * @throws IOException IO异常
     */
    DocumentResponse updateDocumentByFile(String datasetId, String documentId, UpdateDocumentByFileRequest request, File file) throws IOException;

    /**
     * 获取文档嵌入状态
     *
     * @param datasetId 知识库ID
     * @param batch     批次号
     * @return 文档嵌入状态
     * @throws IOException IO异常
     */
    IndexingStatusResponse getIndexingStatus(String datasetId, String batch) throws IOException;

    /**
     * 删除文档
     *
     * @param datasetId  知识库ID
     * @param documentId 文档ID
     * @return 响应
     * @throws IOException      IO异常
     */
    void deleteDocument(String datasetId, String documentId) throws IOException;

    /**
     * 获取知识库文档列表
     *
     * @param datasetId 知识库ID
     * @param keyword   关键词
     * @param page      页码
     * @param limit     每页数量
     * @return 文档列表
     * @throws IOException      IO异常
     */
    com.z.bot.adapter.model.datasets.DocumentListResponse getDocuments(String datasetId, String keyword, Integer page, Integer limit) throws IOException;

    /**
     * 新增文档分段
     *
     * @param datasetId  知识库ID
     * @param documentId 文档ID
     * @param request    新增分段请求
     * @return 分段信息
     * @throws IOException IO异常
     */
    SegmentListResponse createSegments(String datasetId, String documentId, CreateSegmentsRequest request) throws IOException;

    /**
     * 查询文档分段
     *
     * @param datasetId  知识库ID
     * @param documentId 文档ID
     * @param keyword    关键词
     * @param status     状态
     * @return 分段列表
     * @throws IOException      IO异常
     */
    com.z.bot.adapter.model.datasets.SegmentListResponse getSegments(String datasetId, String documentId, String keyword, String status) throws IOException;

    /**
     * 删除文档分段
     *
     * @param datasetId  知识库ID
     * @param documentId 文档ID
     * @param segmentId  分段ID
     * @return 响应
     * @throws IOException      IO异常
     */
    void deleteSegment(String datasetId, String documentId, String segmentId) throws IOException;

    /**
     * 更新文档分段
     *
     * @param datasetId  知识库ID
     * @param documentId 文档ID
     * @param segmentId  分段ID
     * @param request    更新分段请求
     * @return 分段信息
     * @throws IOException IO异常
     */
    SegmentResponse updateSegment(String datasetId, String documentId, String segmentId, UpdateSegmentRequest request) throws IOException;

    /**
     * 获取上传文件
     *
     * @param datasetId  知识库ID
     * @param documentId 文档ID
     * @return 文件信息
     * @throws IOException      IO异常
     */
    com.z.bot.adapter.model.datasets.UploadFileResponse getUploadFile(String datasetId, String documentId) throws IOException;

    /**
     * 检索知识库
     *
     * @param datasetId 知识库ID
     * @param request   检索请求
     * @return 检索结果
     * @throws IOException IO异常
     */
    RetrieveResponse retrieveDataset(String datasetId, RetrieveRequest request) throws IOException;
}
