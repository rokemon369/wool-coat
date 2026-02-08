package org.example.woolcoat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.woolcoat.entity.KbDocument;

/**
 * 知识库文档元数据 Mapper（沿用之前的实体类名，无变更）
 */
@Mapper
public interface KbDocumentMapper extends BaseMapper<KbDocument> {
    // 继承 BaseMapper，自带 CRUD 方法，无需额外编写（如 insert、selectById 等）
}
