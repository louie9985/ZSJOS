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
        if (configService == null || configService.getEnabled(context.getTenantId(), NotifyChannelType.WECOM) == null) {
            return NotifySendResult.failure("WECOM_DISABLED", "企业微信渠道未启用或未配置", false);
        }
        String toUser = resolveToUser(context);
        if (StrUtil.isBlank(toUser)) {
            return NotifySendResult.success("WECOM_RECIPIENT_SKIPPED");
        }
        SocialClientDO client = resolveClient(context.getUserType());
        if (client == null || StrUtil.hasBlank(client.getClientId(), client.getClientSecret(), client.getAgentId())
                || !NumberUtil.isInteger(client.getAgentId())) {
            return NotifySendResult.failure("WECOM_CREDENTIAL_MISSING", "企业微信自建应用凭据未配置", false);
        }
        try {
            String accessToken = getAccessToken(context.getTenantId(), client);
            return doSend(accessToken, client, toUser, context);
        } catch (Exception ex) {
            evictAccessToken(context.getTenantId(), client);
            try {
                String accessToken = getAccessToken(context.getTenantId(), client);
                return doSend(accessToken, client, toUser, context);
            } catch (Exception retryEx) {
                return NotifySendResult.failure("WECOM_SEND_FAILED", "企业微信消息发送失败", true);
            }
        }
    }

    private SocialClientDO resolveClient(Integer userType) {
        SocialClientDO client = socialClientMapper.selectBySocialTypeAndUserType(
                SocialTypeEnum.WECHAT_ENTERPRISE.getType(), userType);
        if (isEnabled(client)) {
            return client;
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
        var response = JSONUtil.parseObj(HttpUtils.get(String.format(GET_TOKEN_URL,
                HttpUtils.encodeUtf8(client.getClientId()), HttpUtils.encodeUtf8(client.getClientSecret())),
                Map.of()));
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
        String clickUrl = clickUrlProviders.stream().map(provider -> provider.createClickUrl(context))
                .filter(StrUtil::isNotBlank).findFirst().orElse(null);
        Map<String, Object> body = StrUtil.isNotBlank(clickUrl)
                ? Map.of("touser", toUser, "msgtype", "textcard", "agentid", Integer.valueOf(client.getAgentId()),
                "textcard", Map.of("title", limit(context.getTitle(), 128),
                        "description", limit(context.getContent(), 512),
                        "url", clickUrl, "btntxt", "查看详情"))
                : Map.of("touser", toUser, "msgtype", "text", "agentid", Integer.valueOf(client.getAgentId()),
                "text", Map.of("content", limit(context.getTitle() + "\n" + context.getContent(), 2048)));
        var response = JSONUtil.parseObj(HttpUtils.post(String.format(SEND_URL, accessToken), Map.of(),
                JSONUtil.toJsonStr(body)));
        int errcode = response.getInt("errcode", 0);
        if (errcode != 0) {
            throw new IllegalStateException("WeCom send failed");
        }
        return NotifySendResult.success(response.getStr("msgid"));
    }

    private static String limit(String value, int maxLength) {
        String normalized = StrUtil.blankToDefault(value, "");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
