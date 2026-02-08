package org.example.woolcoat.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Lucene工具类（中文分词、索引辅助）
 */
public class LuceneUtils {

    /**
     * 中文分词（IK分词器）
     */
    public static List<String> splitChinese(String text) {
        List<String> words = new ArrayList<>();
        if (StringUtils.isBlank(text)) {
            return words;
        }
        // 初始化IK分词器（智能分词）
        try (Analyzer analyzer = new IKAnalyzer(true);
             TokenStream tokenStream = analyzer.tokenStream("", new StringReader(text))) {
            CharTermAttribute attr = tokenStream.addAttribute(CharTermAttribute.class);
            tokenStream.reset();
            while (tokenStream.incrementToken()) {
                words.add(attr.toString());
            }
            tokenStream.end();
        } catch (IOException e) {
            throw new RuntimeException("中文分词失败：" + e.getMessage());
        }
        return words;
    }
}
