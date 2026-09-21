package cn.iocoder.yudao.module.system.framework.justauth.core;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import me.zhyd.oauth.cache.AuthStateCache;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.exception.AuthException;
import me.zhyd.oauth.model.AuthToken;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthWeChatEnterpriseQrcodeRequest;

/** Partner identity verification needs UserId, not access to the corporate address book. */
public class AuthWecomIdentityRequest extends AuthWeChatEnterpriseQrcodeRequest {
    public AuthWecomIdentityRequest(AuthConfig config, AuthStateCache cache) {
        super(config, cache);
    }

    @Override
    public AuthUser getUserInfo(AuthToken token) {
        JSONObject identity = JSON.parseObject(doGetUserInfo(token));
        if (identity == null || !identity.containsKey("errcode")) {
            throw new AuthException("企业微信身份接口响应不完整");
        }
        if (identity.getIntValue("errcode") != 0) {
            throw new AuthException("企业微信身份核验失败（" + identity.getIntValue("errcode")
                    + "），请检查应用凭据及成员可见范围");
        }
        String userId = identity.getString("UserId");
        if (userId == null || userId.isBlank()) {
            throw new AuthException("未获取到企业成员身份，请使用应用可见范围内的企业微信成员账号");
        }
        // Do not persist optional device identifiers or use a technical UserId as a display name.
        JSONObject minimalIdentity = new JSONObject();
        minimalIdentity.put("UserId", userId);
        return AuthUser.builder().uuid(userId).username("").nickname("")
                .source("WECHAT_ENTERPRISE").token(token).rawUserInfo(minimalIdentity).build();
    }
}
