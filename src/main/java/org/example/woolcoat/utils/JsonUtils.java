package org.example.woolcoat.utils;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.core.util.StrUtil;

/**
 * JSON工具类（封装Hutool，统一异常处理）
 */
public class JsonUtils {

    /**
     * 转JSON字符串（格式化）
     */
    public static String toJsonStr(Object obj) {
        if (obj == null) {
            return "{}";
        }
        return JSONUtil.toJsonStr(obj);
    }

    /**
     * 解析JSON字符串为对象
     */
    public static <T> T parseJson(String jsonStr, Class<T> clazz) {
        if (StrUtil.isBlank(jsonStr)) {
            return null;
        }
        try {
            return JSONUtil.toBean(jsonStr, clazz);
        } catch (Exception e) {
            throw new RuntimeException("JSON解析失败：" + jsonStr, e);
        }
    }

    /**
     * 获取JSON中的指定字段值
     */
    public static String getJsonField(String jsonStr, String field) {
        if (StrUtil.isBlank(jsonStr) || StrUtil.isBlank(field)) {
            return null;
        }
        JSONObject json = JSONUtil.parseObj(jsonStr);
        return json.getStr(field);
    }
}
