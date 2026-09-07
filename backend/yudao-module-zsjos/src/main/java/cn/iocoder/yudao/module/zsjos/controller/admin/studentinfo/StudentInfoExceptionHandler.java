package cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.StudentInfoFormController;
import cn.iocoder.yudao.module.zsjos.controller.pub.studentinfo.PublicStudentInfoFormController;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import jakarta.validation.ConstraintViolationException;

/** Prevent framework error-body logging from persisting submitted personal information. */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {StudentInfoFormController.class, PublicStudentInfoFormController.class,
        StudentInfoConfigController.class})
public class StudentInfoExceptionHandler {
    @ExceptionHandler(Exception.class)
    public CommonResult<?> handle(Exception error, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        if (error instanceof ServiceException business) return CommonResult.error(business.getCode(), business.getMessage());
        if (error instanceof AccessDeniedException) return CommonResult.error(403, "没有操作权限");
        if (error instanceof MethodArgumentNotValidException || error instanceof HttpMessageNotReadableException
                || error instanceof ServletRequestBindingException || error instanceof ConstraintViolationException)
            return CommonResult.error(400, "提交格式不符合要求，请检查字段");
        // Exception messages and nested causes can contain SQL parameters or submitted values.
        log.error("Student information request failed: type={}, location={}", error.getClass().getName(),
                error.getStackTrace().length == 0 ? "unknown" : error.getStackTrace()[0]);
        return CommonResult.error(500, "信息收集服务暂不可用，请稍后重试");
    }
}
