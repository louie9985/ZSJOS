package cn.iocoder.yudao.module.zsjos.framework.allinpay;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import lombok.Data;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class AllinpayClient {
    private static final DateTimeFormatter CHANNEL_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final AllinpayProperties properties;
    private final AllinpaySigner signer;
    private final HttpClient httpClient;

    public AllinpayClient(AllinpayProperties properties) {
        this.properties = properties;
        this.signer = new AllinpaySigner(properties);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds()))).build();
    }

    public Map<String, String> unionOrder(String reqsn, int amountFen, String body, String remark,
                                          LocalDateTime expiresAt) {
        Map<String, String> params = base("12", reqsn, amountFen, body);
        params.put("charset", "UTF-8"); params.put("returl", properties.getReturnUrl());
        params.put("remark", limit(remark, 300)); params.put("expiretime", expiresAt.format(CHANNEL_TIME));
        params.put("ishide", "1"); params.put("notify_url", properties.getNotifyUrl());
        params.put("signtype", "RSA"); params.put("randomstr", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        sign(params);
        return params;
    }

    public GatewayResponse alipay(String reqsn, int amountFen, String body) {
        Map<String, String> params = base("11", reqsn, amountFen, body);
        params.put("signtype", "RSA"); params.put("randomstr", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        params.put("paytype", "A01"); params.put("notify_url", properties.getNotifyUrl()); sign(params);
        return postJson(properties.getUnitorderPayUrl(), params);
    }

    public GatewayResponse query(String reqsn) {
        Map<String, String> params = new LinkedHashMap<>(); params.put("cusid", properties.getCusid());
        params.put("appid", properties.getAppid()); params.put("reqsn", reqsn); params.put("version", "11");
        params.put("signtype", "RSA"); params.put("randomstr", UUID.randomUUID().toString().replace("-", "")); sign(params);
        return postJson(properties.getQueryUrl(), params);
    }

    public GatewayResponse close(String reqsn) {
        Map<String, String> params = new LinkedHashMap<>(); params.put("cusid", properties.getCusid());
        params.put("appid", properties.getAppid()); params.put("oldreqsn", reqsn); params.put("version", "11");
        params.put("signtype", "RSA"); params.put("randomstr", UUID.randomUUID().toString().replace("-", "")); sign(params);
        return postJson(properties.getCloseUrl(), params);
    }

    public GatewayResponse refund(String refundReqsn, int amountFen, String originalReqsn,
                                  String originalTrxId, String reason) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("cusid", properties.getCusid()); params.put("appid", properties.getAppid());
        params.put("version", properties.getRefundVersion()); params.put("reqsn", refundReqsn);
        params.put("oldreqsn", originalReqsn); params.put("oldtrxid", originalTrxId);
        params.put("trxamt", String.valueOf(amountFen)); params.put("remark", limit(reason, 150));
        params.put("reason", limit(reason, 50)); params.put("notify_url", properties.getRefundNotifyUrl());
        if (properties.getOrgid() != null && !properties.getOrgid().isBlank()) params.put("orgid", properties.getOrgid());
        params.put("signtype", "RSA"); params.put("randomstr", UUID.randomUUID().toString().replace("-", "")); sign(params);
        return postJson(properties.getRefundUrl(), params);
    }

    public GatewayResponse queryRefund(String refundReqsn, String refundTrxId) {
        Map<String, String> params = new LinkedHashMap<>(); params.put("cusid", properties.getCusid());
        params.put("appid", properties.getAppid()); params.put("version", properties.getRefundVersion());
        if (refundReqsn != null && !refundReqsn.isBlank()) params.put("reqsn", refundReqsn);
        if (refundTrxId != null && !refundTrxId.isBlank()) params.put("trxid", refundTrxId);
        params.put("signtype", "RSA"); params.put("randomstr", UUID.randomUUID().toString().replace("-", "")); sign(params);
        String url = properties.getRefundQueryUrl();
        return postJson(url == null || url.isBlank() ? properties.getQueryUrl() : url, params);
    }

    public boolean verify(Map<String, ?> payload) { return payload.get("sign") != null && signer.verify(payload, payload.get("sign").toString()); }

    private Map<String, String> base(String version, String reqsn, int amountFen, String body) {
        Map<String, String> params = new LinkedHashMap<>(); params.put("cusid", properties.getCusid());
        params.put("appid", properties.getAppid()); params.put("version", version); params.put("trxamt", String.valueOf(amountFen));
        params.put("reqsn", reqsn); params.put("body", limit(body, 100)); if (properties.getOrgid() != null && !properties.getOrgid().isBlank()) params.put("orgid", properties.getOrgid()); return params;
    }

    private void sign(Map<String, String> params) { params.put("sign", signer.sign(params)); }

    private GatewayResponse postJson(String url, Map<String, String> params) {
        if (url == null || url.isBlank()) throw new IllegalStateException("通联接口地址未配置");
        try {
            String form = params.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(Math.max(1, properties.getReadTimeoutSeconds())))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8").POST(HttpRequest.BodyPublishers.ofString(form)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().isBlank()) {
                throw new IllegalStateException("通联接口返回异常状态: " + response.statusCode());
            }
            String raw = response.body();
            Map<String, Object> payload = JsonUtils.parseObject(raw, Map.class);
            if (payload == null || payload.isEmpty()) throw new IllegalStateException("通联接口返回内容无效");
            return GatewayResponse.from(payload, signer.verify(payload, String.valueOf(payload.getOrDefault("sign", ""))));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("通联请求被中断", ex);
        } catch (Exception ex) { throw new IllegalStateException("通联请求失败", ex); }
    }

    private static String encode(String value) { return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8); }
    private static String limit(String value, int max) { return value == null ? "" : value.substring(0, Math.min(max, value.length())); }

    @Data
    public static class GatewayResponse {
        private String retcode; private String trxstatus; private String reqsn; private String trxid; private String chnltrxid;
        private String payinfo; private Integer trxamt; private String errmsg; private boolean signatureValid; private Map<String, Object> raw;
        public static GatewayResponse from(Map<String, Object> payload, boolean valid) {
            GatewayResponse result = new GatewayResponse(); result.raw = payload; result.signatureValid = valid;
            result.retcode = text(payload, "retcode"); result.trxstatus = text(payload, "trxstatus"); result.reqsn = text(payload, "reqsn");
            result.trxid = text(payload, "trxid"); result.chnltrxid = text(payload, "chnltrxid"); result.payinfo = text(payload, "payinfo");
            result.errmsg = text(payload, "errmsg"); try { result.trxamt = payload.get("trxamt") == null ? null : Integer.valueOf(payload.get("trxamt").toString()); } catch (Exception ignored) { }
            return result;
        }
        private static String text(Map<String, Object> p, String k) { return p.get(k) == null ? "" : p.get(k).toString(); }
    }
}
