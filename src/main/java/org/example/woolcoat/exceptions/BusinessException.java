package org.example.woolcoat.exceptions;

import lombok.Getter;

/**
 * 自定义业务异常（沿用之前的错误提示风格，无变更）
 */
@Getter
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
