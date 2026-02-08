package org.example.woolcoat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 知识库文档分片表（对应 kb_document_chunk 表，变量名与之前SQL/代码一致）
 */
@Data
@TableName("kb_document_chunk") // 与数据库表名一致，无变更
public class KbDocumentChunk implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（沿用之前SQL的字段名）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联文档ID（沿用之前代码的变量名）
     */
    private Long documentId;

    /**
     * 分片文本内容（沿用之前代码的变量名）
     */
    private String chunkContent;

    /**
     * 分片序号（从 0 开始，沿用之前代码的变量名）
     */
    private Integer chunkIndex;

    /**
     * 分片关键词（沿用之前代码的变量名）
     */
    private String keyword;

    /**
     * 创建时间（沿用之前SQL的字段名）
     */
    private Date createTime;

    // 用途：关联用户ID，RAG检索时校验文档归属，实现用户数据隔离
    private String userId;
}
