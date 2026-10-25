package cn.iocoder.yudao.module.zsjos.service.positioning;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PositioningAttachmentLimitsTest {
    @Test void exactBudgetAndEmptyDraftAccepted() {
        assertDoesNotThrow(() -> PositioningAttachmentLimits.requireWithinBudget(List.of()));
        assertDoesNotThrow(() -> PositioningAttachmentLimits.requireWithinBudget(List.of(700L * 1024 * 1024, 324L * 1024 * 1024)));
    }
    @Test void totalExcessAndLongOverflowRejected() {
        assertEquals(1900158000, assertThrows(ServiceException.class, () ->
                PositioningAttachmentLimits.requireWithinBudget(List.of(PositioningAttachmentLimits.MAX_TOTAL_BYTES, 1L))).getCode());
        assertThrows(ServiceException.class, () -> PositioningAttachmentLimits.requireWithinBudget(List.of(Long.MAX_VALUE, Long.MAX_VALUE)));
    }
    @Test void UnknownAndEmptySizeRejected() {
        assertThrows(ServiceException.class, () -> PositioningAttachmentLimits.requireWithinBudget(java.util.Arrays.asList((Long) null)));
        assertThrows(ServiceException.class, () -> PositioningAttachmentLimits.requireWithinBudget(List.of(0L)));
    }
}
