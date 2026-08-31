package cn.iocoder.yudao.module.zsjos.controller.app.partner;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.system.service.notify.NotifyMessageService;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerAccountService;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PartnerAppMessageControllerTest {
    private static final long ACCOUNT_ID = 42L;
    private final NotifyMessageService messageService = mock(NotifyMessageService.class);
    private final PartnerAccountService accountService = mock(PartnerAccountService.class);
    private final PartnerAppMessageController controller = new PartnerAppMessageController();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "notifyMessageService", messageService);
        ReflectionTestUtils.setField(controller, "accountService", accountService);
        when(accountService.requireContext(ACCOUNT_ID)).thenReturn(new PartnerContext(ACCOUNT_ID, 7L));
        LoginUser user = new LoginUser().setId(ACCOUNT_ID).setUserType(UserTypeEnum.PARTNER.getValue());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void messageOperationsUsePartnerAccountOwnership() {
        PartnerAppMessageController.PartnerMessagePageReqVO request =
                new PartnerAppMessageController.PartnerMessagePageReqVO();
        request.setGroup("feedback");
        when(messageService.getMyMyNotifyMessagePage(request, ACCOUNT_ID, UserTypeEnum.PARTNER.getValue()))
                .thenReturn(PageResult.empty());
        PartnerAppMessageController.ReadReqVO read = new PartnerAppMessageController.ReadReqVO();
        read.setIds(List.of(8L));

        controller.page(request);
        controller.get(8L);
        controller.read(read);
        controller.unreadCount();

        verify(accountService, times(4)).requireContext(ACCOUNT_ID);
        assertEquals("feedback", request.getBizType());
        verify(messageService).getMyMyNotifyMessagePage(request, ACCOUNT_ID, UserTypeEnum.PARTNER.getValue());
        verify(messageService).getMyNotifyMessage(8L, ACCOUNT_ID, UserTypeEnum.PARTNER.getValue());
        verify(messageService).updateNotifyMessageRead(List.of(8L), ACCOUNT_ID, UserTypeEnum.PARTNER.getValue());
        verify(messageService).getUnreadNotifyMessageCount(ACCOUNT_ID, UserTypeEnum.PARTNER.getValue());
    }
}
