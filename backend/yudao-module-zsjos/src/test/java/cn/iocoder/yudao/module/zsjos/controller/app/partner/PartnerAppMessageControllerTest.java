package cn.iocoder.yudao.module.zsjos.controller.app.partner;

import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyMessageDO;
import cn.iocoder.yudao.module.system.service.notify.NotifyMessageService;
import cn.iocoder.yudao.module.zsjos.service.notification.PartnerNotificationTargetService;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerAccountService;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerAppMessageControllerTest {
    @InjectMocks PartnerAppMessageController controller;
    @Mock NotifyMessageService notifyMessageService;
    @Mock PartnerAccountService accountService;
    @Mock PartnerNotificationTargetService targetService;

    @Test void ownOrderUsesAuthenticatedPartnerRelation() {
        fixture("business_detail");
        when(targetService.orderLeadPath(20L, 8L)).thenReturn("/lead/30");
        assertEquals("/lead/30", controller.get(1L).getData().getBusinessTarget());
    }
    @Test void missingRelationExplainsUnavailableTarget() {
        fixture("business_detail");
        var response = controller.get(1L).getData();
        assertNull(response.getBusinessTarget());
        assertNotNull(response.getTargetUnavailableReason());
    }
    @Test void messageOnlyActionDoesNotResolveBusinessObject() {
        fixture("message_detail");
        assertNull(controller.get(1L).getData().getBusinessTarget());
        verifyNoInteractions(targetService);
    }
    @Test void inaccessibleMessageNeverResolvesBusinessObject() {
        when(accountService.requireContext(null)).thenReturn(new PartnerContext(7L, 8L));
        when(notifyMessageService.getMyNotifyMessage(1L, null, 3)).thenReturn(null);
        assertNull(controller.get(1L).getData());
        verifyNoInteractions(targetService);
    }
    private void fixture(String action) {
        when(accountService.requireContext(null)).thenReturn(new PartnerContext(7L, 8L));
        var message = new NotifyMessageDO(); message.setId(1L); message.setBizId(20L);
        message.setBizType("sales_order"); message.setActionType(action);
        when(notifyMessageService.getMyNotifyMessage(1L, null, 3)).thenReturn(message);
    }
}
