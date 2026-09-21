package cn.iocoder.yudao.module.system.service.notify;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.module.system.api.notify.NotifyChannelAdapter;
import cn.iocoder.yudao.module.system.api.notify.NotifyChannelType;
import cn.iocoder.yudao.module.system.api.notify.NotifyRecipientWecomUserProvider;
import cn.iocoder.yudao.module.system.api.notify.NotifyWecomClickUrlProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDeliveryContext;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult;
import cn.iocoder.yudao.module.system.dal.dataobject.social.SocialClientDO;
import cn.iocoder.yudao.module.system.dal.mysql.social.SocialClientMapper;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Sends rendered business notifications through a tenant-configured WeCom self-built application. */
@Component
public class WecomNotifyChannelAdapter implements NotifyChannelAdapter {

    private static final String ACCESS_TOKEN_KEY = "system:wecom:access-token:%s:%s";
    private static final String GET_TOKEN_URL = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=%s&corpsecret=%s";
    private static final String SEND_URL = "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=%s";

    @Autowired(required = false)
    private NotifyChannelConfigService configService;
    @Resource
    private SocialClientMapper socialClientMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired(required = false)
    private List<NotifyRecipientWecomUserProvider> wecomUserProviders = List.of();
    @Autowired(required = false)
    private List<NotifyWecomClickUrlProvider> clickUrlProviders = List.of();

    @Override
    public String getChannelCode() {
        return NotifyChannelType.WECOM;
    }

    @Override
    public NotifySendResult send(NotifyDeliveryContext context) {
        String toUser;
        SocialClientDO client;
        try {
            if (configService == null || configService.getEnabled(context.getTenantId(), NotifyChannelType.WECOM) == null) {
                return NotifySendResult.failure("WECOM_DISABLED", "企业微信渠道未启用或未配置", false);
            }
            toUser = resolveToUser(context);
            if (StrUtil.isBlank(toUser)) {
                return NotifySendResult.success("WECOM_RECIPIENT_SKIPPED");
            }
            client = resolveClient(context.getUserType());
            if (client == null || StrUtil.hasBlank(client.getClientId(), client.getClientSecret(), client.getAgentId())
                    || !NumberUtil.isInteger(client.getAgentId())) {
                return NotifySendResult.failure("WECOM_CREDENTIAL_MISSING", "企业微信自建应用凭据未配置", false);
            }
        } catch (RuntimeException exception) {
            // No POST has started: configuration/recipient lookup failures are safe to retry.
            return NotifySendResult.failure("WECOM_PREPARE_FAILED", "企微发送配置或接收人查询失败", true);
        }
        NotifyDeliveryContext prepared;
        try { prepared = prepare(context); }
        catch (RuntimeException exception) {
            return NotifySendResult.failure("WECOM_PREPARE_FAILED", "企微链接准备失败", true);
        }
        String accessToken;
        try { accessToken = getAccessToken(context.getTenantId(), client); }
        catch (RuntimeException exception) {
            return NotifySendResult.failure("WECOM_TOKEN_FAILED", "企微应用令牌获取失败", true);
        }
        NotifySendResult result = doSend(accessToken, client, toUser, prepared);
        if (java.util.Set.of("WECOM_API_40014", "WECOM_API_42001", "WECOM_API_40001").contains(
                StrUtil.blankToDefault(result.getErrorCode(), ""))) {
            try {
                evictAccessToken(context.getTenantId(), client);
                return doSend(getAccessToken(context.getTenantId(), client), client, toUser, prepared); }
            catch (RuntimeException exception) {
                return NotifySendResult.failure("WECOM_TOKEN_FAILED", "企微应用令牌刷新失败", true);
            }
        }
        return result;
    }

    public NotifyDeliveryContext prepare(NotifyDeliveryContext context) {
        if (context.isWecomClickPrepared()) return context;
        String url = clickUrlProviders.stream().map(provider -> provider.createClickUrl(context))
                .filter(StrUtil::isNotBlank).findFirst().orElse(null);
        return context.toBuilder().wecomClickUrl(url).wecomClickPrepared(true).build();
    }

    private SocialClientDO resolveClient(Integer userType) {
        SocialClientDO client = socialClientMapper.selectBySocialTypeAndUserType(
                SocialTypeEnum.WECHAT_ENTERPRISE.getType(), userType);
        if (client != null) {
            // An explicit disabled application must not be bypassed by the ADMIN fallback.
            return isEnabled(client) ? client : null;
        }
        client = socialClientMapper.selectBySocialTypeAndUserType(
                SocialTypeEnum.WECHAT_ENTERPRISE.getType(), UserTypeEnum.ADMIN.getValue());
        return isEnabled(client) ? client : null;
    }

    private static boolean isEnabled(SocialClientDO client) {
        return client != null && CommonStatusEnum.ENABLE.getStatus().equals(client.getStatus());
    }

    private String resolveToUser(NotifyDeliveryContext context) {
        return wecomUserProviders.stream()
                .filter(provider -> Objects.equals(provider.getUserType(), context.getUserType()))
                .map(provider -> provider.getWecomUserId(context.getUserId()))
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    private String getAccessToken(Long tenantId, SocialClientDO client) {
        String key = accessTokenKey(tenantId, client);
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(cached)) {
            return cached;
        }
        var response = JSONUtil.parseObj(requestToken(String.format(GET_TOKEN_URL,
                HttpUtils.encodeUtf8(client.getClientId()), HttpUtils.encodeUtf8(client.getClientSecret()))));
        int errcode = response.getInt("errcode", 0);
        if (errcode != 0 || StrUtil.isBlank(response.getStr("access_token"))) {
            throw new IllegalStateException("WeCom access_token request failed");
        }
        String token = response.getStr("access_token");
        int expiresIn = response.getInt("expires_in", 7200);
        stringRedisTemplate.opsForValue().set(key, token, Duration.ofSeconds(Math.max(60, expiresIn - 120L)));
        return token;
    }

    private void evictAccessToken(Long tenantId, SocialClientDO client) {
        if (client != null) {
            stringRedisTemplate.delete(accessTokenKey(tenantId, client));
        }
    }

    private String accessTokenKey(Long tenantId, SocialClientDO client) {
        return String.format(ACCESS_TOKEN_KEY, tenantId, client.getId());
    }

    private NotifySendResult doSend(String accessToken, SocialClientDO client, String toUser,
                                    NotifyDeliveryContext context) {
        String clickUrl = context.getWecomClickUrl();
        Map<String, Object> body = StrUtil.isNotBlank(clickUrl)
                ? Map.of("touser", toUser, "msgtype", "textcard", "agentid", Integer.valueOf(client.getAgentId()),
                "textcard", Map.of("title", limit(context.getTitle(), 128),
                        "description", limit(context.getContent(), 512),
                        "url", clickUrl, "btntxt", "查看详情"))
                : Map.of("touser", toUser, "msgtype", "text", "agentid", Integer.valueOf(client.getAgentId()),
                "text", Map.of("content", limit(context.getTitle() + "\n" + context.getContent(), 2048)));
        try {
            var response = JSONUtil.parseObj(postMessage(String.format(SEND_URL, accessToken), JSONUtil.toJsonStr(body)));
            Integer errcode = response.getInt("errcode");
            if (errcode == null) return uncertain();
            if (errcode != 0) {
                boolean retryable = java.util.Set.of(-1, 45009, 45011, 40014, 42001, 40001).contains(errcode);
                return NotifySendResult.failure("WECOM_API_" + errcode, "企微拒绝本次发送", retryable);
            }
            if (StrUtil.isNotBlank(response.getStr("invaliduser"))
                    || StrUtil.isNotBlank(response.getStr("invalidparty"))
                    || StrUtil.isNotBlank(response.getStr("invalidtag"))
                    || StrUtil.isNotBlank(response.getStr("unlicenseduser"))) {
                return NotifySendResult.failure("WECOM_RECIPIENT_INVALID", "企微接收人无效、不可见或未获许可", false);
            }
            return StrUtil.isBlank(response.getStr("msgid")) ? uncertain() : NotifySendResult.success(response.getStr("msgid"));
        } catch (RuntimeException exception) {
            // A transport/response failure after POST is ambiguous; an immediate retry can duplicate a delivered message.
            return uncertain();
        }
    }

    private NotifySendResult uncertain() {
        return NotifySendResult.failure("WECOM_DELIVERY_UNCERTAIN", "无法确认企微是否已接收，请核查投递记录", false);
    }

    String requestToken(String url) {
        try (var response = cn.hutool.http.HttpRequest.get(url).timeout(10000).execute()) {
            if (!response.isOk()) throw new IllegalStateException("WeCom token HTTP failure");
            return response.body();
        }
    }

    String postMessage(String url, String body) {
        try (var response = cn.hutool.http.HttpRequest.post(url).timeout(10000).body(body).execute()) {
            if (!response.isOk()) throw new IllegalStateException("WeCom send HTTP failure");
            return response.body();
        }
    }

    private static String limit(String value, int maxLength) {
        String normalized = StrUtil.blankToDefault(value, "");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
