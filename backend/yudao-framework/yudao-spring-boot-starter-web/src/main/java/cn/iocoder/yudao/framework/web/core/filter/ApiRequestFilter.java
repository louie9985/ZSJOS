package cn.iocoder.yudao.framework.web.core.filter;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 过滤 /admin-api、/app-api、/part-api 等 API 请求的过滤器
 *
 * @author 芋道源码
 */
@RequiredArgsConstructor
public abstract class ApiRequestFilter extends OncePerRequestFilter {

    protected final WebProperties webProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 只过滤 API 请求的地址
        String apiUri = request.getRequestURI().substring(request.getContextPath().length());
        return !(isApiPath(apiUri, webProperties.getAdminApi().getPrefix())
                || isApiPath(apiUri, webProperties.getAppApi().getPrefix())
                || isApiPath(apiUri, webProperties.getPartnerApi().getPrefix())
                || isApiPath(apiUri, webProperties.getPublicApi().getPrefix()));
    }

    private static boolean isApiPath(String uri, String prefix) {
        return StrUtil.equals(uri, prefix) || StrUtil.startWith(uri, prefix + "/");
    }

}
