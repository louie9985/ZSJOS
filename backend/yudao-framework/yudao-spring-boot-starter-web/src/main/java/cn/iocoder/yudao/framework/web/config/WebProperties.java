package cn.iocoder.yudao.framework.web.config;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "yudao.web")
@Validated
@Data
public class WebProperties {

    @NotNull(message = "APP API 不能为空")
    private Api appApi = new Api("/app-api", "**.controller.app.**");
    /**
     * 独立兼职端 API。必须放在通用 appApi 之前匹配，避免兼职 Controller 被重复挂载。
     */
    @NotNull(message = "PARTNER API 不能为空")
    private Api partnerApi = new Api("/part-api", "**.controller.app.partner.**");
    /** Public, deliberately unauthenticated API surface. */
    @NotNull(message = "Public API 不能为空")
    private Api publicApi = new Api("/public-api", "**.controller.pub.**");
    /**
     * App API 子路径对应的用户类型。未配置的 App API 仍使用 MEMBER。
     */
    private Map<@NotEmpty String, @NotNull Integer> appApiUserTypePrefixes = new LinkedHashMap<>();
    @NotNull(message = "Admin API 不能为空")
    private Api adminApi = new Api("/admin-api", "**.controller.admin.**");

    @NotNull(message = "Admin UI 不能为空")
    private Ui adminUi;

    @AssertTrue(message = "App API 用户类型子路径配置无效")
    public boolean isAppApiUserTypePrefixesValid() {
        return appApiUserTypePrefixes != null && appApiUserTypePrefixes.entrySet().stream().allMatch(entry ->
                entry.getKey() != null && entry.getKey().startsWith("/") && entry.getKey().endsWith("/")
                        && UserTypeEnum.valueOf(entry.getValue()) != null);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Valid
    public static class Api {

        /**
         * API 前缀，实现所有 Controller 提供的 RESTFul API 的统一前缀
         *
         *
         * 意义：通过该前缀，避免 Swagger、Actuator 意外通过 Nginx 暴露出来给外部，带来安全性问题
         *      这样，Nginx 只需要配置转发到 /api/* 的所有接口即可。
         *
         * @see YudaoWebAutoConfiguration#configurePathMatch(PathMatchConfigurer)
         */
        @NotEmpty(message = "API 前缀不能为空")
        private String prefix;

        /**
         * Controller 所在包的 Ant 路径规则
         *
         * 主要目的是，给该 Controller 设置指定的 {@link #prefix}
         */
        @NotEmpty(message = "Controller 所在包不能为空")
        private String controller;

    }

    @Data
    @Valid
    public static class Ui {

        /**
         * 访问地址
         */
        private String url;

    }

}
