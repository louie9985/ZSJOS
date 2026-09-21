package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.module.system.api.social.SocialUserApi;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerProfileUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerProfileServiceImplTest {
    @InjectMocks private PartnerProfileServiceImpl service;
    @Mock private PartnerAccountService accountService;
    @Mock private PartnerMapper partnerMapper;
    @Mock private SocialUserApi socialUserApi;

    @Test void bindingUsesPartnerSubjectWithoutEnablingPushAutomatically() {
        var request = new cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerWecomBindReqVO();
        request.setCode("test-code"); request.setState("test-state");
        service.bindWecom(8L, request);
        verify(accountService).requireContext(8L);
        verify(socialUserApi).bindSocialUser(argThat(value -> value.getUserType() == 3
                && Long.valueOf(8L).equals(value.getUserId()) && value.getSocialType() == 30));
        verify(accountService, never()).updateWecomEnabled(anyLong(), anyBoolean());
    }

    @Test void unboundPartnerCannotEnablePush() {
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.updateNotifyChannel(8L, true));
        verify(socialUserApi).getSocialUserByUserId(3, 8L, 30);
        verify(accountService, never()).updateWecomEnabled(anyLong(), anyBoolean());
    }

    @Test void boundPartnerCanExplicitlyEnablePush() {
        when(socialUserApi.getSocialUserByUserId(3, 8L, 30))
                .thenReturn(new cn.iocoder.yudao.module.system.api.social.dto.SocialUserRespDTO());
        service.updateNotifyChannel(8L, true);
        verify(accountService).updateWecomEnabled(8L, true);
    }

    @Test void readsIndependentNameAndNullableNicknameFromAuthenticatedSubject() {
        when(accountService.requireContext(8L)).thenReturn(new PartnerContext(8L, 21L));
        when(partnerMapper.selectById(21L)).thenReturn(new PartnerDO().setName("真实姓名"));
        when(accountService.getByPartnerId(21L)).thenReturn(new PartnerAccountDO());
        var profile = service.get(8L);
        assertEquals("真实姓名", profile.getName());
        assertNull(profile.getNickname());
    }

    @Test void nicknameUpdateNeverReplacesNameAndUsesAuthenticatedPartnerId() {
        when(accountService.requireContext(8L)).thenReturn(new PartnerContext(8L, 21L));
        var request = new PartnerProfileUpdateReqVO();
        request.setName(" 张三 "); request.setNickname(" 星光 ");
        service.update(8L, request);
        verify(partnerMapper).updateById(argThat((PartnerDO row) -> row.getId().equals(21L)
                && "张三".equals(row.getName()) && "星光".equals(row.getNickname())));
    }

    @Test void invalidAccountCannotWriteAnotherPartnerProfile() {
        when(accountService.requireContext(8L)).thenThrow(new IllegalStateException("disabled"));
        assertThrows(IllegalStateException.class, () -> service.update(8L, new PartnerProfileUpdateReqVO()));
        verifyNoInteractions(partnerMapper);
    }
}
