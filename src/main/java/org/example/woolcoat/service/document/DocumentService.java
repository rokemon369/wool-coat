package org.example.woolcoat.service.document;

import org.example.woolcoat.entity.KbDocument;
import org.example.woolcoat.entity.KbDocumentChunk;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库文档服务接口（完整）
 */
public interface DocumentService {

    /**
     * 上传文档并自动分片、构建RAG索引
     * @param file 上传的文件（支持txt/md/pdf）
     * @param userId 用户ID（文档归属）
     * @return 上传结果提示
     */
    String uploadDocument(MultipartFile file, String userId);

    /**
     * 查询用户的所有文档
     * @param userId 用户ID
     * @return 文档列表
     */
    List<KbDocument> listDocuments(String userId);

    /**
     * 删除文档（同时删除分片和RAG索引）
     * @param docId 文档ID
     * @param userId 用户ID（校验归属）
     * @return 删除结果
     */
    String deleteDocument(Long docId, String userId);

    /**
     * 查询文档的所有分片
     * @param docId 文档ID
     * @param userId 用户ID（校验归属）
     * @return 分片列表
     */
    List<KbDocumentChunk> listDocumentChunks(Long docId, String userId);
}
