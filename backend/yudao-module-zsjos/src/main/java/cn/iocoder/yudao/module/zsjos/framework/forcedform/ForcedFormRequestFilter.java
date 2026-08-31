package cn.iocoder.yudao.module.zsjos.framework.forcedform;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.core.filter.ApiRequestFilter;
import cn.iocoder.yudao.module.zsjos.controller.admin.forcedform.vo.ForcedFormStatusRespVO;
import cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants;
import cn.iocoder.yudao.module.zsjos.service.forcedform.ForcedFormService;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2ClientConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * Workbench marked admin-api requests must stop when the current employee still has
 * pending forced forms. Vue Admin requests do not send the workbench marker header,
 * so the shared admin-api contract remains usable for management pages.
 */
public class ForcedFormRequestFilter extends ApiRequestFilter {

    public static final String WORKBENCH_MARKER_HEADER = "X-ZSJOS-Workbench-Platform";

    private static final List<String> WHITELIST_URLS = List.of(
            "/admin-api/system/auth/login",
            "/admin-api/system/auth/refresh-token",
            "/admin-api/system/auth/get-permission-info",
            "/admin-api/system/auth/logout",
            "/admin-api/zsjos/forced-form/pending",
            "/admin-api/zsjos/forced-form/status",
            "/admin-api/zsjos/forced-form/*",
            "/admin-api/zsjos/forced-form/*/runtime",
            "/admin-api/zsjos/forced-form/*/attachment/upload",
            "/admin-api/zsjos/forced-form/*/submit");

    private final ForcedFormService forcedFormService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public ForcedFormRequestFilter(WebProperties webProperties, ForcedFormService forcedFormService) {
        super(webProperties);
        this.forcedFormService = forcedFormService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isWorkbenchRequest(request) || isWhitelisted(request)) {
            chain.doFilter(request, response);
            return;
        }
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null
                || !UserTypeEnum.ADMIN.getValue().equals(loginUser.getUserType())
                || !isAllowedClient(loginUser.getClientId())) {
            chain.doFilter(request, response);
            return;
        }

        ForcedFormStatusRespVO status = forcedFormService.status(loginUser.getId());
        if (status == null || status.getPendingCount() == null || status.getPendingCount() <= 0) {
            chain.doFilter(request, response);
            return;
        }

        CommonResult<ForcedFormStatusRespVO> result = CommonResult.error(ZsjosErrorCodeConstants.FORCED_FORM_REQUIRED);
        result.setData(status);
        response.setStatus(HttpServletResponse.SC_OK);
        ServletUtils.writeJSON(response, result);
    }

    private boolean isWorkbenchRequest(HttpServletRequest request) {
        return StrUtil.isNotBlank(request.getHeader(WORKBENCH_MARKER_HEADER));
    }

    private boolean isAllowedClient(String clientId) {
        return StrUtil.equalsAny(clientId, OAuth2ClientConstants.CLIENT_ID_ZSJOS_PC,
                OAuth2ClientConstants.CLIENT_ID_ZSJOS_MOBILE);
    }

    private boolean isWhitelisted(HttpServletRequest request) {
        String apiUri = request.getRequestURI().substring(request.getContextPath().length());
        for (String whitelistUrl : WHITELIST_URLS) {
            if (pathMatcher.match(whitelistUrl, apiUri)) {
                return true;
            }
        }
        return false;
    }
}
