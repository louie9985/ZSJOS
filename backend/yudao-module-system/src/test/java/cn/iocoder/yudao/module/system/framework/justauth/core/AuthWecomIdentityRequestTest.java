package cn.iocoder.yudao.module.system.framework.justauth.core;

import me.zhyd.oauth.cache.AuthStateCache;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.exception.AuthException;
import me.zhyd.oauth.model.AuthToken;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AuthWecomIdentityRequestTest {
    private AuthWecomIdentityRequest request(String response) {
        return new AuthWecomIdentityRequest(AuthConfig.builder().clientId("fixture-corp")
                .clientSecret("fixture-secret").agentId("51").redirectUri("https://example.test/login").build(),
                mock(AuthStateCache.class)) {
            @Override protected String doGetUserInfo(AuthToken token) { return response; }
        };
    }
    @Test void userIdSufficesWithoutContactDetailsOrPersonalFields() {
        var user = request("{\"errcode\":0,\"UserId\":\"fixture-user\",\"DeviceId\":\"not-needed\"}")
                .getUserInfo(AuthToken.builder().code("fixture-code").build());
        assertEquals("fixture-user", user.getUuid());
        assertEquals("WECHAT_ENTERPRISE", user.getSource());
        assertEquals("", user.getNickname());
        assertEquals(1, user.getRawUserInfo().size());
    }
    @Test void invalidStateStillRejectsBeforeIdentityLookup() {
        var response = request("{\"errcode\":0,\"UserId\":\"fixture\"}").login(
                me.zhyd.oauth.model.AuthCallback.builder().code("fixture-code").state("unknown-state").build());
        assertFalse(response.ok());
    }
    @Test void rejectsNonMemberAndUpstreamErrors() {
        for (String response : new String[]{"null", "{}", "{\"errcode\":0,\"OpenId\":\"visitor\"}",
                "{\"errcode\":0,\"UserId\":\" \"}", "{\"errcode\":60011,\"UserId\":\"fixture\"}"}) {
            assertThrows(AuthException.class, () -> request(response).getUserInfo(AuthToken.builder().build()));
        }
    }
}
