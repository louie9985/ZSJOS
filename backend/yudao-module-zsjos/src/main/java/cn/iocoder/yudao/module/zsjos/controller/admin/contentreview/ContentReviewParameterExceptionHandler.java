package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewFieldException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;
import java.util.regex.Pattern;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.CONTENT_PARAMETER_INVALID;

/** Only content-review request validation is translated; rejected values are never logged or returned. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ContentReviewController.class)
public class ContentReviewParameterExceptionHandler {
    private static final Pattern WORK = Pattern.compile("^works\\[(\\d+)](?:\\.(.*))?$");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResult<Map<String, Object>> handle(MethodArgumentNotValidException error) {
        var rejected = error.getBindingResult().getFieldError();
        String path = rejected == null ? null : rejected.getField();
        String message = rejected == null || rejected.getDefaultMessage() == null
                ? "提交参数不完整，请检查表单" : rejected.getDefaultMessage();
        Integer index = null;
        if (path != null) {
            var match = WORK.matcher(path);
            if (match.matches()) {
                index = Integer.valueOf(match.group(1));
                message = "第 " + (index + 1) + " 件作品：" + message;
            }
        }
        var detail = new ContentReviewFieldException(CONTENT_PARAMETER_INVALID.getCode(), message, path, index);
        CommonResult<Map<String, Object>> result = CommonResult.error(detail.getCode(), detail.getMessage());
        result.setData(detail.details());
        return result;
    }
}
