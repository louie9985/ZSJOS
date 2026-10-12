package cn.iocoder.yudao.module.zsjos.service.pointsredemption;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.pointsredemption.PointsRequestDO;
import tools.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** External bearer credentials and response bodies must never be logged. */
@Component
public class PointsPlatformClient {
    public static final String PREFIX = "zsjos.points-redemption.";
    @Resource private ConfigApi configs;
    @Value("${zsjos.points-redemption.allowed-origins:https://zsj.nbility.ai}") private String allowedOrigins;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public String currentBaseUrl() {
        String base = configs.getConfigValueByKey(PREFIX + "base-url");
        validateBase(base);
        return base;
    }
    public void requireConfigured() {
        if (!"true".equals(configs.getConfigValueByKey(PREFIX + "enabled"))) throw PointsErrors.unavailable();
        currentBaseUrl(); key();
    }
    private String key() {
        String key = configs.getConfigValueByKey(PREFIX + "api-key");
        if (key == null || key.length() < 32 || key.chars().anyMatch(c -> c <= 32 || c >= 127))
            throw PointsErrors.unavailable();
        return key;
    }
    void validateBase(String base) {
        try {
            URI uri = URI.create(base);
            String origin = uri.getScheme() + "://" + uri.getRawAuthority();
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || !"/api/partner/v1".equals(uri.getPath())
                    || Arrays.stream(allowedOrigins.split(",")).map(String::trim).noneMatch(origin::equals))
                throw PointsErrors.unavailable();
        } catch (IllegalArgumentException | NullPointerException e) { throw PointsErrors.unavailable(); }
    }
    public JsonNode issue(PointsRequestDO row) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", row.getOrderId()); body.put("quantity", 1);
        body.put("creditsAmount", row.getCreditsAmount());
        body.put("expiresAt", row.getExpiresAt() == null ? null
                : row.getExpiresAt().atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime().toString());
        body.put("note", "中世健积分申请 " + row.getRequestNo());
        // An expired request may already exist remotely after a lost response; query before any retry.
        {
            try { return send(row.getPlatformUrl(), "/redemption-batches?orderId="
                    + URLEncoder.encode(row.getOrderId(), StandardCharsets.UTF_8), null); }
            catch (PlatformFailure e) { if (!"not_found".equals(e.code)) throw e; }
        }
        return send(row.getPlatformUrl(), "/redemption-batches", new tools.jackson.databind.json.JsonMapper().writeValueAsString(body));
    }
    public JsonNode code(PointsRequestDO row, boolean disable) {
        if (row.getExternalCodeId() == null || !row.getExternalCodeId().matches("[A-Za-z0-9-]{1,64}"))
            throw PointsErrors.invalid("兑换码外部标识无效");
        return send(row.getPlatformUrl(), "/redemption-codes/" + row.getExternalCodeId()
                + (disable ? "/disable" : ""), disable ? "" : null);
    }
    JsonNode send(String base, String path, String body) {
        validateBase(base);
        int timeout = number("timeout-seconds", 30, 5, 60);
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(timeout)).header("Authorization", "Bearer " + key())
                .header("Accept", "application/json");
        if (body == null) req.GET();
        else req.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        try {
            var response = http.send(req.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (var stream = response.body()) { bytes = stream.readNBytes(32769); }
            if (bytes.length > 32768) throw new PlatformFailure("invalid_response", false, 0);
            JsonNode json;
            try { json = new tools.jackson.databind.json.JsonMapper().readTree(bytes); }
            catch (RuntimeException invalid) { json = null; }
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                String code = json == null ? "http_" + status : json.path("error").path("code").asText("http_" + status);
                if (!code.matches("[a-z_0-9]{1,64}")) code = "http_" + status;
                long retryAfter = 0;
                String retry = response.headers().firstValue("Retry-After").orElse("");
                try { retryAfter = Long.parseLong(retry); } catch (NumberFormatException ignored) {
                    try { retryAfter = Duration.between(Instant.now(), ZonedDateTime.parse(retry,
                            java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds(); }
                    catch (RuntimeException ignoredDate) { }
                }
                throw new PlatformFailure(code, status == 429 || status >= 500 && !"not_configured".equals(code),
                        Math.max(0, retryAfter));
            }
            if (json == null || !json.path("data").isObject()) throw new PlatformFailure("invalid_response", false, 0);
            return json.path("data");
        } catch (java.io.IOException e) { throw new PlatformFailure("network_error", true, 0); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new PlatformFailure("interrupted", true, 0); }
    }
    public int number(String suffix, int fallback, int min, int max) {
        String value = configs.getConfigValueByKey(PREFIX + suffix);
        if (value == null || value.isBlank()) return fallback;
        try { int n = Integer.parseInt(value); if (n >= min && n <= max) return n; }
        catch (NumberFormatException ignored) { }
        throw PointsErrors.unavailable();
    }
    public static class PlatformFailure extends RuntimeException {
        public final String code;
        public final boolean retryable;
        public final long retryAfter;
        public PlatformFailure(String code, boolean retryable, long retryAfter) {
            super(code); this.code = code; this.retryable = retryable; this.retryAfter = retryAfter;
        }
    }
}
