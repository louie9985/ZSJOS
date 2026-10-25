package cn.iocoder.yudao.module.zsjos.service.positioning;

import java.util.Collection;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

/** Budget for one retained attachment group, shared by interviews and positioning cards. */
public final class PositioningAttachmentLimits {
    public static final long MAX_TOTAL_BYTES = 1024L * 1024 * 1024;

    private PositioningAttachmentLimits() { }

    public static void requireWithinBudget(Collection<Long> sizes) {
        long remaining = MAX_TOTAL_BYTES;
        for (Long size : sizes) {
            if (size == null || size <= 0) throw exception(DIRECTOR_FORM_VALUE_INVALID);
            if (size > remaining) throw exception(POSITIONING_ATTACHMENT_TOTAL_EXCEEDED);
            remaining -= size;
        }
    }
}
