package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
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
    @Mock private AdminUserApi adminUserApi;
    @Mock private PersonMapper personMapper;
    @Mock private MediaWorkflowEventService workflowEventService;
    @Mock private MediaAccountCalendarScopeService calendarScopeService;

    @Test
    void legacyMaintenanceCannotWriteAutomaticStatusOrStage() {
        when(accountMapper.selectById(1L)).thenReturn(account());
        assertEquals(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MEDIA_ACCOUNT_PROFILE_UPGRADE_REQUIRED.getCode(),
                assertThrows(ServiceException.class, () -> service.maintain(1L, new MediaAccountMaintenanceReqVO(), 20L)).getCode());
        verifyNoInteractions(revisionMapper, stageLogMapper, workflowEventService);
    }

    @Test
    void calendarUsesParticipantScopeWithoutQueryAllPermission() {
        MediaAccountCalendarPageReqVO req = new MediaAccountCalendarPageReqVO();
        req.setRangeStart(LocalDate.of(2026, 8, 1)); req.setRangeEnd(LocalDate.of(2026, 8, 31));
        when(calendarScopeService.resolve(20L))
                .thenReturn(new MediaAccountCalendarScopeService.Scope(false, Set.of(20L, 21L, 30L)));
        when(accountMapper.selectCalendarPage(req, Set.of(20L, 21L, 30L), false))
                .thenReturn(new cn.iocoder.yudao.framework.common.pojo.PageResult<>(List.of(), 0L));
        when(accountMapper.selectCalendarUnscheduledCount(req, Set.of(20L, 21L, 30L), false)).thenReturn(3L);

        assertEquals(3, service.calendar(req, 20L).getUnscheduledCount());
        verify(accountMapper).selectCalendarPage(req, Set.of(20L, 21L, 30L), false);
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
        when(calendarScopeService.resolve(20L))
                .thenReturn(new MediaAccountCalendarScopeService.Scope(true, Set.of()));
        when(accountMapper.selectCalendarPage(req, Set.of(), true)).thenReturn(new PageResult<>(List.of(), 0L));
        when(accountMapper.selectCalendarUnscheduledCount(req, Set.of(), true)).thenReturn(0L);
        service.calendar(req, 20L);
        verify(accountMapper).selectCalendarPage(req, Set.of(), true);

        req.setRangeStart(LocalDate.of(2026, 9, 1));
        assertEquals(1_900_011_013, assertThrows(ServiceException.class,
                () -> service.calendar(req, 20L)).getCode());
    }

    @Test
    void calendarCandidatesReturnOnlyEnabledUsersFromAuthorizedAccounts() {
        when(calendarScopeService.resolve(20L)).thenReturn(new MediaAccountCalendarScopeService.Scope(false, Set.of(20L, 30L)));
        when(accountMapper.selectCalendarCandidateAccounts(Set.of(20L, 30L), false)).thenReturn(List.of(
                account(), account().setDirectorUserId(31L)));
        when(adminUserApi.getUserMap(Set.of(20L, 30L, 31L))).thenReturn(Map.of(
                20L, user(20L, "运营甲", CommonStatusEnum.ENABLE.getStatus()),
                30L, user(30L, "编导乙", CommonStatusEnum.ENABLE.getStatus()),
                31L, user(31L, "停用编导", CommonStatusEnum.DISABLE.getStatus())));

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

    private AdminUserRespDTO user(Long id, String nickname, Integer status) {
        AdminUserRespDTO user = new AdminUserRespDTO(); user.setId(id); user.setNickname(nickname); user.setStatus(status); return user;
    }
}
