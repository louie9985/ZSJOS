package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountCalendarPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountMaintenanceReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountMaintenanceRevisionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.AccountStageLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMaintenanceRevisionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.service.common.MediaDataScopeService;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaAccountMaintenanceServiceTest {
    @InjectMocks private MediaAccountMaintenanceService service;
    @Mock private MediaAccountMapper accountMapper;
    @Mock private MediaAccountMaintenanceRevisionMapper revisionMapper;
    @Mock private AccountStageLogMapper stageLogMapper;
    @Mock private DictDataApi dictDataApi;
    @Mock private PermissionApi permissionApi;
    @Mock private RoleApi roleApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private PersonMapper personMapper;
    @Mock private MediaWorkflowEventService workflowEventService;
    @Mock private MediaDataScopeService mediaDataScopeService;

    @Test
    void maintainAllowsArbitraryStageSnapshotsAndNotifiesOnlyOtherParticipant() {
        MediaAccountDO account = account().setSStage("s6").setSStageLabelSnapshot("S6 稳定增长");
        when(accountMapper.selectById(1L)).thenReturn(account);
        when(dictDataApi.getDictDataList(MediaAccountMaintenanceService.DICT_CURRENT_STATUS))
                .thenReturn(List.of(dict("a_active_growth", "A类：活跃增长账号")));
        when(dictDataApi.getDictDataList(MediaAccountMaintenanceService.DICT_STAGE))
                .thenReturn(List.of(dict("s1", "S1 定位期")));
        when(dictDataApi.getDictDataList(MediaAccountMaintenanceService.DICT_PRIMARY_PROBLEM))
                .thenReturn(List.of(dict("b1", "B1 定位不清"), dict("b2", "B2 学员不执行")));
        when(dictDataApi.getDictDataList(MediaAccountMaintenanceService.DICT_EXECUTION_MEASURE))
                .thenReturn(List.of(dict("cold_start_7d", "冷启动7天")));
        when(accountMapper.updateMaintenance(any(), eq(0))).thenReturn(1);
        when(revisionMapper.selectMaxRevisionNo(1L)).thenReturn(0);
        AdminUserRespDTO operator = new AdminUserRespDTO(); operator.setId(20L); operator.setNickname("运营甲");
        when(adminUserApi.getUser(20L)).thenReturn(operator);

        MediaAccountMaintenanceReqVO req = new MediaAccountMaintenanceReqVO();
        req.setVersion(0); req.setCurrentStatusValue("a_active_growth"); req.setStageValue("s1");
        req.setPrimaryProblemValues(List.of("b1", "b1", "b2"));
        req.setExecutionMeasureValue("cold_start_7d"); req.setAdjustmentDirection("优化定位");
        req.setStartDate(LocalDate.of(2026, 8, 26)); req.setEndDate(LocalDate.of(2026, 9, 1));

        assertEquals(1, service.maintain(1L, req, 20L));
        assertEquals("s1", account.getSStage());
        assertEquals("S1 定位期", account.getSStageLabelSnapshot());
        assertTrue(account.getPrimaryProblemsJson().contains("B1 定位不清"));
        ArgumentCaptor<MediaAccountMaintenanceRevisionDO> revision = ArgumentCaptor.forClass(MediaAccountMaintenanceRevisionDO.class);
        verify(revisionMapper).insert(revision.capture());
        assertEquals(1, revision.getValue().getRevisionNo());
        assertTrue(revision.getValue().getChangedFieldsJson().contains("stage"));
        verify(workflowEventService).notify(eq("media.account.maintenance_changed"), eq("media-account"),
                eq(1L), eq(30L), eq(20L), anyString(), argThat(payload -> "运营甲".equals(payload.get("operatorName"))));
        verify(workflowEventService, times(1)).notify(anyString(), anyString(), anyLong(), anyLong(),
                anyLong(), anyString(), anyMap());
    }

    @Test
    void unchangedMaintenanceDoesNotWriteRevisionOrNotify() {
        MediaAccountDO account = account();
        when(accountMapper.selectById(1L)).thenReturn(account);
        MediaAccountMaintenanceReqVO req = new MediaAccountMaintenanceReqVO();
        req.setVersion(0); req.setPrimaryProblemValues(List.of());

        assertEquals(0, service.maintain(1L, req, 20L));
        verify(accountMapper, never()).updateMaintenance(any(), anyInt());
        verifyNoInteractions(revisionMapper, workflowEventService);
    }

    @Test
    void staleVersionIsRejectedEvenWhenMaintenanceIsUnchanged() {
        when(accountMapper.selectById(1L)).thenReturn(account().setVersion(3));
        MediaAccountMaintenanceReqVO req = new MediaAccountMaintenanceReqVO();
        req.setVersion(2); req.setPrimaryProblemValues(List.of());

        ServiceException error = assertThrows(ServiceException.class, () -> service.maintain(1L, req, 20L));

        assertEquals(1_900_011_003, error.getCode());
        verifyNoInteractions(dictDataApi, revisionMapper, workflowEventService);
        verify(accountMapper, never()).updateMaintenance(any(), anyInt());
    }

    @Test
    void datesMustBeBothEmptyOrAValidPair() {
        MediaAccountMaintenanceReqVO req = new MediaAccountMaintenanceReqVO();
        req.setVersion(0); req.setStartDate(LocalDate.of(2026, 8, 26));
        ServiceException error = assertThrows(ServiceException.class, () -> service.maintain(1L, req, 20L));
        assertEquals(1_900_011_013, error.getCode());

        req.setEndDate(LocalDate.of(2026, 8, 25));
        assertEquals(1_900_011_013, assertThrows(ServiceException.class,
                () -> service.maintain(1L, req, 20L)).getCode());
        verifyNoInteractions(accountMapper);
    }

    @Test
    void calendarUsesParticipantScopeWithoutQueryAllPermission() {
        MediaAccountCalendarPageReqVO req = new MediaAccountCalendarPageReqVO();
        req.setRangeStart(LocalDate.of(2026, 8, 1)); req.setRangeEnd(LocalDate.of(2026, 8, 31));
        when(mediaDataScopeService.resolve(20L, MediaAccountMaintenanceService.PERMISSION_CALENDAR_QUERY_ALL))
                .thenReturn(new MediaDataScopeService.Scope(false, Set.of(20L, 21L, 30L)));
        when(accountMapper.selectCalendarPage(req, Set.of(20L, 21L, 30L), false))
                .thenReturn(new cn.iocoder.yudao.framework.common.pojo.PageResult<>(List.of(), 0L));
        when(accountMapper.selectCalendarUnscheduledCount(req, Set.of(20L, 21L, 30L), false)).thenReturn(3L);

        assertEquals(3, service.calendar(req, 20L).getUnscheduledCount());
        verify(accountMapper).selectCalendarPage(req, Set.of(20L, 21L, 30L), false);
    }

    @Test
    void maintainCanClearEveryFieldAndDeduplicatesRecipients() {
        MediaAccountDO account = account().setDirectorUserId(30L).setOwnerOperatorUserId(30L)
                .setCurrentStatusValue("a_active_growth").setCurrentStatusLabelSnapshot("A类：活跃增长账号")
                .setSStage("s3").setSStageLabelSnapshot("S3 内容验证")
                .setPrimaryProblemsJson("[{\"value\":\"b4\",\"labelSnapshot\":\"B4 平台分发弱\"}]")
                .setExecutionMeasureValue("content_validation_7d").setExecutionMeasureLabelSnapshot("内容验证7天")
                .setAdjustmentDirection("旧方向").setMaintenanceStartDate(LocalDate.of(2026, 8, 1))
                .setMaintenanceEndDate(LocalDate.of(2026, 8, 7));
        when(accountMapper.selectById(1L)).thenReturn(account);
        when(accountMapper.updateMaintenance(any(), eq(0))).thenReturn(1);
        when(revisionMapper.selectMaxRevisionNo(1L)).thenReturn(4);
        AdminUserRespDTO operator = new AdminUserRespDTO(); operator.setNickname("运营甲");
        when(adminUserApi.getUser(20L)).thenReturn(operator);

        MediaAccountMaintenanceReqVO req = new MediaAccountMaintenanceReqVO();
        req.setVersion(0); req.setPrimaryProblemValues(List.of()); req.setAdjustmentDirection("   ");

        assertEquals(1, service.maintain(1L, req, 20L));
        ArgumentCaptor<MediaAccountMaintenanceRevisionDO> revision = ArgumentCaptor.forClass(MediaAccountMaintenanceRevisionDO.class);
        verify(revisionMapper).insert(revision.capture());
        assertEquals(5, revision.getValue().getRevisionNo());
        assertNull(revision.getValue().getCurrentStatusValue());
        assertEquals("[]", revision.getValue().getPrimaryProblemsJson());
        verify(workflowEventService, times(1)).notify(eq("media.account.maintenance_changed"), anyString(),
                eq(1L), eq(30L), eq(20L), anyString(), anyMap());
    }

    @Test
    void versionConflictDoesNotWriteRevisionOrNotify() {
        when(accountMapper.selectById(1L)).thenReturn(account());
        when(dictDataApi.getDictDataList(MediaAccountMaintenanceService.DICT_STAGE))
                .thenReturn(List.of(dict("s2", "S2 冷启动")));
        when(accountMapper.updateMaintenance(any(), eq(0))).thenReturn(0);
        MediaAccountMaintenanceReqVO req = new MediaAccountMaintenanceReqVO();
        req.setVersion(0); req.setStageValue("s2");

        ServiceException error = assertThrows(ServiceException.class, () -> service.maintain(1L, req, 20L));
        assertEquals(1_900_011_003, error.getCode());
        verify(revisionMapper, never()).insert(any(MediaAccountMaintenanceRevisionDO.class));
        verifyNoInteractions(workflowEventService);
    }

    @Test
    void historyReturnsImmutableSnapshotAndOperatorName() {
        MediaAccountMaintenanceRevisionDO row = new MediaAccountMaintenanceRevisionDO().setId(8L).setAccountId(1L)
                .setRevisionNo(2).setStageValue("s5").setStageLabelSnapshot("S5 客资验证")
                .setPrimaryProblemsJson("[{\"value\":\"b7\",\"labelSnapshot\":\"B7 承接不畅\"}]")
                .setChangedFieldsJson("[\"stage\",\"primaryProblems\"]").setOperatedByUserId(21L)
                .setOperatedAt(LocalDateTime.of(2026, 8, 26, 10, 30));
        when(accountMapper.selectById(1L)).thenReturn(account());
        when(revisionMapper.selectPageByAccountId(any(PageParam.class), eq(1L)))
                .thenReturn(new PageResult<>(List.of(row), 1L));
        AdminUserRespDTO user = new AdminUserRespDTO(); user.setId(21L); user.setNickname("编导乙");
        when(adminUserApi.getUserMap(any())).thenReturn(Map.of(21L, user));

        var result = service.history(1L, new PageParam(), 20L);
        assertEquals(1L, result.getTotal());
        assertEquals("S5 客资验证", result.getList().getFirst().getStageLabelSnapshot());
        assertEquals("B7 承接不畅", result.getList().getFirst().getPrimaryProblems().getFirst().getLabelSnapshot());
        assertEquals("编导乙", result.getList().getFirst().getOperatedByUserName());
    }

    @Test
    void calendarSupportsQueryAllAndRejectsReversedWindow() {
        MediaAccountCalendarPageReqVO req = new MediaAccountCalendarPageReqVO();
        req.setRangeStart(LocalDate.of(2026, 8, 1)); req.setRangeEnd(LocalDate.of(2026, 8, 31));
        when(mediaDataScopeService.resolve(20L, MediaAccountMaintenanceService.PERMISSION_CALENDAR_QUERY_ALL))
                .thenReturn(new MediaDataScopeService.Scope(true, Set.of()));
        when(accountMapper.selectCalendarPage(req, Set.of(), true)).thenReturn(new PageResult<>(List.of(), 0L));
        when(accountMapper.selectCalendarUnscheduledCount(req, Set.of(), true)).thenReturn(0L);
        service.calendar(req, 20L);
        verify(accountMapper).selectCalendarPage(req, Set.of(), true);

        req.setRangeStart(LocalDate.of(2026, 9, 1));
        assertEquals(1_900_011_013, assertThrows(ServiceException.class,
                () -> service.calendar(req, 20L)).getCode());
    }

    @Test
    void allCalendarDoesNotApplyAccountObjectScope() {
        MediaAccountCalendarPageReqVO req = new MediaAccountCalendarPageReqVO();
        req.setRangeStart(LocalDate.of(2026, 8, 1)); req.setRangeEnd(LocalDate.of(2026, 8, 31));
        when(accountMapper.selectCalendarPage(req, Set.of(), true)).thenReturn(new PageResult<>(List.of(), 0L));
        when(accountMapper.selectCalendarUnscheduledCount(req, Set.of(), true)).thenReturn(2L);

        assertEquals(2, service.allCalendar(req, 20L).getUnscheduledCount());

        verifyNoInteractions(mediaDataScopeService);
        verify(accountMapper).selectCalendarPage(req, Set.of(), true);
    }

    @Test
    void calendarCandidatesReturnOnlyEnabledUsersFromSystemRoles() {
        when(roleApi.getRoleByCode("content_director")).thenReturn(role(11L, CommonStatusEnum.ENABLE.getStatus()));
        when(roleApi.getRoleByCode("new_media_operator")).thenReturn(role(12L, CommonStatusEnum.ENABLE.getStatus()));
        when(permissionApi.getUserRoleIdListByRoleIds(Set.of(11L))).thenReturn(Set.of(30L, 31L));
        when(permissionApi.getUserRoleIdListByRoleIds(Set.of(12L))).thenReturn(Set.of(20L));
        when(adminUserApi.getUserList(Set.of(30L, 31L))).thenReturn(List.of(
                user(31L, "停用编导", CommonStatusEnum.DISABLE.getStatus()),
                user(30L, "编导乙", CommonStatusEnum.ENABLE.getStatus())));
        when(adminUserApi.getUserList(Set.of(20L))).thenReturn(List.of(
                user(20L, "运营甲", CommonStatusEnum.ENABLE.getStatus())));

        var result = service.calendarCandidates(20L);

        assertEquals(List.of(30L), result.getDirectors().stream().map(item -> item.getId()).toList());
        assertEquals(List.of(20L), result.getOperators().stream().map(item -> item.getId()).toList());
    }

    private MediaAccountDO account() {
        return new MediaAccountDO().setId(1L).setAccountNo("MA-001").setNickname("中世健课堂")
                .setOwnerOperatorUserId(20L).setDirectorUserId(30L).setVersion(0);
    }

    private DictDataRespDTO dict(String value, String label) {
        DictDataRespDTO row = new DictDataRespDTO(); row.setValue(value); row.setLabel(label); return row;
    }

    private RoleRespDTO role(Long id, Integer status) {
        RoleRespDTO role = new RoleRespDTO(); role.setId(id); role.setStatus(status); return role;
    }

    private AdminUserRespDTO user(Long id, String nickname, Integer status) {
        AdminUserRespDTO user = new AdminUserRespDTO(); user.setId(id); user.setNickname(nickname); user.setStatus(status); return user;
    }
}
