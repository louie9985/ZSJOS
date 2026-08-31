package cn.iocoder.yudao.module.zsjos.service.wecom;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDeliveryContext;
import cn.iocoder.yudao.module.zsjos.controller.pub.wecom.vo.PublicWecomClickRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.Resource;
import java.net.URI;
import java.time.Duration;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.WECOM_CLICK_TICKET_INVALID;

@Service
public class WecomClickTicketService {

    private static final String TICKET_KEY_PREFIX = "zsjos:wecom:click-ticket:";
    private static final String AUDIENCE_ADMIN = "ADMIN";
    private static final String AUDIENCE_PARTNER = "PARTNER";
    private static final String ADMIN_FALLBACK = "/messages/all";
    private static final String PARTNER_FALLBACK = "/messages";

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private LeadMapper leadMapper;

    @Value("${zsjos.wecom.workbench-base-url:}")
    private String workbenchBaseUrl;
    @Value("${zsjos.wecom.partner-h5-base-url:}")
    private String partnerH5BaseUrl;
    @Value("${zsjos.wecom.ticket-ttl-minutes:30}")
    private long ticketTtlMinutes;

    public String createClickUrl(NotifyDeliveryContext context) {
        String audience = audienceOf(context.getUserType());
        if (audience == null) {
            return null;
        }
        String baseUrl = AUDIENCE_ADMIN.equals(audience) ? workbenchBaseUrl : partnerH5BaseUrl;
        if (!hasValidBaseUrl(baseUrl)) {
            return null;
        }
        String ticket = Base64.encodeUrlSafe(RandomUtil.randomBytes(32));
        TicketPayload payload = new TicketPayload();
        payload.setTenantId(context.getTenantId());
        payload.setUserType(context.getUserType());
        payload.setAudience(audience);
        payload.setSceneCode(context.getSceneCode());
        payload.setSourceEventKey(context.getSourceEventKey());
        payload.setActionType(context.getActionType());
        payload.setBizType(context.getBizType());
        payload.setBizId(context.getBizId());
        if ("lead".equals(context.getBizType())) {
            LeadDO lead = context.getBizId() == null ? null : leadMapper.selectById(context.getBizId());
            if (lead == null || StrUtil.isBlank(lead.getLeadNo())) return null;
            payload.setLeadNo(lead.getLeadNo());
        }
        stringRedisTemplate.opsForValue().set(TICKET_KEY_PREFIX + ticket, JsonUtils.toJsonString(payload),
                Duration.ofMinutes(Math.max(1, ticketTtlMinutes)));
        String entryPath = AUDIENCE_ADMIN.equals(audience) ? "/zsjos/wecom-click" : "/wecom/click";
        return UriComponentsBuilder.fromUriString(trimTrailingSlash(baseUrl)).path(entryPath)
                .queryParam("ticket", ticket).build().toUriString();
    }

    public PublicWecomClickRespVO resolve(String ticket) {
        if (StrUtil.isBlank(ticket)) {
            throw exception(WECOM_CLICK_TICKET_INVALID);
        }
        String json = stringRedisTemplate.opsForValue().getAndDelete(TICKET_KEY_PREFIX + ticket);
        TicketPayload payload = StrUtil.isBlank(json) ? null : JsonUtils.parseObject(json, TicketPayload.class);
        if (payload == null || payload.getTenantId() == null || payload.getAudience() == null) {
            throw exception(WECOM_CLICK_TICKET_INVALID);
        }
        PublicWecomClickRespVO response = new PublicWecomClickRespVO();
        response.setAudience(payload.getAudience());
        response.setActionType(payload.getActionType());
        response.setFallbackPath(AUDIENCE_ADMIN.equals(payload.getAudience()) ? ADMIN_FALLBACK : PARTNER_FALLBACK);
        response.setTargetPath(resolveTargetPath(payload));
        return response;
    }

    private static String resolveTargetPath(TicketPayload payload) {
        if (!"business_detail".equals(payload.getActionType())
                || payload.getBizId() == null || StrUtil.isBlank(payload.getBizType())) {
            return null;
        }
        if (AUDIENCE_ADMIN.equals(payload.getAudience())) {
            if ("lead".equals(payload.getBizType())) {
                return StrUtil.isBlank(payload.getLeadNo()) ? null
                        : UriComponentsBuilder.fromPath("/zsjos/leads/manage")
                        .queryParam("leadNo", payload.getLeadNo()).build().toUriString();
            }
            if ("sales_order".equals(payload.getBizType())) {
                return "/zsjos/sales-order-approvals?workType=approval&orderId=" + payload.getBizId();
            }
            if ("student".equals(payload.getBizType())) {
                return "/zsjos/my-students?personId=" + payload.getBizId();
            }
            if ("student_service".equals(payload.getBizType())) {
                return "/zsjos/my-students?serviceRelationId=" + payload.getBizId();
            }
            if ("production-ticket".equals(payload.getBizType())) {
                return "/zsjos/production-tickets?ticketId=" + payload.getBizId();
            }
            return null;
        }
        if (!AUDIENCE_PARTNER.equals(payload.getAudience())) {
            return null;
        }
        return switch (payload.getBizType()) {
            case "lead" -> "/lead/" + payload.getBizId();
            case "cashback" -> "/earnings";
            case "withdrawal" -> "/withdrawal/" + payload.getBizId();
            case "feedback" -> "/feedback/" + payload.getBizId();
            default -> null;
        };
    }

    private static String audienceOf(Integer userType) {
        if (UserTypeEnum.ADMIN.getValue().equals(userType)) {
            return AUDIENCE_ADMIN;
        }
        if (UserTypeEnum.PARTNER.getValue().equals(userType)) {
            return AUDIENCE_PARTNER;
        }
        return null;
    }

    private static boolean hasValidBaseUrl(String baseUrl) {
        String normalized = trimTrailingSlash(baseUrl);
        if (StrUtil.isBlank(normalized)) {
            return false;
        }
        try {
            URI uri = URI.create(normalized);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null && uri.getRawQuery() == null
                    && uri.getRawFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String trimTrailingSlash(String value) {
        return value == null ? "" : value.trim().replaceAll("/+$", "");
    }

    @Data
    private static class TicketPayload {
        private Long tenantId;
        private Integer userType;
        private String audience;
        private String sceneCode;
        private String sourceEventKey;
        private String actionType;
        private String bizType;
        private Long bizId;
        private String leadNo;
    }
}
