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
    @Resource private StudentDeliveryStageMapper stageMapper;
    @Resource private BpmProcessInstanceApi processInstanceApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;

    @Override
    @Transactional
    @ZsjosPermission(bizType = StudentDeliveryStagePermissionProvider.BIZ_TYPE, bizId = "#req.stageId", action = "defer")
    public StudentDeliveryDeferDO request(StudentDeliveryDeferReqVO req) {
        if (req.getRequestedDays() == null || req.getRequestedDays() < 1) throw exception(DEFER_DAYS_INVALID);
        if (req.getReason() == null || req.getReason().isBlank() || req.getReason().length() > 1000) throw exception(DEFER_REASON_INVALID);
        var stage = stageMapper.selectByIdForUpdate(req.getStageId(), TenantContextHolder.getRequiredTenantId());
        Long operator = SecurityFrameworkUtils.getLoginUserId();
        if (stage == null || operator == null || !operator.equals(stage.getDirectorUserId())) throw exception(DEFER_PERMISSION_DENIED);
        // The stage row lock serializes requests and status events for the same deadline.
        var old = deferMapper.selectOne(new LambdaQueryWrapper<StudentDeliveryDeferDO>()
                .eq(StudentDeliveryDeferDO::getStageId, stage.getId()).eq(StudentDeliveryDeferDO::getStatus, "PENDING"));
        if (old != null) return old;
        if (!("PENDING".equals(stage.getStatus()) || "OVERDUE".equals(stage.getStatus())) || stage.getDueAt() == null) throw exception(DEFER_STAGE_INVALID);
        LocalDateTime requestedDueAt;
        try { requestedDueAt = stage.getDueAt().plusDays(req.getRequestedDays()); }
        catch (DateTimeException cause) { throw exception(DEFER_DAYS_INVALID); }
        // MySQL DATETIME has a physical year range; there is no business-day upper limit.
        if (requestedDueAt.getYear() > 9999) throw exception(DEFER_DAYS_INVALID);
        var director = adminUserApi.getUser(stage.getDirectorUserId());
        var dept = director == null || director.getDeptId() == null ? null : deptApi.getDept(director.getDeptId());
        Long supervisorId = dept == null ? null : dept.getLeaderUserId();
        var supervisor = supervisorId == null ? null : adminUserApi.getUser(supervisorId);
        if (director == null || !CommonStatusEnum.ENABLE.getStatus().equals(director.getStatus())
                || dept == null || !CommonStatusEnum.ENABLE.getStatus().equals(dept.getStatus())
                || supervisor == null || !CommonStatusEnum.ENABLE.getStatus().equals(supervisor.getStatus())) throw exception(DEFER_SUPERVISOR_INVALID);
        var row = new StudentDeliveryDeferDO().setStageId(stage.getId()).setRequestedBy(operator)
                .setSupervisorUserId(supervisorId).setRequestedDays(req.getRequestedDays())
                .setOriginalDueAt(stage.getDueAt()).setReason(req.getReason().trim()).setStatus("PENDING");
        deferMapper.insert(row);
        if (stageMapper.update(null, new LambdaUpdateWrapper<StudentDeliveryStageDO>()
                .eq(StudentDeliveryStageDO::getId, stage.getId()).eq(StudentDeliveryStageDO::getStatus, stage.getStatus())
                .set(StudentDeliveryStageDO::getStatus, "DEFER_PENDING")) != 1) throw exception(DEFER_CONFLICT);
        var process = new BpmProcessInstanceCreateReqDTO();
        process.setProcessDefinitionKey("zsjos_student_contact_extension");
        process.setBusinessKey("student-delivery-defer:" + row.getId());
        process.setVariables(new HashMap<>(Map.of("extensionId", row.getId(), "stageId", stage.getId(),
                "requestedDays", row.getRequestedDays(), "reason", row.getReason(),
                "originalDueAt", row.getOriginalDueAt().toString(), "requestedDueAt", requestedDueAt.toString(),
                "stageCode", stage.getStageCode())));
        process.getVariables().put("description", "账号交付延期 · " + stage.getStageCode() + "：" + row.getReason());
        process.getVariables().put("applicantUserId", operator);
        process.getVariables().put("submittedAt", LocalDateTime.now().toString());
        process.setStartUserSelectAssignees(Map.of("deliverySupervisorReview", List.of(supervisorId)));
        row.setBpmProcessInstanceId(processInstanceApi.createProcessInstance(operator, process));
        deferMapper.updateById(row);
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
