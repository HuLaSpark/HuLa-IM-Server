package com.hula.domain.vo.res;

import com.hula.common.MDCKey;
import com.hula.enums.ErrorEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.slf4j.MDC;

/**
 * 通用返回体
 * @author nyh
 */
@Data
@Schema(description ="基础返回体")
public class ApiResult<T> {
    @Schema(description ="成功标识。true/false")
    private Boolean success;
    @Schema(description ="错误码")
    private Integer errCode;
    @Schema(description ="错误消息")
    private String errMsg;
    @Schema(description ="追踪id")
    private String tid;
    @Schema(description ="返回对象")
    private T data;

    private ApiResult() {
        this.setTid(MDC.get(MDCKey.TID));
    }

    public static <T> ApiResult<T> success() {
        ApiResult<T> result = new ApiResult<T>();
        result.setData(null);

        result.setSuccess(Boolean.TRUE);
        return result;
    }

    public static <T> ApiResult<T> success(T data) {
        ApiResult<T> result = new ApiResult<T>();
        result.setData(data);
        result.setSuccess(Boolean.TRUE);
        return result;
    }

    public static <T> ApiResult<T> fail(Integer code, String msg) {
        ApiResult<T> result = new ApiResult<T>();
        result.setSuccess(Boolean.FALSE);
        result.setErrCode(code);
        result.setErrMsg(msg);
        return result;
    }

    public static <T> ApiResult<T> fail(ErrorEnum errorEnum) {
        ApiResult<T> result = new ApiResult<>();
        result.setSuccess(Boolean.FALSE);
        result.setErrCode(errorEnum.getErrorCode());
        result.setErrMsg(errorEnum.getErrorMsg());
        return result;
    }

}
