package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.hutool.crypto.SecureUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.PurchaseIntentRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.PurchaseIntentSaveDraftReqVO;
import cn.iocoder.yudao.module.zsjos.controller.pub.payment.vo.PublicPaymentDetailRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentGatewayEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentIntentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentTransactionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PurchaseIntentDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.PaymentGatewayEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.PaymentIntentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.PaymentTransactionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.PurchaseIntentMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.framework.allinpay.AllinpayClient;
import cn.iocoder.yudao.module.zsjos.framework.allinpay.AllinpayProperties;
import cn.iocoder.yudao.module.zsjos.service.lead.PersonIdentityWriteService;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import org.springframework.dao.DuplicateKeyException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class PurchaseIntentService {
    @Resource private PurchaseIntentMapper purchaseIntentMapper;
    @Resource private PaymentIntentMapper paymentIntentMapper;
    @Resource private PaymentTransactionMapper transactionMapper;
    @Resource private PaymentGatewayEventMapper gatewayEventMapper;
    @Resource private AllinpayProperties allinpayProperties;
    @Resource private LeadMapper leadMapper;
    @Resource private PersonIdentityWriteService personIdentityWriteService;

    public PurchaseIntentRespVO current(PurchaseIntentSaveDraftReqVO request, Long userId) {
        resolvePerson(request, false);
        if (request.getPersonId() == null) return null;
        PurchaseIntentDO intent = purchaseIntentMapper.selectActive(request.getLeadId(), request.getPersonId(), request.getPurchaseType(), request.getSourceKey(), userId);
        return intent == null ? null : convert(intent);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseIntentRespVO saveDraft(PurchaseIntentSaveDraftReqVO request, Long userId) {
        resolvePerson(request, true);
        validateDraft(request);
        PurchaseIntentDO intent = request.getId() == null ? null : purchaseIntentMapper.selectByIdForUpdate(request.getId());
        if (intent == null || !sameSource(intent, request)) {
            intent = purchaseIntentMapper.selectActive(request.getLeadId(), request.getPersonId(), request.getPurchaseType(), request.getSourceKey(), userId);
        }
        String draftJson = JsonUtils.toJsonString(request.getDraft());
        String itemJson = JsonUtils.toJsonString(request.getItems());
        if (intent == null) {
            intent = new PurchaseIntentDO();
            intent.setPurchaseIntentNo("PI" + LocalDateTime.now().toString().replaceAll("\\D", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
            intent.setCollectionMode(request.getCollectionMode()).setPurchaseType(request.getPurchaseType())
                    .setLeadId(request.getLeadId()).setPersonId(request.getPersonId()).setOpportunityId(request.getOpportunityId())
                    .setSourceKey(request.getSourceKey())
                    .setInitiatorUserId(userId).setOwnerUserId(userId).setDraftJson(draftJson).setItemSnapshotJson(itemJson)
                    .setTotalAmount(scale(request.getTotalAmount())).setCurrency("CNY").setSnapshotLocked(false)
                    .setStatus("draft").setLastIdempotencyKey(request.getIdempotencyKey()).setVersion(0);
            purchaseIntentMapper.insert(intent);
            return convert(intent);
        }
        if (!userId.equals(intent.getInitiatorUserId()) && !userId.equals(intent.getOwnerUserId())) throw exception(PURCHASE_INTENT_PERMISSION_DENIED);
        if (request.getIdempotencyKey().equals(intent.getLastIdempotencyKey())) return convert(intent);
        if (Boolean.TRUE.equals(intent.getSnapshotLocked())) {
            PaymentIntentDO payment = paymentIntentMapper.selectLatestByPurchaseIntent(intent.getId());
            boolean switchingMode = !java.util.Objects.equals(intent.getCollectionMode(), request.getCollectionMode());
            if (switchingMode) {
                if (payment == null || !"created".equals(payment.getStatus()) || StrUtil.isNotBlank(payment.getReqsn())) {
                    throw exception(PURCHASE_INTENT_PAYMENT_CONFLICT);
                }
                payment.setStatus("closed").setClosedAt(LocalDateTime.now()).setCloseReason("收款路径切换");
                paymentIntentMapper.updateById(payment); intent.setSnapshotLocked(false); purchaseIntentMapper.updateById(intent);
            } else if (!sameAmount(intent.getTotalAmount(), request.getTotalAmount())
                    || !java.util.Objects.equals(intent.getItemSnapshotJson(), itemJson)) {
                throw exception(PURCHASE_INTENT_PAYMENT_CONFLICT);
            }
        }
        int version = intent.getVersion() == null ? 0 : intent.getVersion();
        if (request.getVersion() != null && !request.getVersion().equals(version)) throw exception(PURCHASE_INTENT_VERSION_CONFLICT);
        if (purchaseIntentMapper.updateDraft(intent.getId(), version, request.getCollectionMode(), draftJson, itemJson, scale(request.getTotalAmount()), request.getIdempotencyKey()) == 0) throw exception(PURCHASE_INTENT_VERSION_CONFLICT);
        intent.setCollectionMode(request.getCollectionMode()).setSourceKey(request.getSourceKey()).setDraftJson(draftJson).setItemSnapshotJson(itemJson).setTotalAmount(scale(request.getTotalAmount())).setVersion(version + 1);
        return convert(intent);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseIntentRespVO createPaymentLink(PurchaseIntentSaveDraftReqVO request, Long userId) {
        if (!"online_link".equals(request.getCollectionMode())) throw exception(PURCHASE_INTENT_DRAFT_INVALID);
        if (!allinpayProperties.isEnabled() || StrUtil.isBlank(allinpayProperties.getPublicBaseUrl())
                || StrUtil.isBlank(allinpayProperties.getLinkHmacSecret())) throw exception(PAYMENT_GATEWAY_UNAVAILABLE);
        PurchaseIntentRespVO saved = saveDraft(request, userId);
        PurchaseIntentDO intent = purchaseIntentMapper.selectByIdForUpdate(saved.getId());
        PaymentIntentDO existing = paymentIntentMapper.selectLatestByPurchaseIntent(intent.getId());
        if (existing != null && List.of("created", "waiting", "paid").contains(existing.getStatus())) return convert(intent);
        String token = randomToken();
        String no = "PAY" + LocalDateTime.now().toString().replaceAll("\\D", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        LocalDateTime expires = LocalDateTime.now().plusHours(Math.max(1, allinpayProperties.getLinkTtlHours()));
        PaymentIntentDO payment = new PaymentIntentDO();
        payment.setPaymentOrderNo(no).setPurchaseIntentId(intent.getId()).setLeadId(intent.getLeadId()).setPersonId(intent.getPersonId())
                .setOpportunityId(intent.getOpportunityId()).setStatus("created").setExpectedAmount(intent.getTotalAmount()).setCurrency("CNY")
                .setProductItemsSnapshot(intent.getItemSnapshotJson()).setInitiatorUserId(userId).setLinkTokenHash(SecureUtil.sha256(token))
                .setExpiresAt(expires).setProvider("allinpay").setVersion(0);
        String base = allinpayProperties.getPublicBaseUrl();
        if (base == null || base.isBlank()) throw exception(PAYMENT_GATEWAY_UNAVAILABLE);
        payment.setLinkUrl(base.replaceAll("/+$", "") + "/pay/" + no + "?token=" + token);
        paymentIntentMapper.insert(payment);
        intent.setSnapshotLocked(true).setStatus("draft");
        purchaseIntentMapper.updateById(intent);
        return convert(intent);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseIntentRespVO refreshPayment(Long id, Long userId) {
        PurchaseIntentDO intent = purchaseIntentMapper.selectByIdForUpdate(id);
        if (intent == null) throw exception(PURCHASE_INTENT_NOT_EXISTS);
        if (!userId.equals(intent.getOwnerUserId()) && !userId.equals(intent.getInitiatorUserId())) throw exception(PURCHASE_INTENT_PERMISSION_DENIED);
        PaymentIntentDO payment = paymentIntentMapper.selectLatestByPurchaseIntent(id);
        if (payment != null && allinpayProperties.isEnabled()) {
            if ("created".equals(payment.getStatus()) && payment.getExpiresAt() != null
                    && payment.getExpiresAt().isBefore(LocalDateTime.now())) {
                payment.setStatus("expired"); paymentIntentMapper.updateById(payment);
            } else if ("waiting".equals(payment.getStatus()) && StrUtil.isNotBlank(payment.getReqsn())) {
                AllinpayClient.GatewayResponse result = new AllinpayClient(allinpayProperties).query(payment.getReqsn());
                recordGatewayEvent(payment, "payment_query", result);
                payment.setQueriedAt(LocalDateTime.now()); paymentIntentMapper.updateById(payment);
                int expected = payment.getExpectedAmount().movePointRight(2).intValueExact();
                if (isPaid(result, expected)) confirmPaid(payment, result, "query");
                else if (payment.getExpiresAt() != null && payment.getExpiresAt().isBefore(LocalDateTime.now())) closeExpired(payment);
            }
        }
        return convert(intent);
    }

    public PublicPaymentDetailRespVO publicDetail(String no, String token) {
        if (!allinpayProperties.isEnabled()) throw exception(PAYMENT_GATEWAY_UNAVAILABLE);
        PaymentIntentDO payment = requirePublic(no, token);
        if ("created".equals(payment.getStatus()) && payment.getExpiresAt() != null
                && payment.getExpiresAt().isBefore(LocalDateTime.now())) {
            payment.setStatus("expired"); paymentIntentMapper.updateById(payment);
        }
        PublicPaymentDetailRespVO response = new PublicPaymentDetailRespVO(); response.setPaymentIntentNo(no).setAmount(payment.getExpectedAmount())
                .setCurrency(payment.getCurrency()).setStatus(payment.getStatus()).setExpiresAt(payment.getExpiresAt());
        List<PurchaseIntentSaveDraftReqVO.Item> items = JsonUtils.parseArray(payment.getProductItemsSnapshot(), PurchaseIntentSaveDraftReqVO.Item.class);
        response.setDescription(items.isEmpty() ? "课程服务" : StrUtil.blankToDefault(items.get(0).getSkuName(), items.get(0).getSkuRef()));
        return response;
    }

    public Object publicOrder(String no, String token, String channel) {
        if (!allinpayProperties.isEnabled()) throw exception(PAYMENT_GATEWAY_UNAVAILABLE);
        PaymentIntentDO payment = requirePublic(no, token);
        if (payment.getExpiresAt() != null && payment.getExpiresAt().isBefore(LocalDateTime.now())) {
            if ("created".equals(payment.getStatus())) {
                payment.setStatus("expired"); paymentIntentMapper.updateById(payment);
            } else if ("waiting".equals(payment.getStatus()) && StrUtil.isNotBlank(payment.getReqsn())
                    && allinpayProperties.isEnabled()) {
                closeExpired(payment);
            }
            throw exception(PAYMENT_LINK_INVALID);
        }
        if (!"created".equals(payment.getStatus()) && !("waiting".equals(payment.getStatus()) && "alipay".equals(channel))) throw exception(PAYMENT_LINK_INVALID);
        AllinpayClient client = new AllinpayClient(allinpayProperties);
        String reqsn = payment.getReqsn(); if (reqsn == null || reqsn.isBlank()) reqsn = "ZS" + payment.getPaymentOrderNo();
        int fen = payment.getExpectedAmount().movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        String body = "课程服务-" + payment.getPaymentOrderNo();
        if ("wechat".equals(channel)) {
            Map<String, String> fields = client.unionOrder(reqsn, fen, body, payment.getPaymentOrderNo(), payment.getExpiresAt());
            Map<String, String> auditFields = new LinkedHashMap<>(fields); auditFields.remove("sign");
            PaymentGatewayEventDO event = new PaymentGatewayEventDO().setEventId("payment_wechat_order:" + payment.getPaymentOrderNo() + ":" + UUID.randomUUID())
                    .setPaymentOrderId(payment.getId()).setEventType("payment_wechat_order").setRequestPayload(JsonUtils.toJsonString(auditFields)).setSignatureValid(true).setProcessingResult("request");
            event.setTenantId(payment.getTenantId()); gatewayEventMapper.insert(event);
            payment.setReqsn(reqsn).setChannel("wechat").setStatus("waiting"); paymentIntentMapper.updateById(payment);
            return fields;
        }
        if (!"alipay".equals(channel)) throw exception(PAYMENT_LINK_INVALID);
            AllinpayClient.GatewayResponse result = client.alipay(reqsn, fen, body);
            recordGatewayEvent(payment, "payment_alipay_order", result);
        if (!result.isSignatureValid() || !"SUCCESS".equals(result.getRetcode()) || result.getPayinfo() == null
                || !isAllowedPayinfo(result.getPayinfo())) throw exception(PAYMENT_GATEWAY_UNAVAILABLE);
        payment.setReqsn(reqsn).setChannel("alipay").setStatus("waiting"); paymentIntentMapper.updateById(payment); return result.getPayinfo();
    }

    public String publicWechatOrderHtml(String no, String token) {
        Object result = publicOrder(no, token, "wechat");
        @SuppressWarnings("unchecked") Map<String, String> fields = (Map<String, String>) result;
        StringBuilder html = new StringBuilder("<!doctype html><html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head><body><form id=\"pay\" method=\"post\" action=\"")
                .append(HtmlUtils.htmlEscape(allinpayProperties.getUnionorderUrl())).append("\">");
        fields.forEach((name, value) -> html.append("<input type=\"hidden\" name=\"")
                .append(HtmlUtils.htmlEscape(name)).append("\" value=\"")
                .append(HtmlUtils.htmlEscape(value)).append("\">") );
        return html.append("</form><script>document.getElementById('pay').submit()</script></body></html>").toString();
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean publicStatus(String no, String token) {
        PaymentIntentDO payment = requirePublic(no, token);
        if ("waiting".equals(payment.getStatus()) && payment.getReqsn() != null && allinpayProperties.isEnabled()) {
        AllinpayClient.GatewayResponse result = new AllinpayClient(allinpayProperties).query(payment.getReqsn());
            recordGatewayEvent(payment, "payment_query", result);
            if (isPaid(result, payment.getExpectedAmount().movePointRight(2).intValueExact())) confirmPaid(payment, result, "query");
            else if (payment.getExpiresAt() != null && payment.getExpiresAt().isBefore(LocalDateTime.now())) closeExpired(payment);
        }
        return "paid".equals(payment.getStatus());
    }

    private boolean isPaid(AllinpayClient.GatewayResponse result, int expectedFen) {
        return result.isSignatureValid() && "SUCCESS".equals(result.getRetcode()) && "0000".equals(result.getTrxstatus())
                && Integer.valueOf(expectedFen).equals(result.getTrxamt());
    }

    private void closeExpired(PaymentIntentDO payment) {
        AllinpayClient.GatewayResponse result = new AllinpayClient(allinpayProperties).close(payment.getReqsn());
        recordGatewayEvent(payment, "payment_close", result);
        if (result.isSignatureValid() && "SUCCESS".equals(result.getRetcode())
                && (StrUtil.isBlank(result.getTrxstatus()) || "0000".equals(result.getTrxstatus()))) {
            payment.setStatus("closed").setClosedAt(LocalDateTime.now()).setCloseReason("支付链接到期关单");
            paymentIntentMapper.updateById(payment);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void notify(Map<String, Object> payload) {
        String reqsn = text(payload, "reqsn"); PaymentIntentDO payment = paymentIntentMapper.selectByReqsn(reqsn); if (payment == null) throw exception(PAYMENT_CALLBACK_INVALID);
        AllinpayClient client = new AllinpayClient(allinpayProperties);
        if (!client.verify(payload)) throw exception(PAYMENT_CALLBACK_INVALID);
        int fen; try { fen = Integer.parseInt(text(payload, "trxamt")); } catch (Exception ex) { throw exception(PAYMENT_CALLBACK_INVALID); }
        int expected = payment.getExpectedAmount().movePointRight(2).intValueExact();
        if (!allinpayProperties.getCusid().equals(text(payload, "cusid")) || !allinpayProperties.getAppid().equals(text(payload, "appid")) || expected != fen) throw exception(PAYMENT_CALLBACK_INVALID);
        if ("SUCCESS".equals(text(payload, "retcode")) && "0000".equals(text(payload, "trxstatus"))) confirmPaid(payment, AllinpayClient.GatewayResponse.from(payload, true), "notify");
    }

    private void confirmPaid(PaymentIntentDO payment, AllinpayClient.GatewayResponse result, String source) {
        payment = paymentIntentMapper.selectByIdForUpdate(payment.getId());
        if (payment == null || "paid".equals(payment.getStatus())) return;
        String eventId = SecureUtil.sha256(payment.getTenantId() + ":payment:" + payment.getPaymentOrderNo() + ":" + result.getTrxid() + ":" + result.getTrxstatus());
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("reqsn", result.getReqsn()); audit.put("trxid", result.getTrxid()); audit.put("chnltrxid", result.getChnltrxid());
        audit.put("trxstatus", result.getTrxstatus()); audit.put("trxamt", result.getTrxamt());
        PaymentGatewayEventDO event = new PaymentGatewayEventDO(); event.setTenantId(payment.getTenantId());
        event.setEventId(eventId).setPaymentOrderId(payment.getId()).setEventType("payment_" + source)
                .setResponsePayload(JsonUtils.toJsonString(audit)).setSignatureValid(true).setProcessingResult("accepted");
        try { gatewayEventMapper.insert(event); } catch (DuplicateKeyException ignored) { return; }
        PaymentTransactionDO transaction = new PaymentTransactionDO(); transaction.setTenantId(payment.getTenantId());
        transaction.setPaymentOrderId(payment.getId()).setAmount(payment.getExpectedAmount()).setCurrency("CNY")
                .setPaidAt(LocalDateTime.now()).setExternalChannel(payment.getChannel()).setExternalTransactionNo(result.getTrxid())
                .setCallbackEventId(eventId).setReqsn(payment.getReqsn()).setTrxId(result.getTrxid()).setChannelTransactionNo(result.getChnltrxid())
                .setAmountFen(result.getTrxamt()).setSource(source); transactionMapper.insert(transaction);
        payment.setStatus("paid").setPaidAt(LocalDateTime.now()); paymentIntentMapper.updateById(payment);
    }

    private PaymentIntentDO requirePublic(String no, String token) {
        if (token == null || token.isBlank()) throw exception(PAYMENT_LINK_INVALID);
        PaymentIntentDO payment = paymentIntentMapper.selectByNoAndTokenHash(no, SecureUtil.sha256(token));
        if (payment == null) throw exception(PAYMENT_LINK_INVALID);
        return payment;
    }

    private PurchaseIntentRespVO convert(PurchaseIntentDO intent) {
        PurchaseIntentRespVO response = new PurchaseIntentRespVO(); response.setId(intent.getId()).setPurchaseIntentNo(intent.getPurchaseIntentNo())
                .setCollectionMode(intent.getCollectionMode()).setPurchaseType(intent.getPurchaseType()).setLeadId(intent.getLeadId()).setPersonId(intent.getPersonId())
                .setDraft(intent.getDraftJson() == null ? Map.of() : JsonUtils.parseObject(intent.getDraftJson(), Map.class)).setItemSnapshotJson(intent.getItemSnapshotJson())
                .setTotalAmount(intent.getTotalAmount()).setCurrency(intent.getCurrency()).setVersion(intent.getVersion()).setPaymentLocked(intent.getSnapshotLocked());
        PaymentIntentDO payment = paymentIntentMapper.selectLatestByPurchaseIntent(intent.getId());
        if (payment != null) { response.setPaymentIntentId(payment.getId()).setPaymentIntentNo(payment.getPaymentOrderNo()).setPaymentUrl(payment.getLinkUrl())
                .setPaymentStatus(payment.getStatus()).setPaymentExpiresAt(payment.getExpiresAt()); response.setDisplayStatus("paid".equals(payment.getStatus()) ? "paid_pending_submission" : "waiting".equals(payment.getStatus()) || "created".equals(payment.getStatus()) ? "pending_payment" : "invalid"); }
        else response.setDisplayStatus("order_draft"); return response;
    }

    private void validateDraft(PurchaseIntentSaveDraftReqVO request) {
        if (!List.of("online_link", "offline_paid").contains(request.getCollectionMode()) || request.getPersonId() == null || request.getItems() == null || request.getItems().isEmpty()) throw exception(PURCHASE_INTENT_DRAFT_INVALID);
        BigDecimal sum = request.getItems().stream().map(PurchaseIntentSaveDraftReqVO.Item::getActualAmount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        if (sum.compareTo(scale(request.getTotalAmount())) != 0 || sum.signum() <= 0) throw exception(PURCHASE_INTENT_DRAFT_INVALID);
    }
    private void resolvePerson(PurchaseIntentSaveDraftReqVO request, boolean createExternal) {
        if (request.getPersonId() != null) return;
        if (request.getLeadId() != null) {
            LeadDO lead = leadMapper.selectById(request.getLeadId());
            if (lead == null) throw exception(PURCHASE_INTENT_DRAFT_INVALID);
            request.setPersonId(lead.getPersonId());
            return;
        }
        if (!createExternal || !"external_repurchase".equals(request.getPurchaseType())) return;
        String name = StrUtil.trim(String.valueOf(request.getDraft().getOrDefault("studentName", "")));
        String mobile = StrUtil.trim(String.valueOf(request.getDraft().getOrDefault("studentMobile", "")));
        String wechat = StrUtil.trim(String.valueOf(request.getDraft().getOrDefault("studentWechatId", "")));
        if (StrUtil.isBlank(name) || StrUtil.isAllBlank(mobile, wechat)) throw exception(PURCHASE_INTENT_DRAFT_INVALID);
        PersonDO person = personIdentityWriteService.resolveOrCreate(name, mobile, wechat, "active");
        request.setPersonId(person.getId());
    }
    private static BigDecimal scale(BigDecimal value) { return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP); }
    private static boolean sameAmount(BigDecimal a, BigDecimal b) { return scale(a).compareTo(scale(b)) == 0; }
    private static boolean sameSource(PurchaseIntentDO a, PurchaseIntentSaveDraftReqVO b) { return java.util.Objects.equals(a.getLeadId(), b.getLeadId()) && java.util.Objects.equals(a.getPersonId(), b.getPersonId()) && java.util.Objects.equals(a.getPurchaseType(), b.getPurchaseType()); }
    private String randomToken() { try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(allinpayProperties.getLinkHmacSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256")); return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(UUID.randomUUID().toString().getBytes())); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private boolean isAllowedPayinfo(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) return false;
            Set<String> hosts = java.util.Arrays.stream(allinpayProperties.getAllowedPayinfoHosts().split(","))
                    .map(String::trim).filter(host -> !host.isEmpty()).collect(java.util.stream.Collectors.toSet());
            return !hosts.isEmpty() && hosts.stream().anyMatch(host -> uri.getHost().equalsIgnoreCase(host)
                    || uri.getHost().toLowerCase().endsWith("." + host.toLowerCase()));
        } catch (Exception ex) { return false; }
    }
    private static String text(Map<String, Object> payload, String key) { return payload.get(key) == null ? "" : payload.get(key).toString(); }

    private void recordGatewayEvent(PaymentIntentDO payment, String type, AllinpayClient.GatewayResponse result) {
        Map<String, Object> payload = new LinkedHashMap<>(); payload.put("retcode", result.getRetcode()); payload.put("trxstatus", result.getTrxstatus()); payload.put("reqsn", result.getReqsn()); payload.put("trxid", result.getTrxid()); payload.put("trxamt", result.getTrxamt());
        PaymentGatewayEventDO event = new PaymentGatewayEventDO().setEventId(type + ":" + payment.getPaymentOrderNo() + ":" + UUID.randomUUID())
                .setPaymentOrderId(payment.getId()).setEventType(type).setResponsePayload(JsonUtils.toJsonString(payload)).setSignatureValid(result.isSignatureValid()).setProcessingResult(result.getRetcode());
        event.setTenantId(payment.getTenantId()); gatewayEventMapper.insert(event);
    }
}
