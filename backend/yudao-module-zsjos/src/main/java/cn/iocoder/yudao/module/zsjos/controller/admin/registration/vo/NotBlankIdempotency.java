package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@NotBlank
@Size(max = 64)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
public @interface NotBlankIdempotency {
    String message() default "幂等键不能为空且不能超过 64 个字符";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
