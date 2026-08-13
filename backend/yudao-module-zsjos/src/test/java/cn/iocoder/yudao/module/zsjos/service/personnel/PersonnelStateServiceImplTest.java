package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PersonnelStateUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PersonnelStateDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PersonnelStateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonnelStateServiceImplTest {

    @InjectMocks private PersonnelStateServiceImpl service;
    @Mock private PersonnelStateMapper mapper;
    @Mock private AdminUserApi adminUserApi;

    @Test
    void getDefaultsToEnabledForExistingAccount() {
        when(adminUserApi.getUser(1L)).thenReturn(new AdminUserRespDTO().setId(1L));

        assertEquals(STATE_ENABLED, service.get(1L).getState());
    }

    @Test
    void updateDepartedPersistsStateAndDisablesAccount() {
        when(adminUserApi.getUser(1L)).thenReturn(new AdminUserRespDTO().setId(1L));
        PersonnelStateUpdateReqVO request = request(STATE_DEPARTED, "  left company  ");

        service.update(1L, request, 9L);

        ArgumentCaptor<PersonnelStateDO> stateCaptor = ArgumentCaptor.forClass(PersonnelStateDO.class);
        verify(mapper).insert(stateCaptor.capture());
        PersonnelStateDO state = stateCaptor.getValue();
        assertEquals(1L, state.getSystemUserId());
        assertEquals(STATE_DEPARTED, state.getBusinessState());
        assertEquals("left company", state.getChangeReason());
        assertEquals(9L, state.getChangedByUserId());
        assertNotNull(state.getChangedAt());
        verify(adminUserApi).updateUserStatus(1L, CommonStatusEnum.DISABLE.getStatus(), "  left company  ");
    }

    @Test
    void updateEnabledRestoresAccount() {
        when(adminUserApi.getUser(1L)).thenReturn(new AdminUserRespDTO().setId(1L));
        when(mapper.selectByUserId(1L)).thenReturn(new PersonnelStateDO().setId(10L)
                .setSystemUserId(1L).setBusinessState(STATE_DISABLED));

        service.update(1L, request(STATE_ENABLED, "return"), 9L);

        verify(mapper).updateById(argThat((PersonnelStateDO state) -> STATE_ENABLED.equals(state.getBusinessState())));
        verify(adminUserApi).updateUserStatus(1L, CommonStatusEnum.ENABLE.getStatus(), "return");
    }

    @Test
    void disabledPersonnelIsNotEligible() {
        when(mapper.selectByUserId(1L)).thenReturn(new PersonnelStateDO().setBusinessState(STATE_DISABLED));

        assertFalse(service.isEnabled(1L));
    }

    private PersonnelStateUpdateReqVO request(String state, String reason) {
        PersonnelStateUpdateReqVO request = new PersonnelStateUpdateReqVO();
        request.setState(state);
        request.setReason(reason);
        return request;
    }
}
