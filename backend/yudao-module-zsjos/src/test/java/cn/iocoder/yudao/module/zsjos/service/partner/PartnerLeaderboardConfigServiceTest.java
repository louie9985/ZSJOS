package cn.iocoder.yudao.module.zsjos.service.partner;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.partner.PartnerLeaderboardConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.partner.PartnerLeaderboardConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerLeaderboardConfigServiceTest {
    @InjectMocks private PartnerLeaderboardConfigService service;
    @Mock private PartnerLeaderboardConfigMapper mapper;
    @Mock private RoleApi roleApi;
    @Mock private PermissionApi permissionApi;

    @Test
    void missingConfigReturnsSafeDefaultsWithoutWriting() {
        var config = service.get();
        assertTrue(config.getEnabled());
        assertFalse(config.getIncludeEmployeeSubmitter());
        assertEquals(List.of(), PartnerLeaderboardConfigService.splitCodes(config.getEmployeeRoleCodes()));
        assertEquals(PartnerLeaderboardConfigService.TYPES,
                PartnerLeaderboardConfigService.splitCodes(config.getEnabledTypes()));
        assertEquals("estimated_income", config.getDefaultType());
        assertEquals("month", config.getDefaultPeriod());
        verify(mapper).selectCurrent();
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void employeeSwitchOffDoesNotResolveRoles() {
        var config = new PartnerLeaderboardConfigDO().setIncludeEmployeeSubmitter(false)
                .setEmployeeRoleCodes("sales");
        assertEquals(Set.of(), service.eligibleEmployeeIds(config));
        verifyNoInteractions(roleApi, permissionApi);
    }

    @Test
    void enabledEmployeesComeOnlyFromConfiguredEnabledSystemRoles() {
        var config = new PartnerLeaderboardConfigDO().setIncludeEmployeeSubmitter(true)
                .setEmployeeRoleCodes("sales,disabled,removed");
        var sales = new RoleRespDTO(); sales.setId(7L); sales.setStatus(0);
        var disabled = new RoleRespDTO(); disabled.setId(8L); disabled.setStatus(1);
        when(roleApi.getRoleByCode("sales")).thenReturn(sales);
        when(roleApi.getRoleByCode("disabled")).thenReturn(disabled);
        when(permissionApi.getUserRoleIdListByRoleIds(List.of(7L))).thenReturn(Set.of(42L));
        assertEquals(Set.of(42L), service.eligibleEmployeeIds(config));
        verify(permissionApi).getUserRoleIdListByRoleIds(List.of(7L));
    }

    @Test
    void emptyAndLegacyEmptyRolesNeverMatchEmployees() {
        for (String codes : new String[]{null, "", "[]"}) {
            assertTrue(service.eligibleEmployeeIds(new PartnerLeaderboardConfigDO()
                    .setIncludeEmployeeSubmitter(true).setEmployeeRoleCodes(codes)).isEmpty());
        }
        verifyNoInteractions(roleApi, permissionApi);
    }

    @Test
    void saveRejectsEmptyOrMismatchedDefaults() {
        var empty = new PartnerLeaderboardConfigDO().setEnabledTypes("").setDefaultType("estimated_income").setDefaultPeriod("month");
        assertThrows(IllegalArgumentException.class, () -> service.save(empty));
        var mismatch = new PartnerLeaderboardConfigDO().setEnabledTypes("lead_count").setDefaultType("estimated_income").setDefaultPeriod("month");
        assertThrows(IllegalArgumentException.class, () -> service.save(mismatch));
        var badPeriod = new PartnerLeaderboardConfigDO().setEnabledTypes("lead_count").setDefaultType("lead_count").setDefaultPeriod("year");
        assertThrows(IllegalArgumentException.class, () -> service.save(badPeriod));
        verifyNoInteractions(mapper);
    }
}
