package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCreateReqDTO;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.system.api.logger.LoginLogApi;
import cn.iocoder.yudao.module.system.api.logger.dto.LoginLogCreateReqDTO;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2ClientConstants;
import cn.iocoder.yudao.module.system.enums.logger.LoginLogTypeEnum;
import cn.iocoder.yudao.module.system.enums.logger.LoginResultEnum;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerLoginReqVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerLoginRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerPermissionInfoRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.monitor.TracerUtils.getTraceId;
import static cn.iocoder.yudao.framework.common.util.servlet.ServletUtils.getUserAgent;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_TOKEN_TYPE_INVALID;

@Service
public class PartnerAuthServiceImpl implements PartnerAuthService {

    public static final Set<String> PORTAL_PERMISSIONS = Set.of(
            "zsjos:partner:self-query", "zsjos:lead:submit", "zsjos:lead:query-submitted",
            "zsjos:lead:submitter-supplement", "zsjos:lead:urge", "zsjos:lead-complaint:create",
            "zsjos:lead:appeal:create", "zsjos:cashback:my-query", "zsjos:withdrawal:my-query",
            "zsjos:withdrawal:apply");

    @Resource private PartnerAccountService accountService;
    @Resource private PartnerMapper partnerMapper;
    @Resource private OAuth2TokenCommonApi oauth2TokenApi;
    @Resource private LoginLogApi loginLogApi;
    @Resource private ConfigApi configApi;

    @Override
    public PartnerLoginRespVO login(PartnerLoginReqVO reqVO, String loginIp) {
        PartnerAccountDO account;
        try {
            account = accountService.authenticate(reqVO.getMobile(), reqVO.getPassword(), loginIp);
        } catch (RuntimeException ex) {
            writeLog(null, reqVO.getMobile(), LoginResultEnum.BAD_CREDENTIALS, loginIp,
                    LoginLogTypeEnum.LOGIN_MOBILE);
            throw ex;
        }
        String clientId = resolveClientId(reqVO.getPlatform());
        OAuth2AccessTokenCreateReqDTO create = new OAuth2AccessTokenCreateReqDTO().setUserId(account.getId())
                .setUserType(UserTypeEnum.PARTNER.getValue()).setClientId(clientId)
                .setMaxDevices(readPositive(OAuth2ClientConstants.CONFIG_MOBILE_MAX_DEVICES,
                        OAuth2ClientConstants.DEFAULT_MAX_DEVICES, OAuth2ClientConstants.MAX_DEVICE_LIMIT))
                .setRefreshTokenValiditySeconds(readPositive(OAuth2ClientConstants.CONFIG_REMEMBER_DAYS,
                        OAuth2ClientConstants.DEFAULT_REMEMBER_DAYS, OAuth2ClientConstants.MAX_REMEMBER_DAYS)
                        * 24 * 60 * 60);
        OAuth2AccessTokenRespDTO token = oauth2TokenApi.createAccessToken(create);
        writeLog(account.getId(), account.getMobile(), LoginResultEnum.SUCCESS, loginIp,
                LoginLogTypeEnum.LOGIN_MOBILE);
        return BeanUtils.toBean(token, PartnerLoginRespVO.class).setClientId(clientId);
    }

    @Override
    public PartnerLoginRespVO refresh(String refreshToken, String clientId) {
        String resolvedClientId = ObjectUtil.defaultIfNull(clientId, OAuth2ClientConstants.CLIENT_ID_ZSJOS_MOBILE);
        OAuth2AccessTokenRespDTO token = oauth2TokenApi.refreshAccessToken(refreshToken, resolvedClientId);
        if (!UserTypeEnum.PARTNER.getValue().equals(token.getUserType())) {
            oauth2TokenApi.removeAccessToken(token.getAccessToken());
            throw exception(PARTNER_TOKEN_TYPE_INVALID);
        }
        try {
            accountService.requireContext(token.getUserId());
        } catch (RuntimeException ex) {
            oauth2TokenApi.removeAccessToken(token.getAccessToken());
            throw ex;
        }
        return BeanUtils.toBean(token, PartnerLoginRespVO.class).setClientId(resolvedClientId);
    }

    @Override
    public void logout(String accessToken) {
        OAuth2AccessTokenRespDTO token = oauth2TokenApi.removeAccessToken(accessToken, UserTypeEnum.PARTNER.getValue());
        if (token == null) return;
        try {
            PartnerAccountDO account = accountService.getById(token.getUserId());
            writeLog(token.getUserId(), account == null ? null : account.getMobile(), LoginResultEnum.SUCCESS,
                    null, LoginLogTypeEnum.LOGOUT_SELF);
        } catch (RuntimeException ignored) {
            // 令牌撤销是退出的安全边界，审计基础资料缺失或写入失败不能恢复已撤销的会话。
        }
    }

    @Override
    public PartnerPermissionInfoRespVO getPermissionInfo(Long accountId) {
        PartnerContext context = accountService.requireContext(accountId);
        PartnerDO partner = partnerMapper.selectById(context.partnerId());
        PartnerPermissionInfoRespVO result = new PartnerPermissionInfoRespVO();
        result.setUser(new PartnerPermissionInfoRespVO.User(accountId, partner.getName(), partner.getAvatar()));
        result.setRoles(Set.of("partner"));
        result.setPermissions(PORTAL_PERMISSIONS);
        return result;
    }

    private String resolveClientId(String platform) {
        if (platform == null || "MOBILE".equalsIgnoreCase(platform)) return OAuth2ClientConstants.CLIENT_ID_ZSJOS_MOBILE;
        if ("PC".equalsIgnoreCase(platform)) return OAuth2ClientConstants.CLIENT_ID_ZSJOS_PC;
        throw exception(PARTNER_TOKEN_TYPE_INVALID);
    }

    private int readPositive(String key, int fallback, int max) {
        try {
            int value = Integer.parseInt(configApi.getConfigValueByKey(key));
            return value >= 1 && value <= max ? value : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void writeLog(Long userId, String mobile, LoginResultEnum result, String ip, LoginLogTypeEnum type) {
        loginLogApi.createLoginLog(new LoginLogCreateReqDTO().setLogType(type.getType()).setTraceId(getTraceId())
                .setUserId(userId).setUserType(UserTypeEnum.PARTNER.getValue()).setUsername(mobile)
                .setResult(result.getResult()).setUserIp(ip == null ? "0.0.0.0" : ip).setUserAgent(getUserAgent()));
    }
}
