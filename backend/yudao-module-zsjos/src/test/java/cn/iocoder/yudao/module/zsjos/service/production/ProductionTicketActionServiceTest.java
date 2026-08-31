package cn.iocoder.yudao.module.zsjos.service.production;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductionTicketActionServiceTest {
    @InjectMocks private ProductionTicketActionService service;
    @Mock private ProductionTicketCommandService commandService;
    @Mock private ProductionTicketService ticketService;

    @Test
    void firstRejectNormalizesReasonMutatesAndCompletesOnce() {
        when(commandService.fingerprint("reject-assignment", 10L, 2, "原因", 20L)).thenReturn("fp");
        when(commandService.begin(eq("key-1"), any(), eq(Boolean.class)))
                .thenReturn(new ProductionTicketCommandService.Claim<>(true, null));

        assertTrue(service.rejectAssignment(10L, 2, "  原因  ", "key-1", 20L));

        verify(ticketService).rejectAssignment(10L, 2, "原因", "key-1");
        verify(commandService).complete("key-1", 20L, true);
    }

    @Test
    void exactClaimReplaySkipsTicketMutationAndSecondCompletion() {
        when(commandService.fingerprint("claim", 10L, 2, 20L)).thenReturn("fp");
        when(commandService.begin(eq("key-1"), any(), eq(Boolean.class)))
                .thenReturn(new ProductionTicketCommandService.Claim<>(false, true));

        assertTrue(service.claim(10L, 2, "key-1", 20L));

        verifyNoInteractions(ticketService);
        verify(commandService, never()).complete(anyString(), anyLong(), any());
    }
}
