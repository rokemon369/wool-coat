package org.example.woolcoat.service.rag;

import java.util.List;
import java.util.Map;

/**
 * RAG知识库检索服务（基于Lucene）
 */
public interface RagSearchService {

    /**
     * 构建文档分片的Lucene索引（文档上传后调用）
     * @param docChunkId 文档分片ID
     * @param chunkContent 分片内容
     * @param docId 所属文档ID
     */
    void buildIndex(Long docChunkId, String chunkContent, Long docId);

    /**
     * 检索知识库（根据问题匹配分片内容）
     * @param question 用户问题
     * @param topK 返回前K条结果
     * @param userId 用户ID（仅检索该用户的文档）
     * @return 检索结果（分片内容+文档ID+相似度）
     */
    List<Map<String, Object>> search(String question, int topK, String userId);

    /**
     * 删除文档索引（文档删除时调用）
     * @param docId 文档ID
     */
    void deleteIndex(Long docId);
}
