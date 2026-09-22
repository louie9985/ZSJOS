package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.ContentReviewFieldExceptionHandler;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.ContentReviewParameterExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class ContentReviewErrorContractTest {
    @Test
    void bothClientsReceiveReadableMessageWithOptionalWorkContext() {
        var error = (ContentReviewFieldException) ContentReviewFieldException.atWork(
                ContentReviewFieldException.field(CONTENT_LINK_INVALID, "leadResourceUrl", "引流资料链接"), 1);
        var response = new ContentReviewFieldExceptionHandler().handle(error);
        assertEquals(CONTENT_LINK_INVALID.getCode(), response.getCode());
        assertEquals("第 2 件作品：引流资料链接格式不正确，请填写包含有效域名的完整 HTTPS 地址", response.getMsg());
        assertEquals("works[1].leadResourceUrl", response.getData().get("fieldPath"));
        assertEquals(1, response.getData().get("workIndex"));
        assertSame(error, ContentReviewFieldException.atWork(error, 5));
    }

    @Test
    void authorizationAndUnexpectedErrorsRemainUnwrapped() {
        var denied = ServiceExceptionUtil.exception(CONTENT_REVIEW_PERMISSION_DENIED);
        assertSame(denied, ContentReviewFieldException.atWork(denied, 0));
        var unexpected = new IllegalStateException("infrastructure detail");
        assertSame(unexpected, ContentReviewFieldException.atWork(unexpected, 0));
    }

    @Test
    void beanValidationIncludesWorkAndFieldWithoutRejectedValue() throws Exception {
        var binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "works[2].title", "private rejected value",
                false, null, null, "请填写发布标题"));
        var parameter = new MethodParameter(getClass().getDeclaredMethod("accept", Object.class), 0);
        var response = new ContentReviewParameterExceptionHandler().handle(
                new MethodArgumentNotValidException(parameter, binding));
        assertEquals(CONTENT_PARAMETER_INVALID.getCode(), response.getCode());
        assertEquals("第 3 件作品：请填写发布标题", response.getMsg());
        assertEquals("works[2].title", response.getData().get("fieldPath"));
        assertEquals(2, response.getData().get("workIndex"));
        assertFalse(response.toString().contains("private rejected value"));
    }

    private void accept(Object request) { }
}
