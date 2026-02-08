package org.example.woolcoat.utils;

import cn.hutool.core.util.StrUtil;

/**
 * Token计算工具类（适配LLM Token估算）
 */
public class TokenUtils {
    // 中文/英文Token系数（1中文字≈1Token，1英文单词≈0.75Token）
    private static final float CHINESE_TOKEN_COEFFICIENT = 1.0f;
    private static final float ENGLISH_TOKEN_COEFFICIENT = 0.75f;

    /**
     * 估算文本Token数
     */
    public static int calculateToken(String text) {
        if (StrUtil.isBlank(text)) {
            return 0;
        }
        int chineseCharCount = 0;
        int englishCharCount = 0;
        for (char c : text.toCharArray()) {
            if (isChineseChar(c)) {
                chineseCharCount++;
            } else if (Character.isLetter(c)) {
                englishCharCount++;
            }
        }
        // 英文按单词估算（每6个字母≈1单词）
        int englishWordCount = englishCharCount / 6;
        return (int) (chineseCharCount * CHINESE_TOKEN_COEFFICIENT + englishWordCount * ENGLISH_TOKEN_COEFFICIENT);
    }

    /**
     * 判断是否为中文字符
     */
    private static boolean isChineseChar(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
    }

    /**
     * 按Token数裁剪文本（保留末尾）
     */
    public static String trimTextByToken(String text, int maxToken) {
        if (calculateToken(text) <= maxToken || StrUtil.isBlank(text)) {
            return text;
        }
        // 从后往前截取，保证核心信息
        int startIndex = Math.max(0, text.length() - (int) (maxToken / CHINESE_TOKEN_COEFFICIENT));
        return text.substring(startIndex);
    }
}
