package org.example.woolcoat.config;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.example.woolcoat.utils.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.nio.file.Paths;

/**
 * Lucene配置（索引目录、分词器）
 */
@Configuration
public class LuceneConfig {

    @Value("${rag.lucene.index-path:./lucene/index/}")
    private String luceneIndexPath;

    /**
     * Lucene索引目录
     */
    @Bean
    public Directory luceneDirectory() {
        try {
            // 创建索引目录（不存在则创建）
            FileUtils.createDirIfNotExist(luceneIndexPath);
            return FSDirectory.open(Paths.get(luceneIndexPath));
        } catch (Exception e) {
            throw new RuntimeException("初始化Lucene索引目录失败：" + e.getMessage());
        }
    }

    /**
     * IK中文分词器（智能分词）
     */
    @Bean
    public IKAnalyzer ikAnalyzer() {
        return new IKAnalyzer(true); // true=智能分词，false=细粒度分词
    }
}
