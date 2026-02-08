package org.example.woolcoat.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.example.woolcoat.vo.response.CommonResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理（沿用之前的 VO 响应，无变更）
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常（后续扩展，基础层先配置）
     */
    @ExceptionHandler(BusinessException.class)
    public CommonResponse<?> handleBusinessException(BusinessException e) {
        log.error("业务异常：{}", e.getMessage());
        return CommonResponse.fail(e.getMessage());
    }

    /**
     * 捕获文件上传过大异常（沿用之前的 10MB 限制，无变更）
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public CommonResponse<?> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.error("文件上传异常：{}", e.getMessage());
        return CommonResponse.fail("文件大小超过 10MB，暂不支持");
    }

    /**
     * 捕获所有未处理异常（兜底，避免暴露敏感信息）
     */
    @ExceptionHandler(Exception.class)
    public CommonResponse<?> handleException(Exception e) {
        log.error("系统异常：", e);
        return CommonResponse.fail("系统繁忙，请稍后再试");
    }
}
