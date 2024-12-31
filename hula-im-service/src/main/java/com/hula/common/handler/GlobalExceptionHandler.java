package com.hula.common.handler;

import com.hula.domain.vo.res.ApiResult;
import com.hula.exception.BusinessException;
import com.hula.enums.CommonErrorEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


/**
 * 自定义全局异常捕获
 *
 * @author nyh
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * validation参数校验异常
     */
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ApiResult methodArgumentNotValidExceptionExceptionHandler(MethodArgumentNotValidException e) {
        StringBuilder errorMsg = new StringBuilder();
        e.getBindingResult().getFieldErrors().forEach(x -> errorMsg.append(x.getField()).append(x.getDefaultMessage()).append(","));
        String message = errorMsg.toString();
        log.info("validation parameters error！The reason is:{}", message);
        return ApiResult.fail(CommonErrorEnum.PARAM_VALID.getErrorCode(), message.substring(0, message.length() - 1));
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(value = BusinessException.class)
    public ApiResult<String> businessExceptionHandler(BusinessException e) {
        log.error(String.format("business exception！The reason is: %s", e.getMessage()), e);
        return ApiResult.fail(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(value = Throwable.class)
    public ApiResult<String> throwableExceptionHandler(Throwable e) {
        log.error(String.format("system error！The reason is: %s", e.getMessage()), e);
        return ApiResult.fail(CommonErrorEnum.SYSTEM_ERROR.getErrorCode(), CommonErrorEnum.SYSTEM_ERROR.getErrorMsg());
    }
}
