package cn.iocoder.yudao.module.zsjos.service.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.BusinessTaskPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.BusinessTaskRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessTaskServiceImplTest {
    @Test
    void administratorReadUsesSelectedScopeWithoutActionProjection() {
        var mapper = mock(BusinessTaskMapper.class);
        var scopes = mock(cn.iocoder.yudao.module.zsjos.service.common.BusinessReadScopeService.class);
        var users = mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class);
        var service = new BusinessTaskServiceImpl(mapper, List.of(), Clock.systemUTC());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "readScopeService", scopes);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "userApi", users);
        var req = new BusinessTaskPageReqVO(); req.setReadScope("ALL");
        var task = new BusinessTaskDO(); task.setId(2L); task.setBizType("sample"); task.setAssigneeId(20L);
        task.setStatus("pending"); task.setActionCode("COMPLETE_BIRTHDAY_CARE");
        when(scopes.resolve("ALL", null, 10L)).thenReturn(null);
        when(mapper.selectReadPage(isNull(), same(req), any())).thenReturn(new PageResult<>(List.of(task), 1L));
        when(users.getUserMap(List.of(20L))).thenReturn(Map.of(20L,
                new cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO().setId(20L).setNickname("测试执行人")));
        var result = service.getMyPage(10L, req).getList().getFirst();
        assertEquals(20L, result.getAssigneeId());
        assertEquals("测试执行人", result.getAssigneeName());
        assertFalse(result.getActionable()); assertNull(result.getActionCode());
        verify(mapper, never()).selectMyPage(any(), any(), any());
        when(scopes.resolve("ALL", null, 11L)).thenThrow(new cn.iocoder.yudao.framework.common.exception.ServiceException(403, "denied"));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> service.getMyPage(11L, req));
    }
    @Test
    void usesDatabasePageAndMarksUnknownSceneNotActionable() {
        BusinessTaskMapper mapper = mock(BusinessTaskMapper.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-10T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        BusinessTaskDO task = new BusinessTaskDO(); task.setId(1L); task.setBizType("unknown"); task.setBizId(8L);
        task.setTaskType("unknown_task"); task.setStatus("pending"); task.setDueAt(LocalDateTime.of(2026, 8, 10, 11, 0));
        when(mapper.selectMyPage(eq(9L), any(), any())).thenReturn(new PageResult<>(List.of(task), 1L));
        BusinessTaskServiceImpl service = new BusinessTaskServiceImpl(mapper, List.of(), clock);
        BusinessTaskPageReqVO reqVO = new BusinessTaskPageReqVO(); reqVO.setBucket("today");

        PageResult<BusinessTaskRespVO> page = service.getMyPage(9L, reqVO);

        assertEquals(1L, page.getTotal()); assertEquals("业务任务 #1", page.getList().getFirst().getTitle());
        assertFalse(page.getList().getFirst().getActionable()); assertNull(page.getList().getFirst().getActionCode());
        verify(mapper).selectMyPage(eq(9L), same(reqVO), eq(LocalDateTime.of(2026, 8, 10, 12, 0)));
    }

    @Test
    void providerFillsHistoricalTaskWithoutOverwritingSnapshot() {
        BusinessTaskMapper mapper = mock(BusinessTaskMapper.class);
        BusinessTaskDO task = new BusinessTaskDO(); task.setId(2L); task.setBizType("sample"); task.setBizId(3L);
        task.setTaskType("sample"); task.setStatus("pending"); task.setTitleSnapshot("固定标题");
        when(mapper.selectMyPage(anyLong(), any(), any())).thenReturn(new PageResult<>(List.of(task), 1L));
        BusinessTaskSceneProvider provider = new BusinessTaskSceneProvider() {
            public String getBizType() { return "sample"; }
            public Map<Long, BusinessTaskDisplay> getDisplayMap(List<BusinessTaskDO> tasks) {
                return Map.of(2L, new BusinessTaskDisplay("动态标题", "摘要", "OPEN_SAMPLE"));
            }
        };
        BusinessTaskServiceImpl service = new BusinessTaskServiceImpl(mapper, List.of(provider), Clock.systemUTC());

        BusinessTaskRespVO result = service.getMyPage(1L, new BusinessTaskPageReqVO()).getList().getFirst();

        assertEquals("固定标题", result.getTitle()); assertEquals("摘要", result.getSummary());
        assertEquals("OPEN_SAMPLE", result.getActionCode()); assertTrue(result.getActionable());
    }
}
