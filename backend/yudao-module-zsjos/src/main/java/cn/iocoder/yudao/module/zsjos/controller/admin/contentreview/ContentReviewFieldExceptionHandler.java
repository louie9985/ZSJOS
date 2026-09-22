package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewFieldException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

/** Also handles field failures propagated through the existing BPM task boundary. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ContentReviewFieldExceptionHandler {
    @ExceptionHandler(ContentReviewFieldException.class)
    public CommonResult<Map<String, Object>> handle(ContentReviewFieldException error) {
        CommonResult<Map<String, Object>> result = CommonResult.error(error.getCode(), error.getMessage());
        result.setData(error.details());
        return result;
    }
}
