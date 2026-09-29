package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifyUserPageReqVO;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarNotificationUsersTest {
    @InjectMocks private CalendarNotificationService service;
    @Mock private AdminUserApi users;
    @Mock private CalendarNotificationAccess access;

    @ParameterizedTest
    @ValueSource(strings = {"EXAM", "COURSE"})
    void exposesSystemDepartmentAndPreservesCandidateScopeAndPagination(String type) {
        var request = new CalendarNotifyUserPageReqVO();
        request.setCalendarType(type); request.setKeyword("测试"); request.setPageNo(2); request.setPageSize(100);
        var employee = new AdminUserRespDTO().setId(7L).setNickname("测试员工").setDeptId(31L);
        var orphan = new AdminUserRespDTO().setId(8L).setNickname("未分配员工");
        when(users.getCandidateUserPage(any())).thenReturn(new PageResult<>(List.of(employee, orphan), 102L));
        var result = service.getUsers(request);
        assertEquals(102L, result.getTotal());
        assertEquals(7L, result.getList().getFirst().id());
        assertEquals("测试员工", result.getList().getFirst().nickname());
        assertEquals(31L, result.getList().getFirst().deptId());
        assertNull(result.getList().get(1).deptId());
        var order = inOrder(access, users);
        order.verify(access).check(type, false);
        order.verify(users).getCandidateUserPage(argThat(query -> "ALL".equals(query.getQualificationMode())
                && "测试".equals(query.getKeyword()) && query.getPageNo() == 2 && query.getPageSize() == 100));
        verifyNoMoreInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXAM", "COURSE"})
    void rejectsUnauthorizedCandidatesBeforeQueryingSystem(String type) {
        var request = new CalendarNotifyUserPageReqVO(); request.setCalendarType(type);
        var denied = new IllegalStateException("denied");
        doThrow(denied).when(access).check(type, false);
        assertSame(denied, assertThrows(IllegalStateException.class, () -> service.getUsers(request)));
        verifyNoInteractions(users);
    }
}
