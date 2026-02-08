package org.example.woolcoat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 知识库文档元数据表（对应 kb_document 表，变量名与之前SQL/代码一致）
 */
@Data
@TableName("kb_document") // 与数据库表名一致，无变更
public class KbDocument implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（沿用之前SQL的字段名）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文档名称（沿用之前代码的变量名）
     */
    private String documentName;

    /**
     * 文档后缀（md/txt/pdf，沿用之前代码的变量名）
     */
    private String documentSuffix;

    /**
     * 文档大小（字节，沿用之前代码的变量名）
     */
    private Long documentSize;

    /**
     * 用户ID（默认 default_user，沿用之前代码的变量名）
     */
    private String userId;

    /**
     * 文档标签（多个用逗号分隔，沿用之前代码的变量名）
     */
    private String tag;

    /**
     * 状态：1-有效，0-删除（沿用之前SQL的字段名）
     */
    private Integer status;

    /**
     * 创建时间（沿用之前SQL的字段名）
     */
    private Date createTime;

    /**
     * 更新时间（沿用之前SQL的字段名）
     */
    private Date updateTime;
}
