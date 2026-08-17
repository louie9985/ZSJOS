package cn.iocoder.yudao.module.system.controller.app.partner;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.message.NotifyMessageMyPageReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyMessageDO;
import cn.iocoder.yudao.module.system.service.notify.NotifyMessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartnerAppMessageControllerTest {

    private static final long USER_ID = 42L;

    private final NotifyMessageService notifyMessageService = mock(NotifyMessageService.class);
    private final PartnerAppMessageController controller = new PartnerAppMessageController();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "notifyMessageService", notifyMessageService);
        LoginUser loginUser = new LoginUser().setId(USER_ID).setUserType(UserTypeEnum.ADMIN.getValue());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void controllerRequiresPartnerRole() {
        PreAuthorize annotation = PartnerAppMessageController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation);
        assertEquals("@ss.hasRole('part_time_partner')", annotation.value());
    }

    @Test
    void pageUsesAuthenticatedAdminOwnership() {
        NotifyMessageMyPageReqVO request = new NotifyMessageMyPageReqVO();
        when(notifyMessageService.getMyMyNotifyMessagePage(
                request, USER_ID, UserTypeEnum.ADMIN.getValue())).thenReturn(new PageResult<>(List.of(), 0L));

        controller.getPage(request);

        verify(notifyMessageService).getMyMyNotifyMessagePage(
                request, USER_ID, UserTypeEnum.ADMIN.getValue());
    }

    @Test
    void pagePreservesEmptyResult() {
        NotifyMessageMyPageReqVO request = new NotifyMessageMyPageReqVO();
        when(notifyMessageService.getMyMyNotifyMessagePage(
                request, USER_ID, UserTypeEnum.ADMIN.getValue())).thenReturn(new PageResult<>(List.of(), 0L));

        PageResult<?> result = controller.getPage(request).getData();

        assertEquals(0L, result.getTotal());
        assertEquals(List.of(), result.getList());
    }

    @Test
    void detailPreservesOwnershipMiss() {
        when(notifyMessageService.getMyNotifyMessage(8L, USER_ID, UserTypeEnum.ADMIN.getValue()))
                .thenReturn(null);

        assertNull(controller.get(8L).getData());
    }

    @Test
    void detailReadAndUnreadCountUseAuthenticatedAdminOwnership() {
        when(notifyMessageService.getMyNotifyMessage(7L, USER_ID, UserTypeEnum.ADMIN.getValue()))
                .thenReturn(new NotifyMessageDO().setId(7L));
        PartnerAppMessageController.ReadReqVO read = new PartnerAppMessageController.ReadReqVO();
        read.setIds(List.of(7L));

        controller.get(7L);
        controller.read(read);
        controller.getUnreadCount();

        verify(notifyMessageService).getMyNotifyMessage(7L, USER_ID, UserTypeEnum.ADMIN.getValue());
        verify(notifyMessageService).updateNotifyMessageRead(List.of(7L), USER_ID, UserTypeEnum.ADMIN.getValue());
        verify(notifyMessageService).getUnreadNotifyMessageCount(USER_ID, UserTypeEnum.ADMIN.getValue());
    }

    @Test
    void repeatedReadRemainsOwnershipScopedAndIdempotent() {
        PartnerAppMessageController.ReadReqVO read = new PartnerAppMessageController.ReadReqVO();
        read.setIds(List.of(7L));

        controller.read(read);
        controller.read(read);

        verify(notifyMessageService, org.mockito.Mockito.times(2))
                .updateNotifyMessageRead(List.of(7L), USER_ID, UserTypeEnum.ADMIN.getValue());
    }
}
