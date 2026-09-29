package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarNotifyBatchMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

/** History belongs to the retained batch, including after the underlying calendar was deleted. */
@Component
public class CalendarNotifyBatchObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    public static final String BIZ_TYPE = "calendar-notification-batch";
    @Resource private CalendarNotifyBatchMapper mapper;
    @Resource private CalendarNotificationAccess access;

    @Override public String getBizType() { return BIZ_TYPE; }

    @Override public boolean hasPermission(Long id, String action, Long userId) {
        try { check(id, action, userId); return true; }
        catch (ServiceException denied) { return false; }
    }

    @Override public void check(Long id, String action, Long userId) {
        if (id == null || id <= 0 || userId == null || !("read".equals(action) || "recipients".equals(action)))
            throw exception(CALENDAR_NOTIFY_PERMISSION_DENIED);
        var row = mapper.selectById(id);
        if (row == null || Boolean.TRUE.equals(row.getDeleted())
                || !Objects.equals(row.getTenantId(), TenantContextHolder.getRequiredTenantId()))
            throw exception(CALENDAR_NOTIFY_BATCH_NOT_EXISTS);
        // Existing tenant-wide calendar visibility remains unchanged; the batch type selects the send permission.
        access.checkForUser(row.getCalendarType(), false, userId);
    }
}
