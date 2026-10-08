package cn.iocoder.yudao.module.zsjos.service.examcalendar;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.EXAM_SCHEDULE_PERMISSION_DENIED;

@Component
public class ExamScheduleObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    public static final String BIZ_TYPE = "exam-schedule";

    @Resource private ExamScheduleMapper mapper;
    @Resource private PermissionApi permissionApi;
    @Resource private cn.iocoder.yudao.module.zsjos.service.calendar.CalendarNotificationAccess notificationAccess;

    @Override
    public String getBizType() {
        return BIZ_TYPE;
    }

    @Override
    public boolean hasPermission(Long id, String action, Long userId) {
        if (id == null || id <= 0 || userId == null) return false;
        Long tenantId = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId();
        if ("reedit".equals(action)) {
            var claimed = mapper.selectReeditRecord(id, tenantId);
            return claimed != null && java.util.Objects.equals(tenantId, claimed.getTenantId())
                    && java.util.Objects.equals(userId, claimed.getRevokedBy())
                    && (!Boolean.TRUE.equals(claimed.getDeleted()) || claimed.getReeditClaimedAt() != null)
                    && permissionApi.hasAnyPermissions(userId, ExamScheduleService.PERMISSION_MANAGE);
        }
        var row = mapper.selectById(id);
        if (row == null || Boolean.TRUE.equals(row.getDeleted()) || !java.util.Objects.equals(row.getTenantId(),
                cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId())) return false;
        if ("notify".equals(action)) {
            try { return notificationAccess.checkForUser("EXAM", false, userId); }
            catch (cn.iocoder.yudao.framework.common.exception.ServiceException denied) { return false; }
        }
        if ("read-attachment".equals(action)) {
            if (!permissionApi.hasAnyPermissions(userId, "zsjos:exam-calendar:query")) return false;
            if ("PUBLISHED".equals(row.getRecordStatus())) return true;
            if (!permissionApi.hasAnyPermissions(userId, ExamScheduleService.PERMISSION_MANAGE)) return false;
            return "DRAFT".equals(row.getRecordStatus()) || ("REVOKED".equals(row.getRecordStatus())
                    && row.getReeditClaimedAt() == null && row.getRevokedAt() != null
                    && row.getRevokedAt().plusMinutes(5).isAfter(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))));
        }
        return permissionApi.hasAnyPermissions(userId, ExamScheduleService.PERMISSION_MANAGE)
                && ("update".equals(action) || "publish".equals(action) || "revoke".equals(action) || "maintain".equals(action));
    }

    @Override
    public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw exception(EXAM_SCHEDULE_PERMISSION_DENIED);
    }
}
