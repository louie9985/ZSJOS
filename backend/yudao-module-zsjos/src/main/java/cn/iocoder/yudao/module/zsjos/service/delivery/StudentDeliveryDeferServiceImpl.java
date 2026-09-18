package cn.iocoder.yudao.module.zsjos.service.delivery;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.delivery.vo.StudentDeliveryDeferReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryDeferDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryStageDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryDeferMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryStageMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.service.delivery.StudentDeliveryErrors.*;

@Service
public class StudentDeliveryDeferServiceImpl implements StudentDeliveryDeferService {
    @Resource private StudentDeliveryDeferMapper deferMapper;
    @Resource private cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService events;
    @Resource private cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService tasks;
    @Resource private StudentDeliveryStageMapper stageMapper;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryPlanMapper plans;
    @Resource private BpmProcessInstanceApi processInstanceApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;

    @Override
    @Transactional
    @ZsjosPermission(bizType = StudentDeliveryStagePermissionProvider.BIZ_TYPE, bizId = "#req.stageId", action = "defer")
    public StudentDeliveryDeferDO request(StudentDeliveryDeferReqVO req) {
        if (req.getNewDueAt() == null) throw exception(DEFER_DAYS_INVALID);
        if (req.getReason() == null || req.getReason().isBlank() || req.getReason().length() > 1000) throw exception(DEFER_REASON_INVALID);
        var stage = stageMapper.selectByIdForUpdate(req.getStageId(), TenantContextHolder.getRequiredTenantId());
        Long operator = SecurityFrameworkUtils.getLoginUserId();
        if (stage == null || operator == null || !operator.equals(stage.getDirectorUserId())) throw exception(DEFER_PERMISSION_DENIED);
        // The stage row lock serializes requests and status events for the same deadline.
        var replay = deferMapper.selectOne(new LambdaQueryWrapper<StudentDeliveryDeferDO>().eq(StudentDeliveryDeferDO::getStageId, req.getStageId())
                .eq(StudentDeliveryDeferDO::getIdempotencyKey, req.getIdempotencyKey()));
        if (replay != null) {
            if (!java.util.Objects.equals(replay.getNewDueAt(), req.getNewDueAt()) || !java.util.Objects.equals(replay.getReason(), req.getReason().trim())) throw exception(DEFER_CONFLICT);
            return replay;
        }
        var plan=plans.selectById(stage.getPlanId());
        if(plan==null || !"ACTIVE".equals(plan.getStatus())) throw exception(CYCLE_INVALID);
        if (!req.getNewDueAt().isAfter(LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")))) throw exception(DEFER_DAYS_INVALID);
        if (!java.util.Objects.equals(stage.getVersion(), req.getVersion())) throw exception(DEFER_CONFLICT);
        var old = deferMapper.selectOne(new LambdaQueryWrapper<StudentDeliveryDeferDO>()
                .eq(StudentDeliveryDeferDO::getStageId, stage.getId()).eq(StudentDeliveryDeferDO::getStatus, "PENDING"));
        if (old != null) throw exception(DEFER_STAGE_INVALID);
        if (!("PENDING".equals(stage.getStatus()) || "OVERDUE".equals(stage.getStatus())) || stage.getDueAt() == null) throw exception(DEFER_STAGE_INVALID);
        LocalDateTime requestedDueAt = req.getNewDueAt();
        if (!requestedDueAt.isAfter(stage.getDueAt())) throw exception(DEFER_DAYS_INVALID);
        var director = adminUserApi.getUser(stage.getDirectorUserId());
        var dept = director == null || director.getDeptId() == null ? null : deptApi.getDept(director.getDeptId());
        Long supervisorId = dept == null ? null : dept.getLeaderUserId();
        var supervisor = supervisorId == null ? null : adminUserApi.getUser(supervisorId);
        if (director == null || !CommonStatusEnum.ENABLE.getStatus().equals(director.getStatus())
                || dept == null || !CommonStatusEnum.ENABLE.getStatus().equals(dept.getStatus())
                || supervisor == null || !CommonStatusEnum.ENABLE.getStatus().equals(supervisor.getStatus())) throw exception(DEFER_SUPERVISOR_INVALID);
        var row = new StudentDeliveryDeferDO().setStageId(stage.getId()).setRequestedBy(operator)
                .setSupervisorUserId(supervisorId).setRequestedDays((int) java.time.Duration.between(stage.getDueAt(), requestedDueAt).toDays())
                .setOriginalDueAt(stage.getDueAt()).setNewDueAt(requestedDueAt).setIdempotencyKey(req.getIdempotencyKey())
                .setReason(req.getReason().trim()).setStatus("EFFECTIVE").setDecidedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")));
        deferMapper.insert(row);
        if (stageMapper.update(null, new LambdaUpdateWrapper<StudentDeliveryStageDO>()
                .eq(StudentDeliveryStageDO::getId, stage.getId()).eq(StudentDeliveryStageDO::getVersion, req.getVersion())
                .set(StudentDeliveryStageDO::getStatus, "PENDING").set(StudentDeliveryStageDO::getDueAt, requestedDueAt)
                .set(StudentDeliveryStageDO::getVersion, req.getVersion()+1)) != 1) throw exception(DEFER_CONFLICT);
        tasks.updatePending("student_delivery_confirmation", stage.getId(), operator, "填写"+stage.getStageCode()+"期交付确认", "交付已延期", requestedDueAt, requestedDueAt);
        events.notify("student.delivery.deferred", "student_delivery_stage", stage.getId(), supervisorId, operator,
                "delivery-defer:" + row.getId(), Map.of("reason", row.getReason(), "changeSummary", "延期至 " + requestedDueAt,
                        "deepLink", "/zsjos/my-students", "bizNo", stage.getStageCode()));
        return row;
    }

    @Override
    @Transactional
    public void handleProcessResult(String processId, Integer status, String reason) {
        if (!BpmProcessInstanceStatusEnum.isProcessEndStatus(status)) return;
        var initial = deferMapper.selectByProcessInstanceId(processId);
        if (initial == null) return;
        var stage = stageMapper.selectByIdForUpdate(initial.getStageId(), TenantContextHolder.getRequiredTenantId());
        var row = deferMapper.selectByProcessInstanceId(processId);
        if (row == null || !"PENDING".equals(row.getStatus())) return;
        boolean approved = BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(status);
        boolean cancelled = BpmProcessInstanceStatusEnum.CANCEL.getStatus().equals(status);
        row.setStatus(approved ? "APPROVED" : cancelled ? "CANCELLED" : "REJECTED")
                .setDecisionReason(reason).setDecidedAt(LocalDateTime.now());
        if (stage == null || !"DEFER_PENDING".equals(stage.getStatus())) throw exception(DEFER_CONFLICT);
        LocalDateTime dueAt = approved ? row.getOriginalDueAt().plusDays(row.getRequestedDays()) : row.getOriginalDueAt();
        if (stageMapper.update(null, new LambdaUpdateWrapper<StudentDeliveryStageDO>()
                .eq(StudentDeliveryStageDO::getId, row.getStageId()).eq(StudentDeliveryStageDO::getStatus, "DEFER_PENDING")
                .set(StudentDeliveryStageDO::getStatus, "PENDING").set(StudentDeliveryStageDO::getDueAt, dueAt)) != 1) throw exception(DEFER_CONFLICT);
        deferMapper.updateById(row);
    }
}
