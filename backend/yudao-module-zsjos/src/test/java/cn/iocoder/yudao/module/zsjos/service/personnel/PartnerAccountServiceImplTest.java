package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerAccountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_STATUS_ENABLED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_ACCOUNT_CONCURRENT_MODIFICATION;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerAccountServiceImplTest {

    @InjectMocks private PartnerAccountServiceImpl service;
    @Mock private PartnerAccountMapper accountMapper;
    @Mock private PartnerMapper partnerMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private OAuth2TokenCommonApi oauth2TokenApi;

    @Test
    void createNormalizesMobileBeforePersisting() {
        when(passwordEncoder.encode("pass1234")).thenReturn("encoded");

        service.create(10L, " 13800138000 ", "pass1234");

        verify(accountMapper).selectByMobile("13800138000");
        verify(accountMapper).insert(org.mockito.ArgumentMatchers.<PartnerAccountDO>argThat(account -> account.getPartnerId().equals(10L)
                && account.getMobile().equals("13800138000") && account.getPassword().equals("encoded")));
    }

    @Test
    void authenticateReturnsTypedPartnerContextOnlyForEnabledAccountAndSubject() {
        PartnerAccountDO account = account();
        when(accountMapper.selectByMobile("13800138000")).thenReturn(account);
        when(accountMapper.selectById(20L)).thenReturn(account);
        when(passwordEncoder.matches("pass1234", "encoded")).thenReturn(true);
        when(partnerMapper.selectById(10L)).thenReturn(new PartnerDO().setId(10L).setStatus(PARTNER_STATUS_ENABLED));
        when(accountMapper.updateById(any(PartnerAccountDO.class))).thenReturn(1);

        PartnerAccountDO result = service.authenticate(" 13800138000 ", "pass1234", "127.0.0.1");

        assertEquals(20L, result.getId());
        verify(accountMapper, never()).updateById(any(PartnerAccountDO.class));
    }

    @Test
    void disablingAccountRevokesOnlyPartnerTokens() {
        when(accountMapper.selectByPartnerId(10L)).thenReturn(account());
        when(accountMapper.updateById(any(PartnerAccountDO.class))).thenReturn(1);

        service.setEnabled(10L, false);

        verify(accountMapper).updateById(org.mockito.ArgumentMatchers.<PartnerAccountDO>argThat(value ->
                CommonStatusEnum.DISABLE.getStatus().equals(value.getStatus())));
        verify(oauth2TokenApi).removeAccessToken(20L, UserTypeEnum.PARTNER.getValue());
        verifyNoMoreInteractions(oauth2TokenApi);
    }

    @Test
    void recordLoginConflictDoesNotUpdateAccount() {
        PartnerAccountDO account = account();
        when(accountMapper.selectById(20L)).thenReturn(account);
        when(accountMapper.updateById(any(PartnerAccountDO.class))).thenReturn(0);

        assertServiceException(() -> service.recordLogin(20L, "127.0.0.1"),
                PARTNER_ACCOUNT_CONCURRENT_MODIFICATION);
    }

    @Test
    void disablingConflictDoesNotRevokeTokens() {
        when(accountMapper.selectByPartnerId(10L)).thenReturn(account());
        when(accountMapper.updateById(any(PartnerAccountDO.class))).thenReturn(0);

        assertServiceException(() -> service.setEnabled(10L, false), PARTNER_ACCOUNT_CONCURRENT_MODIFICATION);

        verifyNoInteractions(oauth2TokenApi);
    }

    @Test
    void mobileConflictDoesNotRevokeTokens() {
        when(accountMapper.selectByPartnerId(10L)).thenReturn(account());
        when(accountMapper.updateById(any(PartnerAccountDO.class))).thenReturn(0);

        assertServiceException(() -> service.updateMobile(10L, "13900139000"),
                PARTNER_ACCOUNT_CONCURRENT_MODIFICATION);

        verifyNoInteractions(oauth2TokenApi);
    }

    @Test
    void passwordConflictDoesNotRevokeTokens() {
        when(accountMapper.selectByPartnerId(10L)).thenReturn(account());
        when(passwordEncoder.encode("new-pass")).thenReturn("new-encoded");
        when(accountMapper.updateById(any(PartnerAccountDO.class))).thenReturn(0);

        assertServiceException(() -> service.resetPassword(10L, "new-pass"),
                PARTNER_ACCOUNT_CONCURRENT_MODIFICATION);

        verifyNoInteractions(oauth2TokenApi);
    }

    @Test
    void enabledMobileRequiresBothAccountAndPartnerToBeEnabled() {
        PartnerAccountDO account = account();
        when(accountMapper.selectById(20L)).thenReturn(account);
        when(partnerMapper.selectById(10L)).thenReturn(new PartnerDO().setId(10L).setStatus(PARTNER_STATUS_ENABLED));

        assertEquals("13800138000", service.getEnabledMobile(20L));
    }

    private PartnerAccountDO account() {
        return new PartnerAccountDO().setId(20L).setPartnerId(10L).setMobile("13800138000")
                .setPassword("encoded").setStatus(CommonStatusEnum.ENABLE.getStatus()).setVersion(0);
    }
}
