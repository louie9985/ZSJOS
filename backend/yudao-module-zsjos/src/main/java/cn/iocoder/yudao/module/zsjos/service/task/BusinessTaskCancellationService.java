package cn.iocoder.yudao.module.zsjos.service.task;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.TASK_TYPE_SUBMITTER_ASSIST;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.TASK_TYPE_REVISION;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.BUSINESS_TASK_NOT_EXISTS;

@Service
public class BusinessTaskCancellationService {
    public static final String BIZ_TYPE = "business_task_cancellation";
    public static final ErrorCode FORBIDDEN = new ErrorCode(1_900_180_001, "只能取消本人待处理的订单补正或提交人协助待办");
    public static final ErrorCode INVALID_REASON = new ErrorCode(1_900_180_002, "请填写取消原因，最多500字");
    public static final ErrorCode STATE_CHANGED = new ErrorCode(1_900_180_003, "待办状态已变化，请刷新后重试");
    @Resource private BusinessTaskMapper taskMapper;

    public static boolean supports(BusinessTaskDO task) {
        return TASK_TYPE_REVISION.equals(task.getTaskType()) || TASK_TYPE_SUBMITTER_ASSIST.equals(task.getTaskType());
    }
    public static boolean owns(BusinessTaskDO task, Long userId) {
        return task != null && userId != null && Objects.equals(task.getAssigneeId(), userId)
                && "user".equals(task.getAssigneeType());
    }

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = BIZ_TYPE, bizId = "#taskId", action = "cancel")
    public boolean cancel(Long taskId, Long userId, String reason) {
        String normalized = reason == null ? "" : reason.strip();
        if (normalized.isBlank() || normalized.length() > 500) throw exception(INVALID_REASON);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        BusinessTaskDO task = taskMapper.selectByIdForUpdate(taskId, tenantId);
        if (task == null) throw exception(BUSINESS_TASK_NOT_EXISTS);
        if (!owns(task, userId) || !supports(task)) throw exception(FORBIDDEN);
        // Only the inbox row changes: domain records and their ability to continue remain intact.
        if ("cancelled".equals(task.getStatus())) return true;
        if (!"pending".equals(task.getStatus())) throw exception(STATE_CHANGED);
        if (taskMapper.cancelMyPendingById(taskId, tenantId, userId, LocalDateTime.now(), normalized) != 1)
            throw exception(STATE_CHANGED);
        return true;
    }
}
