package org.example.woolcoat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.woolcoat.entity.KbDocumentChunk;

import java.util.List;

/**
 * 知识库文档分片 Mapper（沿用之前的实体类名，含批量插入方法）
 */
@Mapper
public interface KbDocumentChunkMapper extends BaseMapper<KbDocumentChunk> {

    /**
     * 批量插入文档分片（对应 DocumentService 中的 insertBatch，变量名无变更）
     */
    void insertBatch(@Param("chunkList") List<KbDocumentChunk> chunkList);
}
