package org.example.woolcoat.vo.response;

import lombok.Data;

/**
 * 全局统一响应（沿用之前的 VO 包路径，无变更）
 */
@Data
public class CommonResponse<T> {

    /**
     * 响应状态码：200-成功，500-失败
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String msg;

    /**
     * 响应数据
     */
    private T data;

    // 成功响应（无数据）
    public static <T> CommonResponse<T> success() {
        return success(null);
    }

    // 成功响应（有数据，沿用之前的变量名风格）
    public static <T> CommonResponse<T> success(T data) {
        CommonResponse<T> response = new CommonResponse<>();
        response.setCode(200);
        response.setMsg("操作成功");
        response.setData(data);
        return response;
    }

    // 失败响应（沿用之前的变量名风格）
    public static <T> CommonResponse<T> fail(String msg) {
        CommonResponse<T> response = new CommonResponse<>();
        response.setCode(500);
        response.setMsg(msg);
        response.setData(null);
        return response;
    }
}
