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
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioninginterview.PositioningInterviewMapper;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.DICT_PERSONA_TYPE;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.DICT_PROFESSION;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
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
    @Mock private PositioningInterviewMapper positioningInterviewMapper;
    @Mock private DictDataApi dictDataApi;
    @Mock private cn.iocoder.yudao.module.zsjos.service.delivery.StudentDeliveryPlanService studentDeliveryPlanService;

    @Test
    void directorCreatesEmptyAccountWithOwnersFromSelectedRelation() {
        TenantContextHolder.setTenantId(1L);
        MediaAccountSaveReqVO request = baseCreateRequest();
        stubCreatePrerequisites(request);
        assertEquals(9L, service.create(request, 248L));
        ArgumentCaptor<MediaAccountDO> saved = ArgumentCaptor.forClass(MediaAccountDO.class);
        verify(mapper).insert(saved.capture());
        assertEquals("MA-001", saved.getValue().getAccountNo());
        assertEquals(40L, saved.getValue().getStudentPersonId());
        assertEquals(248L, saved.getValue().getDirectorUserId());
        assertEquals(248L, saved.getValue().getOwnerOperatorUserId());
        assertNull(saved.getValue().getNickname());
        assertNull(saved.getValue().getPlatformValue());
        assertNull(saved.getValue().getSStage());
        assertEquals("{}", saved.getValue().getDetailValuesJson());
    }

    @Test
    void unrelatedOperatorCannotCreateEvenIfClientClaimsDirector() {
        TenantContextHolder.setTenantId(1L);
        var request = baseCreateRequest(); request.setDirectorUserId(248L);
        when(relationMapper.selectByIdForUpdate(30L, 1L)).thenReturn(createRelation());
        assertEquals(MEDIA_ACCOUNT_SERVICE_INVALID.getCode(), assertThrows(ServiceException.class,
                () -> service.create(request, 600L)).getCode());
        verify(mapper, never()).insert(any(MediaAccountDO.class));
    }

    @Test
    void assignedOperatorCreatesWithRelationDirectorAndSeparateCreator() {
        TenantContextHolder.setTenantId(1L);
        var request = baseCreateRequest();
        request.setDirectorUserId(248L);
        stubCreatePrerequisites(request);
        when(relationMapper.selectByIdForUpdate(30L, 1L)).thenReturn(createRelation().setOperatorUserId(600L));
        assertEquals(9L, service.create(request, 600L));
        ArgumentCaptor<MediaAccountDO> saved = ArgumentCaptor.forClass(MediaAccountDO.class);
        verify(mapper).insert(saved.capture());
        assertEquals(248L, saved.getValue().getDirectorUserId());
        assertEquals(600L, saved.getValue().getOwnerOperatorUserId());
        assertEquals(600L, saved.getValue().getCreateOperatorUserId());
        assertEquals(30L, saved.getValue().getCreateServiceRelationId());
        verify(relationMapper, never()).selectActiveAcceptedByPersonForUpdate(any(), any());
    }

    @Test
    void createRejectsPrepopulatedBusinessFields() {
        TenantContextHolder.setTenantId(1L);
        var request = baseCreateRequest(); request.setNickname("unexpected");
        when(relationMapper.selectByIdForUpdate(30L, 1L)).thenReturn(createRelation());
        assertEquals(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID.getCode(), assertThrows(ServiceException.class,
                () -> service.create(request, 248L)).getCode());
        verify(mapper, never()).insert(any(MediaAccountDO.class));
    }

    @Test
    void createRejectsIncompletePositioningInterview() {
        TenantContextHolder.setTenantId(1L);
        MediaAccountSaveReqVO request = baseCreateRequest();
        ServiceRelationDO relation = createRelation().setDirectorStage("positioning_interview");
        when(relationMapper.selectByIdForUpdate(30L, 1L)).thenReturn(relation);

        ServiceException error = assertThrows(ServiceException.class, () -> service.create(request, 248L));

        assertEquals(MEDIA_ACCOUNT_POSITIONING_INCOMPLETE.getCode(), error.getCode());
        verify(mapper, never()).insert(any(MediaAccountDO.class));
    }

    @Test
    void createRejectsRelationVersionConflict() {
        TenantContextHolder.setTenantId(1L);
        MediaAccountSaveReqVO request = baseCreateRequest();
        request.setVersion(2);
        when(relationMapper.selectByIdForUpdate(30L, 1L)).thenReturn(createRelation());

        ServiceException error = assertThrows(ServiceException.class, () -> service.create(request, 248L));

        assertEquals(STUDENT_SERVICE_VERSION_CONFLICT.getCode(), error.getCode());
        verify(mapper, never()).insert(any(MediaAccountDO.class));
    }

    @Test
    void exactCreateReplayReturnsOriginalAccountWithoutDuplicate() {
        TenantContextHolder.setTenantId(1L);
        MediaAccountSaveReqVO request = baseCreateRequest();
        AtomicReference<MediaAccountDO> inserted = new AtomicReference<>();
        when(relationMapper.selectByIdForUpdate(30L, 1L)).thenReturn(createRelation());
        when(mapper.selectByCreateIdempotencyKey("account-create-key"))
                .thenAnswer(invocation -> inserted.get());
        when(personMapper.selectById(40L)).thenReturn(new PersonDO().setId(40L));
        when(numberService.next()).thenReturn("MA-001");
        when(fieldConfigService.validateAndSnapshot(request.getDetailValues())).thenReturn(
                new MediaAccountFieldConfigService.DetailSnapshot(12L, request.getDetailValues(), List.of()));
        when(mapper.insert(any(MediaAccountDO.class))).thenAnswer(invocation -> {
            MediaAccountDO account = invocation.getArgument(0);
            account.setId(9L);
            inserted.set(account);
            return 1;
        });

        assertEquals(9L, service.create(request, 248L));
        assertEquals(9L, service.create(request, 248L));

        verify(mapper, org.mockito.Mockito.times(1)).insert(any(MediaAccountDO.class));
    }

    @Test
    void reusedCreateKeyWithDifferentPayloadIsRejected() {
        TenantContextHolder.setTenantId(1L);
        MediaAccountSaveReqVO request = baseCreateRequest();
        when(relationMapper.selectByIdForUpdate(30L, 1L)).thenReturn(createRelation());
        when(mapper.selectByCreateIdempotencyKey("account-create-key")).thenReturn(new MediaAccountDO()
                .setId(9L).setStudentPersonId(40L).setCreateServiceRelationId(30L)
                .setCreateOperatorUserId(248L).setCreateIdempotencyKey("account-create-key")
                .setCreateRequestFingerprint("different"));

        ServiceException error = assertThrows(ServiceException.class, () -> service.create(request, 248L));

        assertEquals(MEDIA_ACCOUNT_CREATE_IDEMPOTENCY_CONFLICT.getCode(), error.getCode());
        verify(mapper, never()).insert(any(MediaAccountDO.class));
    }

    @Test
    void legacyFullUpdateCannotBypassProfileFieldPolicy() {
        assertEquals(MEDIA_ACCOUNT_PROFILE_UPGRADE_REQUIRED.getCode(), assertThrows(ServiceException.class,
                () -> service.update(9L, updateRequest(), 248L)).getCode());
        verify(mapper, never()).updateById(any(MediaAccountDO.class));
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
        request.setStudentPersonId(40L); request.setServiceRelationId(30L); request.setVersion(3);
        request.setIdempotencyKey("account-create-key");
        request.setDetailValues(Map.of());
        return request;
    }

    private void stubCreatePrerequisites(MediaAccountSaveReqVO request) {
        when(relationMapper.selectByIdForUpdate(30L, 1L)).thenReturn(createRelation());
        when(personMapper.selectById(40L)).thenReturn(new PersonDO().setId(40L));
        when(numberService.next()).thenReturn("MA-001");
        when(fieldConfigService.validateAndSnapshot(request.getDetailValues())).thenReturn(
                new MediaAccountFieldConfigService.DetailSnapshot(12L, request.getDetailValues(), List.of()));
        when(mapper.insert(any(MediaAccountDO.class))).thenAnswer(invocation -> {
            invocation.<MediaAccountDO>getArgument(0).setId(9L);
            return 1;
        });
    }

    private ServiceRelationDO createRelation() {
        return new ServiceRelationDO().setId(30L).setPersonId(40L).setContentDirectorUserId(248L)
                .setOperatorUserId(248L).setStatus("active").setAcceptanceStatus("accepted")
                .setDirectorStage("positioning_interview_completed").setVersion(3);
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
