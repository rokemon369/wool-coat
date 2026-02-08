package org.example.woolcoat.service.rag.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.example.woolcoat.entity.KbDocumentChunk;
import org.example.woolcoat.mapper.KbDocumentChunkMapper;
import org.example.woolcoat.service.rag.RagSearchService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于Lucene的RAG检索服务实现（真实中文检索）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LuceneRagSearchServiceImpl implements RagSearchService {

    private final Directory luceneDirectory; // Lucene索引目录
    private final Analyzer ikAnalyzer; // IK分词器
    private final KbDocumentChunkMapper chunkMapper; // 文档分片Mapper

    // Lucene字段名定义
    private static final String FIELD_CHUNK_ID = "chunkId"; // 分片ID
    private static final String FIELD_DOC_ID = "docId"; // 文档ID
    private static final String FIELD_CONTENT = "content"; // 分片内容

    @Override
    public void buildIndex(Long docChunkId, String chunkContent, Long docId) {
        if (docChunkId == null || docId == null || chunkContent.isBlank()) {
            throw new RuntimeException("构建索引参数不能为空");
        }
        try {
            // 1. 创建IndexWriter（写入索引）
            IndexWriterConfig config = new IndexWriterConfig(ikAnalyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND); // 追加模式
            IndexWriter indexWriter = new IndexWriter(luceneDirectory, config);

            // 2. 创建Lucene Document
            Document doc = new Document();
            doc.add(new LongPoint(FIELD_CHUNK_ID, docChunkId)); // 分片ID（可检索）
            doc.add(new StringField(FIELD_CHUNK_ID, docChunkId.toString(), Field.Store.YES)); // 存储分片ID
            doc.add(new LongPoint(FIELD_DOC_ID, docId)); // 文档ID（可检索）
            doc.add(new StringField(FIELD_DOC_ID, docId.toString(), Field.Store.YES)); // 存储文档ID
            doc.add(new TextField(FIELD_CONTENT, chunkContent, Field.Store.YES)); // 分片内容（分词+存储）

            // 3. 写入索引
            indexWriter.addDocument(doc);
            indexWriter.commit();
            indexWriter.close();

            log.info("RAG索引构建成功：docChunkId={}，docId={}", docChunkId, docId);
        } catch (Exception e) {
            throw new RuntimeException("构建RAG索引失败：" + e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> search(String question, int topK, String userId) {
        if (question.isBlank() || topK <= 0) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> searchResults = new ArrayList<>();
        try {
            // 1. 创建IndexSearcher（检索索引）
            DirectoryReader reader = DirectoryReader.open(luceneDirectory);
            IndexSearcher searcher = new IndexSearcher(reader);

            // 2. 构建查询（IK分词+内容检索）
            QueryParser parser = new QueryParser(FIELD_CONTENT, ikAnalyzer);
            Query query = parser.parse(QueryParser.escape(question)); // 转义特殊字符

            // 3. 执行检索（返回前topK条）
            TopDocs topDocs = searcher.search(query, topK);
            ScoreDoc[] scoreDocs = topDocs.scoreDocs;

            // 4. 解析检索结果
            for (ScoreDoc scoreDoc : scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                Long chunkId = Long.parseLong(doc.get(FIELD_CHUNK_ID));
                Long docId = Long.parseLong(doc.get(FIELD_DOC_ID));
                String content = doc.get(FIELD_CONTENT);
                float score = scoreDoc.score; // 相似度得分（越高越相关）

                // 校验文档归属（仅返回当前用户的文档）
                LambdaQueryWrapper<KbDocumentChunk> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(KbDocumentChunk::getId, chunkId)
                        .eq(KbDocumentChunk::getDocumentId, docId);
                KbDocumentChunk chunk = chunkMapper.selectOne(wrapper);
                if (chunk == null || !userId.equals(chunk.getUserId())) {
                    continue; // 跳过非当前用户的文档
                }

                // 构建结果Map
                Map<String, Object> result = new HashMap<>();
                result.put("chunkId", chunkId);
                result.put("docId", docId);
                result.put("content", content);
                result.put("similarity", score); // 相似度
                result.put("chunkIndex", chunk.getChunkIndex());
                searchResults.add(result);
            }

            reader.close();
            log.info("RAG检索完成：question={}，返回{}条结果", question, searchResults.size());
        } catch (Exception e) {
            throw new RuntimeException("RAG检索失败：" + e.getMessage());
        }
        return searchResults;
    }

    @Override
    public void deleteIndex(Long docId) {
        if (docId == null) {
            return;
        }
        try {
            IndexWriterConfig config = new IndexWriterConfig(ikAnalyzer);
            IndexWriter indexWriter = new IndexWriter(luceneDirectory, config);
            // 删除指定文档ID的所有索引
            Query query = LongPoint.newExactQuery(FIELD_DOC_ID, docId);
            indexWriter.deleteDocuments(query);
            indexWriter.commit();
            indexWriter.close();
            log.info("RAG索引删除成功：docId={}", docId);
        } catch (Exception e) {
            throw new RuntimeException("删除RAG索引失败：" + e.getMessage());
        }
    }
}