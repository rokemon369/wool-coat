package org.example.woolcoat.service.document.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.woolcoat.entity.KbDocument;
import org.example.woolcoat.entity.KbDocumentChunk;
import org.example.woolcoat.exceptions.BusinessException;
import org.example.woolcoat.mapper.KbDocumentChunkMapper;
import org.example.woolcoat.mapper.KbDocumentMapper;
import org.example.woolcoat.service.document.DocumentService;
import org.example.woolcoat.service.rag.RagSearchService;
import org.example.woolcoat.utils.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 知识库文档服务实现（完整，包含分片、RAG索引构建、用户隔离）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    // 依赖注入
    private final KbDocumentMapper documentMapper;
    private final KbDocumentChunkMapper documentChunkMapper;
    private final RagSearchService ragSearchService; // RAG索引服务

    // 配置项（从application.yml读取）
    @Value("${rag.document.allowed-suffix:md,txt,pdf}")
    private String allowedSuffix;
    @Value("${rag.document.chunk-size:500}")
    private Integer chunkSize;
    @Value("${rag.document.chunk-overlap:50}")
    private Integer chunkOverlap;
    @Value("${rag.document.storage-path:./rag-documents/}")
    private String storagePath;

    @Override
    public String uploadDocument(MultipartFile file, String userId) {
        // 1. 校验参数
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传的文件不能为空");
        }
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException("用户ID不能为空（文档归属）");
        }

        // 2. 校验文件格式
        String originalFilename = file.getOriginalFilename();
        String suffix = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        List<String> allowedSuffixList = List.of(allowedSuffix.split(","));
        if (!allowedSuffixList.contains(suffix)) {
            throw new BusinessException("不支持的文件格式：" + suffix + "，仅支持" + allowedSuffix);
        }

        try {
            // 3. 创建文件存储目录（按用户ID隔离）
            String userStoragePath = storagePath + userId + "/";
            FileUtils.createDirIfNotExist(userStoragePath);
            String fileSaveName = UUID.randomUUID() + "." + suffix;
            String filePath = userStoragePath + fileSaveName;
            file.transferTo(new File(filePath));
            log.info("文件保存成功：{}", filePath);

            // 4. 读取文件内容
            String fileContent = FileUtils.readFileContent(filePath, suffix);
            if (!StringUtils.hasText(fileContent)) {
                throw new BusinessException("文件内容为空，无法分片");
            }

            // 5. 保存文档主记录
            KbDocument document = new KbDocument();
            document.setDocumentName(originalFilename);
            document.setDocumentSuffix(suffix);
            document.setDocumentSize(file.getSize());
            document.setUserId(userId);
            document.setCreateTime(Date.from(Instant.now()));
            documentMapper.insert(document);
            Long docId = document.getId(); // 获取新增文档ID

            // 6. 文档分片（固定长度+重叠）
            List<KbDocumentChunk> chunkList = splitDocument(fileContent, docId, userId);

            // 7. 保存分片记录到数据库
            for (KbDocumentChunk chunk : chunkList) {
                documentChunkMapper.insert(chunk);
            }

            // 8. 为每个分片构建RAG索引（核心：关联Lucene）
            for (KbDocumentChunk chunk : chunkList) {
                ragSearchService.buildIndex(chunk.getId(), chunk.getChunkContent(), docId);
            }

            return String.format("文档上传成功！文档ID：%d，分片数：%d，存储路径：%s",
                    docId, chunkList.size(), filePath);
        } catch (Exception e) {
            log.error("文档上传失败", e);
            throw new BusinessException("文档上传失败：" + e.getMessage());
        }
    }

    @Override
    public List<KbDocument> listDocuments(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException("用户ID不能为空");
        }
        // 只查询当前用户的文档
        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbDocument::getUserId, userId)
                .orderByDesc(KbDocument::getCreateTime);
        return documentMapper.selectList(wrapper);
    }

    @Override
    public String deleteDocument(Long docId, String userId) {
        if (docId == null) {
            throw new BusinessException("文档ID不能为空");
        }
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException("用户ID不能为空");
        }

        // 1. 校验文档归属（只能删除自己的文档）
        KbDocument document = documentMapper.selectById(docId);
        if (document == null) {
            throw new BusinessException("文档不存在：" + docId);
        }
        if (!userId.equals(document.getUserId())) {
            throw new BusinessException("无权限删除该文档（非本人上传）");
        }

        try {
            // 2. 删除RAG索引
            ragSearchService.deleteIndex(docId);

            // 3. 删除文档分片
            LambdaQueryWrapper<KbDocumentChunk> chunkWrapper = new LambdaQueryWrapper<>();
            chunkWrapper.eq(KbDocumentChunk::getDocumentId, docId);
            documentChunkMapper.delete(chunkWrapper);

            // 4. 删除文档主记录
            documentMapper.deleteById(docId);

            return "文档删除成功！文档ID：" + docId;
        } catch (Exception e) {
            log.error("删除文档失败", e);
            throw new BusinessException("文档删除失败：" + e.getMessage());
        }
    }

    @Override
    public List<KbDocumentChunk> listDocumentChunks(Long docId, String userId) {
        if (docId == null) {
            throw new BusinessException("文档ID不能为空");
        }
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException("用户ID不能为空");
        }

        // 1. 先校验文档归属
        KbDocument document = documentMapper.selectById(docId);
        if (document == null || !userId.equals(document.getUserId())) {
            throw new BusinessException("无权限查看该文档的分片（非本人上传）");
        }

        // 2. 查询该文档的所有分片
        LambdaQueryWrapper<KbDocumentChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbDocumentChunk::getDocumentId, docId)
                .orderByAsc(KbDocumentChunk::getChunkIndex);
        return documentChunkMapper.selectList(wrapper);
    }

    /**
     * 私有方法：文档分片（核心逻辑）
     * @param content 文档内容
     * @param docId 文档ID
     * @param userId 用户ID
     * @return 分片列表
     */
    private List<KbDocumentChunk> splitDocument(String content, Long docId, String userId) {
        List<KbDocumentChunk> chunkList = new ArrayList<>();
        int contentLength = content.length();
        int start = 0;
        int chunkIndex = 1; // 分片序号从1开始

        while (start < contentLength) {
            // 计算分片结束位置（最后一个分片取到末尾）
            int end = Math.min(start + chunkSize, contentLength);
            // 截取分片内容
            String chunkContent = content.substring(start, end);
            // 下一个分片的起始位置（重叠部分）
            start = end - chunkOverlap;

            // 构建分片实体
            KbDocumentChunk chunk = new KbDocumentChunk();
            chunk.setDocumentId(docId);
            chunk.setChunkContent(chunkContent);
            chunk.setChunkIndex(chunkIndex);
            chunk.setUserId(userId); // 关键：关联用户ID
            chunk.setCreateTime(Date.from(Instant.now()));

            chunkList.add(chunk);
            chunkIndex++;
        }

        log.info("文档分片完成：文档ID={}，分片数={}，分片大小={}，重叠={}",
                docId, chunkList.size(), chunkSize, chunkOverlap);
        return chunkList;
    }
}