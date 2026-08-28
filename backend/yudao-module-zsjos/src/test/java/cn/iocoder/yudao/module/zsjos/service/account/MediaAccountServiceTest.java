package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountDetailSnapshotVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaAccountServiceTest {
    @InjectMocks private MediaAccountService service;
    @Mock private MediaAccountMapper mapper;
    @Mock private MediaAccountNumberService numberService;
    @Mock private PermissionApi permissionApi;
    @Mock private PersonMapper personMapper;
    @Mock private AdminUserApi adminUserApi;
    @Mock private MediaAccountFieldConfigService fieldConfigService;
    @Mock private MediaAccountObjectPermissionProvider objectPermissionProvider;
    @Mock private ServiceRelationMapper relationMapper;

    @Test
    void ordinaryCreatorIsBoundAsDirectorAndSnapshotsConfiguredDetails() {
        TenantContextHolder.setTenantId(1L);
        MediaAccountSaveReqVO request = new MediaAccountSaveReqVO();
        request.setStudentPersonId(40L); request.setDirectorUserId(999L);
        request.setPlatformValue("douyin"); request.setPlatformLabelSnapshot("抖音");
        request.setDetailValues(Map.of("uid", "dy-100", "nickname", "中世健课堂"));
        when(personMapper.selectById(40L)).thenReturn(new PersonDO().setId(40L));
        when(permissionApi.hasAnyPermissions(248L, "zsjos:media-account:query-all")).thenReturn(false);
        when(numberService.next()).thenReturn("MA-001");
        MediaAccountDetailSnapshotVO uid = new MediaAccountDetailSnapshotVO();
        uid.setKey("uid"); uid.setLabel("UID"); uid.setValue("dy-100"); uid.setDisplayValue("dy-100");
        when(fieldConfigService.validateAndSnapshot(request.getDetailValues())).thenReturn(
                new MediaAccountFieldConfigService.DetailSnapshot(12L, request.getDetailValues(), List.of(uid)));
        when(mapper.insert(any(MediaAccountDO.class))).thenAnswer(invocation -> {
            invocation.<MediaAccountDO>getArgument(0).setId(9L); return 1;
        });

        assertEquals(9L, service.create(request, 248L));
        verify(mapper).insert(org.mockito.ArgumentMatchers.argThat((MediaAccountDO account) ->
                account.getStudentPersonId().equals(40L)
                        && account.getDirectorUserId().equals(248L)
                        && account.getOwnerOperatorUserId().equals(248L)
                        && account.getPlatformAccountId().equals("dy-100")
                        && account.getNickname().equals("中世健课堂")
                        && account.getDetailConfigVersionId().equals(12L)));
        verify(adminUserApi).validateUser(248L);
    }

    @Test
    void projectsHistoryAccessWhenFeatureAndObjectPermissionsBothPass() {
        MediaAccountDO account = new MediaAccountDO().setId(5L);
        when(objectPermissionProvider.hasPermission(5L, "read", 248L)).thenReturn(true);
        when(permissionApi.hasAnyPermissions(248L,
                "zsjos:media-account:query", "zsjos:media-account:maintenance")).thenReturn(true);

        assertTrue(service.availableActionsForVisible(account, 248L).contains("VIEW_ACCOUNT_HISTORY"));
    }

    @Test
    void omitsHistoryAccessWhenFeatureOrObjectPermissionFails() {
        MediaAccountDO account = new MediaAccountDO().setId(5L);
        when(objectPermissionProvider.hasPermission(5L, "read", 248L)).thenReturn(true);

        assertFalse(service.availableActionsForVisible(account, 248L).contains("VIEW_ACCOUNT_HISTORY"));
        assertFalse(service.availableActionsForVisible(account, 230L).contains("VIEW_ACCOUNT_HISTORY"));
    }
}
