package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.util.AssertUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerInvitationCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerStudentInvitationCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerActivateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerInvitationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOwnershipDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOwnershipLogDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerInvitationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerOwnershipLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerOwnershipMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerInvitationRespVO;
import java.time.ZoneId;
import static org.mockito.Mockito.verifyNoInteractions;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_INVITATION_EXPIRY_INVALID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_INVITATION_SCENE_STUDENT;
import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_INVITATION_STATUS_ACTIVE;
import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_INVITATION_STATUS_USED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_INVITATION_EXPIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartnerInvitationServiceImplTest {

    @InjectMocks private PartnerInvitationServiceImpl service;
    @Mock private PartnerInvitationMapper invitationMapper;
    @Mock private PartnerMapper partnerMapper;
    @Mock private PartnerOwnershipMapper ownershipMapper;
    @Mock private PartnerOwnershipLogMapper ownershipLogMapper;
    @Mock private PartnerAccountService partnerAccountService;
    @Mock private PartnerStudentLinkService partnerStudentLinkService;
    @Mock private PersonMapper personMapper;
    @Mock private ServiceRelationMapper serviceRelationMapper;
    @Mock private RoleApi roleApi;
    @Mock private PermissionApi permissionApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private PlatformTransactionManager transactionManager;

    @Test
    void createVoidsOldActiveInvitationAndReturnsGeneratedCode() {
        mockOperator();
        PartnerInvitationCreateReqVO reqVO = new PartnerInvitationCreateReqVO()
                .setName("张三").setMobile(" 13800138000 ").setAssignedOperatorUserId(9L);

        var result = service.create(reqVO, 1L);

        assertTrue(result.getInviteCode().matches("^[A-Z]{4}\\d{4}$"));
        assertEquals("张三", result.getName());
        verify(invitationMapper).voidActiveByMobile(org.mockito.ArgumentMatchers.eq("13800138000"), any());
        verify(invitationMapper).insert(org.mockito.ArgumentMatchers.<PartnerInvitationDO>argThat(row ->
                "13800138000".equals(row.getMobile())
                        && PARTNER_INVITATION_STATUS_ACTIVE.equals(row.getStatus())
                        && row.getExpiresAt().isAfter(LocalDateTime.now().plusDays(6))));
    }

    @Test
    void activateCreatesPartnerAccountOwnershipAndConsumesInvitation() {
        stubTransaction();
        mockOperator();
        PartnerInvitationDO invitation = invitation();
        when(invitationMapper.selectActiveByMobileAndCodeForUpdate("13800138000", "ABCD1234"))
                .thenReturn(invitation);
        when(partnerAccountService.create(any(), org.mockito.ArgumentMatchers.eq("13800138000"),
                org.mockito.ArgumentMatchers.eq("Password123"))).thenReturn(account());
        when(invitationMapper.updateById(any(PartnerInvitationDO.class))).thenReturn(1);

        PartnerAccountDO account = service.activate(new PartnerActivateReqVO()
                .setMobile("13800138000").setPassword("Password123")
                .setConfirmPassword("Password123").setInviteCode("abcd1234"));

        assertEquals(20L, account.getId());
        verify(partnerMapper).insert(org.mockito.ArgumentMatchers.<PartnerDO>argThat(row ->
                row.getPartnerNo().matches("^PT\\d{18}$") && "张三".equals(row.getName())));
        verify(ownershipMapper).insert(any(PartnerOwnershipDO.class));
        verify(ownershipLogMapper).insert(any(PartnerOwnershipLogDO.class));
        verify(invitationMapper).updateById(org.mockito.ArgumentMatchers.<PartnerInvitationDO>argThat(row ->
                PARTNER_INVITATION_STATUS_USED.equals(row.getStatus()) && row.getUsedAt() != null));
    }

    @Test
    void createStudentInvitationKeepsOriginalSnapshotAndEditableRegistrationProfile() {
        mockOperator();
        PersonDO student = new PersonDO().setId(88L).setName("学员原姓名").setMobile("13900000000");
        ServiceRelationDO relation = new ServiceRelationDO().setId(66L).setPersonId(88L)
                .setContentDirectorUserId(7L).setOperatorUserId(9L).setOrderId(55L);
        when(personMapper.selectById(88L)).thenReturn(student);
        when(serviceRelationMapper.selectActiveByContentDirectorAndPerson(7L, 88L)).thenReturn(List.of(relation));

        var result = service.createStudentInvitation(new PartnerStudentInvitationCreateReqVO()
                .setStudentPersonId(88L).setAssignedOperatorUserId(9L).setName("兼职注册名").setMobile(" 13800138000 "), 7L);

        assertEquals(PARTNER_INVITATION_SCENE_STUDENT, result.getInvitationScene());
        assertEquals("学员原姓名", result.getStudentNameSnapshot());
        assertEquals("13900000000", result.getStudentMobileSnapshot());
        assertEquals("兼职注册名", result.getName());
        assertEquals("13800138000", result.getMobile());
        verify(invitationMapper).insert(org.mockito.ArgumentMatchers.<PartnerInvitationDO>argThat(row ->
                Long.valueOf(9L).equals(row.getAssignedOperatorUserId()) && row.getInitiatedByDirectorUserId().equals(7L)
                        && row.getAssignmentContextJson().contains("66")));
        verify(personMapper, never()).updateById(any(PersonDO.class));
    }

    @Test
    void activateStudentInvitationBindsStudentWithoutCreatingOperatorOwnership() {
        stubTransaction();
        PartnerInvitationDO invitation = invitation()
                .setInvitationScene(PARTNER_INVITATION_SCENE_STUDENT)
                .setStudentPersonId(88L)
                .setStudentNameSnapshot("学员原姓名")
                .setStudentMobileSnapshot("13900000000")
                .setInitiatedByDirectorUserId(7L)
                .setAssignedOperatorUserId(null)
                .setAssignedOperatorNameSnapshot(null)
                .setName("兼职注册名");
        when(invitationMapper.selectActiveByMobileAndCodeForUpdate("13800138000", "ABCD1234"))
                .thenReturn(invitation);
        when(partnerAccountService.create(any(), org.mockito.ArgumentMatchers.eq("13800138000"),
                org.mockito.ArgumentMatchers.eq("Password123"))).thenReturn(account());
        when(invitationMapper.updateById(any(PartnerInvitationDO.class))).thenReturn(1);

        service.activate(new PartnerActivateReqVO().setMobile("13800138000").setPassword("Password123")
                .setConfirmPassword("Password123").setInviteCode("ABCD1234"));

        verify(partnerMapper).insert(org.mockito.ArgumentMatchers.<PartnerDO>argThat(row ->
                "兼职注册名".equals(row.getName()) && "13800138000".equals(row.getMobile())));
        verify(partnerStudentLinkService).bind(any(), org.mockito.ArgumentMatchers.eq(88L),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(7L));
        verify(ownershipMapper, never()).insert(any(PartnerOwnershipDO.class));
        verify(ownershipLogMapper, never()).insert(any(PartnerOwnershipLogDO.class));
        verify(personMapper, never()).updateById(any(PersonDO.class));
    }

    @Test
    void activateExpiredInviteReturnsStableError() {
        stubTransaction();
        PartnerInvitationDO invitation = invitation().setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(invitationMapper.selectActiveByMobileAndCodeForUpdate("13800138000", "ABCD1234"))
                .thenReturn(invitation);
        when(invitationMapper.updateById(invitation)).thenReturn(1);

        AssertUtils.assertServiceException(() -> service.activate(new PartnerActivateReqVO()
                .setMobile("13800138000").setPassword("Password123")
                .setConfirmPassword("Password123").setInviteCode("ABCD1234")), PARTNER_INVITATION_EXPIRED);
        verify(invitationMapper).updateById(org.mockito.ArgumentMatchers.<PartnerInvitationDO>argThat(row ->
                cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_INVITATION_STATUS_EXPIRED
                        .equals(row.getStatus()) && row.getVoidedAt() != null));
        verify(transactionManager).commit(any());
    }

    @Test
    void createPreservesCustomExpiry() {
        mockOperator();
        LocalDateTime expiry = LocalDateTime.now().plusDays(2).withNano(0);
        var result = service.create(new PartnerInvitationCreateReqVO()
                .setName("测试邀请").setMobile("13800138000").setAssignedOperatorUserId(9L)
                .setExpiresAt(expiry), 1L);
        assertEquals(expiry, result.getExpiresAt());
        verify(invitationMapper).insert(org.mockito.ArgumentMatchers.<PartnerInvitationDO>argThat(
                row -> expiry.equals(row.getExpiresAt())));
    }

    @Test
    void createStudentPreservesCustomExpiry() {
        mockOperator();
        mockStudent();
        LocalDateTime expiry = LocalDateTime.now().plusHours(3).withNano(0);
        var result = service.createStudentInvitation(studentRequest().setExpiresAt(expiry), 7L);
        assertEquals(expiry, result.getExpiresAt());
        verify(invitationMapper).insert(org.mockito.ArgumentMatchers.<PartnerInvitationDO>argThat(
                row -> expiry.equals(row.getExpiresAt())));
        verify(invitationMapper).voidActiveByStudent(org.mockito.ArgumentMatchers.eq(88L), any());
        verify(invitationMapper).voidActiveByMobile(org.mockito.ArgumentMatchers.eq("13800138000"), any());
    }

    @Test
    void bothEntrypointsDefaultToSevenDays() {
        mockOperator();
        mockStudent();
        LocalDateTime before = LocalDateTime.now().plusDays(7);
        var general = service.create(new PartnerInvitationCreateReqVO()
                .setName("测试邀请").setMobile("13800138000").setAssignedOperatorUserId(9L), 1L);
        var student = service.createStudentInvitation(studentRequest(), 7L);
        LocalDateTime after = LocalDateTime.now().plusDays(7);
        for (var result : List.of(general, student)) {
            assertTrue(!result.getExpiresAt().isBefore(before));
            assertTrue(!result.getExpiresAt().isAfter(after));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void invalidExpiryDoesNotVoidOrInsertGeneralInvitation(int offsetDays) {
        mockOperator();
        var request = new PartnerInvitationCreateReqVO().setName("测试邀请")
                .setMobile("13800138000").setAssignedOperatorUserId(9L)
                .setExpiresAt(LocalDateTime.now().plusDays(offsetDays));
        AssertUtils.assertServiceException(() -> service.create(request, 1L), PARTNER_INVITATION_EXPIRY_INVALID);
        verifyNoInteractions(invitationMapper);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void invalidExpiryDoesNotVoidOrInsertStudentInvitation(int offsetDays) {
        mockStudent();
        var request = studentRequest().setExpiresAt(LocalDateTime.now().plusDays(offsetDays));
        AssertUtils.assertServiceException(() -> service.createStudentInvitation(request, 7L),
                PARTNER_INVITATION_EXPIRY_INVALID);
        verifyNoInteractions(invitationMapper);
    }

    @Test
    void expiryUsesEpochMillisecondsInBothRequestsAndResponse() {
        long timestamp = 1893456000000L;
        String json = "{\"expiresAt\":" + timestamp + "}";
        var general = JsonUtils.parseObject(json, PartnerInvitationCreateReqVO.class);
        var student = JsonUtils.parseObject(json, PartnerStudentInvitationCreateReqVO.class);
        LocalDateTime expected = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault());
        assertEquals(expected, general.getExpiresAt());
        assertEquals(expected, student.getExpiresAt());
        assertTrue(JsonUtils.toJsonString(new PartnerInvitationRespVO().setExpiresAt(expected))
                .contains("\"expiresAt\":" + timestamp));
    }

    @Test
    void studentContextDefaultsToUniqueOperatorAndReturnsPersistedInvitation() {
        mockStudent();
        when(serviceRelationMapper.selectActiveByContentDirectorAndPerson(7L, 88L))
                .thenReturn(List.of(new ServiceRelationDO().setOperatorUserId(9L)));
        when(invitationMapper.selectLatestByStudent(88L)).thenReturn(invitation());
        var result = service.getStudentContext(88L, 7L);
        assertEquals(9L, result.getDefaultOperatorUserId());
        assertEquals("ABCD1234", result.getInvitation().getInviteCode());
        org.junit.jupiter.api.Assertions.assertFalse(result.isOpened());
    }

    @Test
    void studentContextDoesNotGuessUnassignedOrConflictingOperator() {
        mockStudent();
        org.junit.jupiter.api.Assertions.assertNull(service.getStudentContext(88L, 7L).getDefaultOperatorUserId());
        when(serviceRelationMapper.selectActiveByContentDirectorAndPerson(7L, 88L))
                .thenReturn(List.of(new ServiceRelationDO().setOperatorUserId(9L),
                        new ServiceRelationDO().setOperatorUserId(10L)));
        var result = service.getStudentContext(88L, 7L);
        assertTrue(result.isOperatorAssignmentConflict());
        org.junit.jupiter.api.Assertions.assertNull(result.getDefaultOperatorUserId());
    }

    @Test
    void studentContextHidesInvitationOnceBoundAndProjectsExpiryWithoutWriting() {
        mockStudent();
        when(invitationMapper.selectLatestByStudent(88L))
                .thenReturn(invitation().setExpiresAt(LocalDateTime.now().minusMinutes(1)));
        assertEquals("expired", service.getStudentContext(88L, 7L).getInvitation().getStatus());
        verify(invitationMapper, never()).updateById(any(PartnerInvitationDO.class));
        when(partnerStudentLinkService.hasActiveStudentLink(88L)).thenReturn(true);
        var result = service.getStudentContext(88L, 7L);
        assertTrue(result.isOpened());
        org.junit.jupiter.api.Assertions.assertNull(result.getInvitation());
    }

    @Test
    void unrelatedDirectorCannotReadOrCreateStudentInvitation() {
        when(personMapper.selectById(88L)).thenReturn(new PersonDO().setId(88L));
        AssertUtils.assertServiceException(() -> service.getStudentContext(88L, 7L),
                cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_STUDENT_INVITATION_FORBIDDEN);
        AssertUtils.assertServiceException(() -> service.createStudentInvitation(studentRequest(), 7L),
                cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_STUDENT_INVITATION_FORBIDDEN);
        verifyNoInteractions(invitationMapper);
    }

    @Test
    void boundStudentCannotCreateAnotherInvitation() {
        mockStudent();
        when(partnerStudentLinkService.hasActiveStudentLink(88L)).thenReturn(true);
        AssertUtils.assertServiceException(() -> service.createStudentInvitation(studentRequest(), 7L),
                cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_STUDENT_LINK_CONFLICT);
        verifyNoInteractions(invitationMapper);
    }

    @Test
    void missingOperatorDoesNotInvalidateExistingInvitation() {
        mockStudent();
        AssertUtils.assertServiceException(() -> service.createStudentInvitation(
                studentRequest().setAssignedOperatorUserId(null), 7L),
                cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_INVITATION_OPERATOR_INVALID);
        verifyNoInteractions(invitationMapper);
    }

    @Test
    void selectedOperatorIsIndependentOfStudentOperator() {
        mockStudent();
        mockOperator();
        when(serviceRelationMapper.selectActiveByContentDirectorAndPerson(7L, 88L))
                .thenReturn(List.of(new ServiceRelationDO().setOperatorUserId(10L)));
        var result = service.createStudentInvitation(studentRequest(), 7L);
        assertEquals(9L, result.getAssignedOperatorUserId());
        verify(serviceRelationMapper, never()).updateById(any(ServiceRelationDO.class));
    }

    @Test
    void activateNewStudentInvitationCreatesBothRelationships() {
        stubTransaction();
        mockOperator();
        var invitation = invitation().setInvitationScene(PARTNER_INVITATION_SCENE_STUDENT)
                .setStudentPersonId(88L).setInitiatedByDirectorUserId(7L);
        when(invitationMapper.selectActiveByMobileAndCodeForUpdate("13800138000", "ABCD1234"))
                .thenReturn(invitation);
        when(partnerAccountService.create(any(), any(), any())).thenReturn(account());
        when(invitationMapper.updateById(any(PartnerInvitationDO.class))).thenReturn(1);
        service.activate(new PartnerActivateReqVO().setMobile("13800138000").setPassword("Password123")
                .setConfirmPassword("Password123").setInviteCode("ABCD1234"));
        verify(partnerStudentLinkService).bind(any(), org.mockito.ArgumentMatchers.eq(88L), any(),
                org.mockito.ArgumentMatchers.eq(7L));
        verify(ownershipMapper).insert(org.mockito.ArgumentMatchers.<PartnerOwnershipDO>argThat(
                row -> Long.valueOf(9L).equals(row.getEmployeeUserId())));
        verify(ownershipLogMapper).insert(any(PartnerOwnershipLogDO.class));
        verify(transactionManager).commit(any());
    }

    @Test
    void ownershipFailureRollsBackActivationTransaction() {
        stubTransaction();
        mockOperator();
        when(invitationMapper.selectActiveByMobileAndCodeForUpdate("13800138000", "ABCD1234"))
                .thenReturn(invitation().setInvitationScene(PARTNER_INVITATION_SCENE_STUDENT)
                        .setStudentPersonId(88L).setInitiatedByDirectorUserId(7L));
        when(partnerAccountService.create(any(), any(), any())).thenReturn(account());
        when(ownershipMapper.insert(any(PartnerOwnershipDO.class))).thenThrow(new IllegalStateException("test"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> service.activate(
                new PartnerActivateReqVO().setMobile("13800138000").setPassword("Password123")
                        .setConfirmPassword("Password123").setInviteCode("ABCD1234")));
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
        verify(invitationMapper, never()).updateById(any(PartnerInvitationDO.class));
    }

    @Test
    void disabledOperatorCannotCreateOrActivateAndDoesNotInvalidateInvitation() {
        mockStudent();
        mockOperator();
        when(adminUserApi.getUser(9L)).thenReturn(new AdminUserRespDTO().setId(9L)
                .setStatus(CommonStatusEnum.DISABLE.getStatus()));
        AssertUtils.assertServiceException(() -> service.createStudentInvitation(studentRequest(), 7L),
                cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_INVITATION_OPERATOR_INVALID);
        verifyNoInteractions(invitationMapper);
        stubTransaction();
        when(invitationMapper.selectActiveByMobileAndCodeForUpdate("13800138000", "ABCD1234"))
                .thenReturn(invitation().setInvitationScene(PARTNER_INVITATION_SCENE_STUDENT).setStudentPersonId(88L));
        AssertUtils.assertServiceException(() -> service.activate(new PartnerActivateReqVO()
                .setMobile("13800138000").setPassword("Password123")
                .setConfirmPassword("Password123").setInviteCode("ABCD1234")),
                cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_INVITATION_OPERATOR_INVALID);
        verify(partnerMapper, never()).insert(any(PartnerDO.class));
        verify(transactionManager).rollback(any());
    }

    @Test
    void concurrentStudentBindingPreventsActivationBeforeAccountCreation() {
        stubTransaction();
        when(invitationMapper.selectActiveByMobileAndCodeForUpdate("13800138000", "ABCD1234"))
                .thenReturn(invitation().setInvitationScene(PARTNER_INVITATION_SCENE_STUDENT).setStudentPersonId(88L));
        when(partnerStudentLinkService.hasActiveStudentLink(88L)).thenReturn(true);
        AssertUtils.assertServiceException(() -> service.activate(new PartnerActivateReqVO()
                .setMobile("13800138000").setPassword("Password123")
                .setConfirmPassword("Password123").setInviteCode("ABCD1234")),
                cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_STUDENT_LINK_CONFLICT);
        verify(partnerMapper, never()).insert(any(PartnerDO.class));
        verifyNoInteractions(ownershipMapper);
        verify(transactionManager).rollback(any());
    }

    private PartnerStudentInvitationCreateReqVO studentRequest() {
        return new PartnerStudentInvitationCreateReqVO().setStudentPersonId(88L).setAssignedOperatorUserId(9L)
                .setName("测试邀请").setMobile("13800138000");
    }

    private void mockStudent() {
        when(personMapper.selectById(88L)).thenReturn(new PersonDO().setId(88L).setName("测试学员"));
        when(serviceRelationMapper.selectActiveByContentDirectorAndPerson(7L, 88L))
                .thenReturn(List.of(new ServiceRelationDO().setId(66L).setPersonId(88L)
                        .setContentDirectorUserId(7L)));
    }

    private void mockOperator() {
        when(roleApi.getRoleByCode("new_media_operator")).thenReturn(new RoleRespDTO()
                .setId(3L).setCode("new_media_operator").setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(permissionApi.getUserRoleIdListByRoleIds(Set.of(3L))).thenReturn(Set.of(9L));
        when(adminUserApi.getUser(9L)).thenReturn(new AdminUserRespDTO()
                .setId(9L).setNickname("运营A").setStatus(CommonStatusEnum.ENABLE.getStatus()));
    }

    private void stubTransaction() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private PartnerInvitationDO invitation() {
        return new PartnerInvitationDO().setId(1L).setInviteCode("ABCD1234").setName("张三")
                .setInvitationScene("GENERAL")
                .setMobile("13800138000").setAssignedOperatorUserId(9L)
                .setAssignedOperatorNameSnapshot("运营A").setStatus(PARTNER_INVITATION_STATUS_ACTIVE)
                .setExpiresAt(LocalDateTime.now().plusDays(1)).setCreatedByUserId(1L).setVersion(0);
    }

    private PartnerAccountDO account() {
        return new PartnerAccountDO().setId(20L).setPartnerId(10L).setMobile("13800138000")
                .setStatus(CommonStatusEnum.ENABLE.getStatus()).setVersion(0);
    }
}
