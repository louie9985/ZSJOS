package cn.iocoder.yudao.module.system.controller.admin.logger;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.controller.admin.logger.vo.operatelog.OperateLogPageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.logger.vo.operatelog.OperateLogRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.logger.OperateLogDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.service.logger.OperateLogService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OperateLogControllerTest extends BaseMockitoUnitTest {
    @InjectMocks private OperateLogController controller;
    @Mock private OperateLogService operateLogService;
    @Mock private AdminUserService adminUserService;
    @Mock private HttpServletResponse response;

    @Test
    void pageResolvesNamesInBatchWithoutCrossingIdentityTypes() {
        OperateLogPageReqVO request = new OperateLogPageReqVO();
        List<OperateLogDO> logs = List.of(log(7L, 2), log(7L, 2), log(8L, 2),
                log(7L, 3), log(7L, 1), log(null, 2), log(7L, null));
        when(operateLogService.getOperateLogPage(request)).thenReturn(new PageResult<>(logs, 7L));
        when(adminUserService.getUserMap(Set.of(7L, 8L))).thenReturn(Map.of(7L, user()));
        PageResult<OperateLogRespVO> result = controller.pageOperateLog(request).getData();
        assertEquals(7L, result.getTotal());
        assertEquals("测试姓名", result.getList().get(0).getUserName());
        assertEquals("测试姓名", result.getList().get(1).getUserName());
        result.getList().subList(2, 7).forEach(row -> assertNull(row.getUserName()));
        verify(adminUserService).getUserMap(Set.of(7L, 8L));
    }

    @Test
    void detailResolvesNameWithoutTranslationAspect() {
        when(operateLogService.getOperateLog(1L)).thenReturn(log(7L, 2));
        when(adminUserService.getUserMap(Set.of(7L))).thenReturn(Map.of(7L, user()));
        assertEquals("测试姓名", controller.getOperateLog(1L).getData().getUserName());
    }

    @Test
    void absentDetailAndEmptyPageDoNotQueryUsers() {
        assertNull(controller.getOperateLog(1L).getData());
        OperateLogPageReqVO request = new OperateLogPageReqVO();
        when(operateLogService.getOperateLogPage(request)).thenReturn(new PageResult<>(List.of(), 0L));
        assertTrue(controller.pageOperateLog(request).getData().getList().isEmpty());
        verifyNoInteractions(adminUserService);
    }

    @Test
    void exportIncludesResolvedNameWithoutTranslationService() throws Exception {
        OperateLogPageReqVO request = new OperateLogPageReqVO();
        when(operateLogService.getOperateLogPage(request))
                .thenReturn(new PageResult<>(List.of(log(7L, 2)), 1L));
        when(adminUserService.getUserMap(Set.of(7L))).thenReturn(Map.of(7L, user()));
        try (var excel = mockStatic(ExcelUtils.class)) {
            controller.exportOperateLog(response, request);
            excel.verify(() -> ExcelUtils.write(eq(response), eq("操作日志.xls"), eq("数据列表"),
                    eq(OperateLogRespVO.class), argThat((List<OperateLogRespVO> rows) ->
                            rows.size() == 1 && "测试姓名".equals(rows.get(0).getUserName()))));
        }
        assertEquals(PageParam.PAGE_SIZE_NONE, request.getPageSize());
    }

    private static OperateLogDO log(Long userId, Integer userType) {
        OperateLogDO log = new OperateLogDO();
        log.setUserId(userId);
        log.setUserType(userType);
        return log;
    }

    private static AdminUserDO user() {
        AdminUserDO user = new AdminUserDO();
        user.setId(7L);
        user.setNickname("测试姓名");
        return user;
    }
}
