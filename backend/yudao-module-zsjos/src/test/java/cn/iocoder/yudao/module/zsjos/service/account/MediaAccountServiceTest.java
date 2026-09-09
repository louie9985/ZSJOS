package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountDetailSnapshotVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.DICT_ACCOUNT_TYPE;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.DICT_PROFESSION;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MEDIA_ACCOUNT_FIELD_CONFIG_INVALID;
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
    @Mock private DictDataApi dictDataApi;

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
        when(dictDataApi.getDictDataList("zsjos_account_platform"))
                .thenReturn(List.of(dict("zsjos_account_platform", "douyin", "抖音", true)));
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
    void createSnapshotsEnabledRecommendationDictionaryLabels() {
        TenantContextHolder.setTenantId(1L);
        MediaAccountSaveReqVO request = baseCreateRequest();
        request.setAccountTypePrimaryValue("expert");
        request.setTrackPrimaryValue("law");
        when(dictDataApi.getDictDataList("zsjos_account_platform"))
                .thenReturn(List.of(dict("zsjos_account_platform", "douyin", "抖音", true)));
        when(dictDataApi.getDictDataList(DICT_ACCOUNT_TYPE))
                .thenReturn(List.of(dict(DICT_ACCOUNT_TYPE, "expert", "专家型", true)));
        when(dictDataApi.getDictDataList(DICT_PROFESSION))
                .thenReturn(List.of(dict(DICT_PROFESSION, "law", "法学", true)));
        stubCreatePrerequisites(request);

        service.create(request, 248L);

        verify(mapper).insert(org.mockito.ArgumentMatchers.argThat((MediaAccountDO account) ->
                "expert".equals(account.getAccountTypePrimaryValue())
                        && "专家型".equals(account.getAccountTypePrimaryLabelSnapshot())
                        && "law".equals(account.getTrackPrimaryValue())
                        && "法学".equals(account.getTrackPrimaryLabelSnapshot())));
    }

    @Test
    void updateRejectsDisabledRecommendationDictionaryValue() {
        MediaAccountDO account = existingAccount();
        when(mapper.selectById(9L)).thenReturn(account);
        when(dictDataApi.getDictDataList(DICT_ACCOUNT_TYPE))
                .thenReturn(List.of(dict(DICT_ACCOUNT_TYPE, "expert", "专家型", false)));
        MediaAccountUpdateReqVO request = updateRequest();
        request.setAccountTypePrimaryValue("expert");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.update(9L, request, 248L));

        assertEquals(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID.getCode(), error.getCode());
    }

    @Test
    void updateRejectsMissingRecommendationDictionaryValue() {
        MediaAccountDO account = existingAccount();
        when(mapper.selectById(9L)).thenReturn(account);
        when(dictDataApi.getDictDataList(DICT_PROFESSION)).thenReturn(List.of());
        MediaAccountUpdateReqVO request = updateRequest();
        request.setTrackPrimaryValue("missing");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.update(9L, request, 248L));

        assertEquals(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID.getCode(), error.getCode());
    }

    @Test
    void updateClearsRecommendationValueAndLabelPairsForBlankSelections() {
        MediaAccountDO account = existingAccount()
                .setAccountTypePrimaryValue("expert").setAccountTypePrimaryLabelSnapshot("专家型")
                .setAccountTypeSecondaryValue("personal").setAccountTypeSecondaryLabelSnapshot("个人型")
                .setTrackPrimaryValue("law").setTrackPrimaryLabelSnapshot("法学")
                .setTrackSecondaryValue("finance").setTrackSecondaryLabelSnapshot("金融学");
        when(mapper.selectById(9L)).thenReturn(account);
        when(mapper.updateProfile(any(MediaAccountDO.class), org.mockito.ArgumentMatchers.eq(3))).thenReturn(1);
        MediaAccountUpdateReqVO request = updateRequest();
        request.setAccountTypePrimaryValue(" ");
        request.setAccountTypeSecondaryValue("");
        request.setTrackPrimaryValue("  ");
        request.setTrackSecondaryValue("");

        service.update(9L, request, 248L);

        ArgumentCaptor<MediaAccountDO> captor = ArgumentCaptor.forClass(MediaAccountDO.class);
        verify(mapper).updateProfile(captor.capture(), org.mockito.ArgumentMatchers.eq(3));
        MediaAccountDO updated = captor.getValue();
        assertNull(updated.getAccountTypePrimaryValue());
        assertNull(updated.getAccountTypePrimaryLabelSnapshot());
        assertNull(updated.getAccountTypeSecondaryValue());
        assertNull(updated.getAccountTypeSecondaryLabelSnapshot());
        assertNull(updated.getTrackPrimaryValue());
        assertNull(updated.getTrackPrimaryLabelSnapshot());
        assertNull(updated.getTrackSecondaryValue());
        assertNull(updated.getTrackSecondaryLabelSnapshot());
    }

    @Test
    void updateKeepsHistoricalRecommendationLabelWhenSelectionIsUnchanged() {
        MediaAccountDO account = existingAccount()
                .setAccountTypePrimaryValue("expert").setAccountTypePrimaryLabelSnapshot("原专家标签");
        when(mapper.selectById(9L)).thenReturn(account);
        when(mapper.updateProfile(any(MediaAccountDO.class), org.mockito.ArgumentMatchers.eq(3))).thenReturn(1);

        service.update(9L, updateRequest(), 248L);

        verify(mapper).updateProfile(org.mockito.ArgumentMatchers.argThat((MediaAccountDO updated) ->
                "expert".equals(updated.getAccountTypePrimaryValue())
                        && "原专家标签".equals(updated.getAccountTypePrimaryLabelSnapshot())),
                org.mockito.ArgumentMatchers.eq(3));
        org.mockito.Mockito.verifyNoInteractions(dictDataApi);
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

    private MediaAccountSaveReqVO baseCreateRequest() {
        MediaAccountSaveReqVO request = new MediaAccountSaveReqVO();
        request.setStudentPersonId(40L);
        request.setPlatformValue("douyin");
        request.setPlatformLabelSnapshot("客户端标签不可信");
        request.setDetailValues(Map.of("uid", "dy-100", "nickname", "中世健课堂"));
        return request;
    }

    private void stubCreatePrerequisites(MediaAccountSaveReqVO request) {
        when(personMapper.selectById(40L)).thenReturn(new PersonDO().setId(40L));
        when(permissionApi.hasAnyPermissions(248L, "zsjos:media-account:query-all")).thenReturn(false);
        when(numberService.next()).thenReturn("MA-001");
        when(fieldConfigService.validateAndSnapshot(request.getDetailValues())).thenReturn(
                new MediaAccountFieldConfigService.DetailSnapshot(12L, request.getDetailValues(), List.of()));
        when(mapper.insert(any(MediaAccountDO.class))).thenAnswer(invocation -> {
            invocation.<MediaAccountDO>getArgument(0).setId(9L);
            return 1;
        });
    }

    private MediaAccountDO existingAccount() {
        return new MediaAccountDO().setId(9L).setVersion(3).setDirectorUserId(248L);
    }

    private MediaAccountUpdateReqVO updateRequest() {
        MediaAccountUpdateReqVO request = new MediaAccountUpdateReqVO();
        request.setVersion(3);
        return request;
    }

    private DictDataRespDTO dict(String type, String value, String label, boolean enabled) {
        DictDataRespDTO item = new DictDataRespDTO();
        item.setDictType(type);
        item.setValue(value);
        item.setLabel(label);
        item.setStatus(enabled ? CommonStatusEnum.ENABLE.getStatus() : CommonStatusEnum.DISABLE.getStatus());
        return item;
    }
}
