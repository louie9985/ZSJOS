package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.system.api.logger.LoginLogApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class PartnerAuthServiceImplTest {

    @InjectMocks private PartnerAuthServiceImpl service;
    @Mock private PartnerAccountService accountService;
    @Mock private PartnerMapper partnerMapper;
    @Mock private OAuth2TokenCommonApi oauth2TokenApi;
    @Mock private LoginLogApi loginLogApi;
    @Mock private ConfigApi configApi;
    @Mock private cn.iocoder.yudao.module.system.api.social.SocialClientApi socialClientApi;
    @Mock private cn.iocoder.yudao.module.system.api.social.SocialUserApi socialUserApi;

    @Test
    void selectsWebOnlyForExplicitInClientRequest() {
        service.getWecomAuthorizeUrl("https://example.test/profile?wecomBind=1", true);
        verify(socialClientApi).getWecomWebAuthorizeUrl(3, "https://example.test/profile?wecomBind=1");
        service.getWecomAuthorizeUrl("https://example.test/login", false);
        verify(socialClientApi).getAuthorizeUrl(30, 3, "https://example.test/login");
    }

    @Test
    void wecomAuthorizationRetainsPartnerAudience() {
        service.getWecomAuthorizeUrl("https://example.test/login");
        verify(socialClientApi).getAuthorizeUrl(30, 3, "https://example.test/login");
    }

    @Test
    void wecomLoginNeverUsesAdminBindingOrTokenIdentity() {
        var request = new cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerWecomLoginReqVO();
        request.setCode("test-code"); request.setState("test-state");
        var social = new cn.iocoder.yudao.module.system.api.social.dto.SocialUserRespDTO();
        social.setUserId(20L);
        when(socialUserApi.getSocialUserByCode(3, 30, "test-code", "test-state")).thenReturn(social);
        when(accountService.requireContext(20L)).thenReturn(new PartnerContext(20L, 70L));
        when(accountService.getById(20L)).thenReturn(new PartnerAccountDO().setId(20L));
        when(oauth2TokenApi.createAccessToken(any())).thenReturn(new OAuth2AccessTokenRespDTO().setUserId(20L));
        service.wecomLogin(request, "127.0.0.1");
        verify(oauth2TokenApi).createAccessToken(argThat(value -> value.getUserType() == 3
                && Integer.valueOf(604800).equals(value.getRefreshTokenValiditySeconds())
                && Long.valueOf(20L).equals(value.getUserId())));
        verify(socialUserApi, never()).getSocialUserByCode(eq(2), any(), any(), any());
    }

    @Test
    void unboundWecomCannotIssueToken() {
        var request = new cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerWecomLoginReqVO();
        request.setCode("test-code"); request.setState("test-state");
        org.junit.jupiter.api.Assertions.assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.wecomLogin(request, "127.0.0.1"));
        verifyNoInteractions(oauth2TokenApi, accountService);
    }

    @Test
    void logoutRevokesOnlyExpectedPartnerTypeAndWritesAudit() {
        OAuth2AccessTokenRespDTO token = new OAuth2AccessTokenRespDTO().setUserId(20L);
        when(oauth2TokenApi.removeAccessToken("access-token", UserTypeEnum.PARTNER.getValue())).thenReturn(token);
        when(accountService.getById(20L)).thenReturn(new PartnerAccountDO().setId(20L).setMobile("13800138000"));

        service.logout("access-token");

        verify(loginLogApi).createLoginLog(any());
        verifyNoMoreInteractions(oauth2TokenApi);
    }

    @Test
    void logoutMissingOrWrongTypeTokenIsIdempotent() {
        when(oauth2TokenApi.removeAccessToken("unknown-token", UserTypeEnum.PARTNER.getValue())).thenReturn(null);

        assertDoesNotThrow(() -> service.logout("unknown-token"));

        verifyNoInteractions(accountService, loginLogApi);
    }

    @Test
    void logoutAuditFailureDoesNotFailAfterRevocation() {
        OAuth2AccessTokenRespDTO token = new OAuth2AccessTokenRespDTO().setUserId(20L);
        when(oauth2TokenApi.removeAccessToken("access-token", UserTypeEnum.PARTNER.getValue())).thenReturn(token);
        when(accountService.getById(20L)).thenThrow(new IllegalStateException("audit unavailable"));

        assertDoesNotThrow(() -> service.logout("access-token"));
    }

    @Test
    void portalPermissionsIncludeStudentPositioningConfirmation() {
        assertTrue(PartnerAuthServiceImpl.PORTAL_PERMISSIONS.contains("zsjos:positioning-card:student-confirm"));
    }
}
