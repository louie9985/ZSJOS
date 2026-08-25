package cn.iocoder.yudao.module.zsjos.framework.mediascreen;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
@Order(-100)
public class MediaScreenAccessFilter extends OncePerRequestFilter {
    private static final String PREFIX = "/public-api/zsjos/media-screen/";
    private final MediaScreenProperties properties;

    public MediaScreenAccessFilter(MediaScreenProperties properties) { this.properties = properties; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith(PREFIX)) { chain.doFilter(request, response); return; }
        if (!properties.isEnabled() || !"GET".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE); return;
        }
        String ip = resolveIp(request);
        Long tenantId;
        try { tenantId = Long.valueOf(request.getParameter("tenantId")); }
        catch (Exception ex) { response.sendError(HttpServletResponse.SC_FORBIDDEN); return; }
        if (!allowed(ip, tenantId)) { response.sendError(HttpServletResponse.SC_FORBIDDEN); return; }
        TenantContextHolder.setTenantId(tenantId);
        try { chain.doFilter(request, response); } finally { TenantContextHolder.clear(); }
    }

    private String resolveIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!matchesAny(remote, properties.getTrustedProxies())) return remote;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        String real = request.getHeader("X-Real-IP");
        return real == null || real.isBlank() ? remote : real.trim();
    }

    private boolean allowed(String ip, Long tenantId) {
        return properties.getClients().stream().anyMatch(c -> tenantId.equals(c.getTenantId())
                && matchesAny(ip, c.getCidrs()));
    }

    private boolean matchesAny(String ip, Iterable<String> ranges) {
        for (String range : ranges) if (matches(ip, range)) return true;
        return false;
    }

    static boolean matches(String ip, String cidr) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            String[] parts = cidr.trim().split("/", 2);
            InetAddress network = InetAddress.getByName(parts[0]);
            int bits = address.getAddress().length * 8;
            int prefix = parts.length == 1 ? bits : Integer.parseInt(parts[1]);
            if (network.getAddress().length != address.getAddress().length || prefix < 0 || prefix > bits) return false;
            byte[] a = address.getAddress(), n = network.getAddress();
            int full = prefix / 8, rem = prefix % 8;
            for (int i = 0; i < full; i++) if (a[i] != n[i]) return false;
            return rem == 0 || (a[full] & (0xff << (8 - rem))) == (n[full] & (0xff << (8 - rem)));
        } catch (UnknownHostException | NumberFormatException ex) { return false; }
    }
}
