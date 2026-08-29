package cn.iocoder.yudao.module.zsjos.controller.admin.bpm;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.bpm.vo.ZsjosBpmBusinessTaskTargetRespVO;
import cn.iocoder.yudao.module.zsjos.service.bpm.ZsjosBpmBusinessTaskTargetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ZsjosBpmBusinessTaskTargetControllerTest {

    private static final Long USER_ID = 88L;
    private final ZsjosBpmBusinessTaskTargetService targetService = mock(ZsjosBpmBusinessTaskTargetService.class);
    private final ZsjosBpmBusinessTaskTargetController controller = new ZsjosBpmBusinessTaskTargetController();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void delegatesTaskTargetLookupWithLoginUserId() {
        ReflectionTestUtils.setField(controller, "targetService", targetService);
        LoginUser user = new LoginUser();
        user.setId(USER_ID);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin-api/zsjos/bpm/business-task-target");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityFrameworkUtils.setLoginUser(user, request);
        ZsjosBpmBusinessTaskTargetRespVO target = new ZsjosBpmBusinessTaskTargetRespVO();
        target.setSupported(true);
        target.setBizType("lead_appeal");
        target.setRoute("/zsjos/appeals");
        when(targetService.getTarget("task-1", "done", USER_ID)).thenReturn(target);

        CommonResult<ZsjosBpmBusinessTaskTargetRespVO> result = controller.getBusinessTaskTarget("task-1", "done");

        assertEquals(target, result.getData());
        verify(targetService).getTarget("task-1", "done", USER_ID);
    }
}
