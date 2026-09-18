package cn.iocoder.yudao.module.zsjos.service.delivery;

import cn.iocoder.yudao.module.zsjos.controller.admin.delivery.vo.StudentDeliveryPlanRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountObjectPermissionProvider;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class StudentDeliveryOverviewService {
    @Resource private StudentDeliveryCycleService cycles;
    @Resource private StudentDeliveryPlanService planService;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardSubmissionMapper positioning;
    @Resource private MediaAccountMapper accounts;
    @Resource private StudentDeliveryPlanMapper plans;
    @Resource private StudentDeliveryStageMapper stages;
    @Resource private StudentDeliverySubmissionMapper submissions;
    @Resource private StudentDeliveryDeferMapper defers;
    @Resource private DeliveryPositioningSource sources;
    @Resource private AdminUserApi users;
    @Resource private DeptApi departments;
    @Resource private PermissionApi permissions;
    @Resource private MediaAccountObjectPermissionProvider objects;

    @ZsjosPermission(bizType="media-account",bizId="#accountId",action="read")
    public StudentDeliveryPlanRespVO get(Long accountId, Long userId, Long planId) {
        var account = accounts.selectById(accountId);
        if (account == null) throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MEDIA_ACCOUNT_NOT_EXISTS);
        var plan = plans.selectOne(new LambdaQueryWrapper<StudentDeliveryPlanDO>().eq(StudentDeliveryPlanDO::getAccountId,accountId)
                .eq(planId != null, StudentDeliveryPlanDO::getId,planId).orderByDesc(StudentDeliveryPlanDO::getId).last("LIMIT 1"));
        var vo = new StudentDeliveryPlanRespVO(); vo.setAccountId(accountId);
        var source = sources.latest(account);
        if (plan != null && !"ACTIVE".equals(plan.getStatus())) {
            source = plan.getSourceSubmissionId() == null ? null : positioning.selectById(plan.getSourceSubmissionId());
            if (source != null && (!Objects.equals(source.getStudentPersonId(),account.getStudentPersonId()) || !Objects.equals(source.getServiceRelationId(),account.getCreateServiceRelationId()))) source = null;
        }
        final var sourceSnapshot = source;
        vo.setSourceAvailable(source != null); vo.setWeeklyLeads(cycles.weeklyLeads(accountId));
        if (plan == null) { vo.setStatus("WAITING_SOURCE"); vo.setStages(new ArrayList<>()); addAgreements(vo,source); return vo; }
        vo.setId(plan.getId()); vo.setStatus(plan.getStatus()); vo.setAccountOpenedAt(plan.getAccountOpenedAt()); vo.setRoundNo(Objects.requireNonNullElse(plan.getRoundNo(),1));
        vo.setRounds(plans.selectList(new LambdaQueryWrapper<StudentDeliveryPlanDO>().eq(StudentDeliveryPlanDO::getAccountId,accountId).orderByDesc(StudentDeliveryPlanDO::getId))
                .stream().map(p -> new StudentDeliveryPlanRespVO.Round(p.getId(),p.getRoundNo(),p.getStatus())).toList());
        var director = users.getUser(account.getDirectorUserId());
        var dept = director == null || director.getDeptId() == null ? null : departments.getDept(director.getDeptId());
        var supervisor = dept == null || dept.getLeaderUserId() == null ? null : users.getUser(dept.getLeaderUserId());
        vo.setNotificationRecipient(supervisor == null ? null : supervisor.getNickname());
        boolean writable = "ACTIVE".equals(plan.getStatus()) && Objects.equals(account.getDirectorUserId(), userId) && objects.hasPermission(accountId,"edit",userId);
        vo.setStages(stages.selectList(new LambdaQueryWrapper<StudentDeliveryStageDO>().eq(StudentDeliveryStageDO::getPlanId,plan.getId()).orderByAsc(StudentDeliveryStageDO::getStageCode)).stream().map(stage -> {
            var item = new StudentDeliveryPlanRespVO.Stage(); item.setId(stage.getId()); item.setStageCode(stage.getStageCode()); item.setStatus(stage.getStatus()); item.setVersion(stage.getVersion());
            item.setTriggerAt(stage.getTriggerAt()); item.setDueAt(stage.getDueAt()); item.setCompletedAt(stage.getCompletedAt());
            var completed = submissions.selectOne(new LambdaQueryWrapper<StudentDeliverySubmissionDO>().eq(StudentDeliverySubmissionDO::getStageId,stage.getId()).last("LIMIT 1"));
            item.setAgreement(completed != null ? DeliveryPositioningSource.parse(completed.getAttachmentSnapshotJson()) : sources.snapshot(sourceSnapshot,stage.getStageCode()));
            if (completed != null) { item.setConfirmation(DeliveryPositioningSource.parse(completed.getFieldValuesJson())); var user = users.getUser(completed.getSubmittedBy()); item.setCompletedByName(user == null ? null : user.getNickname()); }
            item.setDefers(defers.selectList(new LambdaQueryWrapper<StudentDeliveryDeferDO>().eq(StudentDeliveryDeferDO::getStageId,stage.getId()).orderByDesc(StudentDeliveryDeferDO::getId)).stream().map(d -> new StudentDeliveryPlanRespVO.Defer(d.getId(),d.getReason(),d.getOriginalDueAt(),d.getNewDueAt(),d.getStatus(),d.getCreateTime())).toList());
            boolean pending = Set.of("PENDING","OVERDUE").contains(stage.getStatus());
            item.setCanSubmit(writable && pending && sourceSnapshot != null && item.getAgreement().get("agreement") != null && permissions.hasAnyPermissions(userId,"zsjos:student-delivery:submit"));
            item.setCanDefer(writable && pending && permissions.hasAnyPermissions(userId,"zsjos:student-delivery:defer"));
            item.setCanReposition(writable && "MONITORING".equals(stage.getStatus()) && vo.getWeeklyLeads()!=null && vo.getWeeklyLeads()<5 && permissions.hasAnyPermissions(userId,"zsjos:student-delivery:reposition") && permissions.hasAnyPermissions(userId,"zsjos:positioning-card:edit"));
            return item;
        }).toList());
        addAgreements(vo,source);
        return vo;
    }
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    @ZsjosPermission(bizType="media-account",bizId="#accountId",action="edit")
    public void ensure(Long accountId, Long userId) {
        var account=accounts.selectById(accountId);
        if(account==null || !Objects.equals(account.getDirectorUserId(),userId)) throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(StudentDeliveryErrors.DEFER_PERMISSION_DENIED);
        if(plans.selectCount(new LambdaQueryWrapper<StudentDeliveryPlanDO>().eq(StudentDeliveryPlanDO::getAccountId,accountId))>0) return;
        if(account.getCreateTime()==null || account.getStudentPersonId()==null || account.getCreateServiceRelationId()==null) throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(StudentDeliveryErrors.CYCLE_INVALID);
        planService.ensurePlan(account.getStudentPersonId(),accountId,account.getCreateServiceRelationId(),account.getDirectorUserId(),account.getCreateTime());
    }
    private void addAgreements(StudentDeliveryPlanRespVO vo, cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardSubmissionDO source) {
        var all = new ArrayList<>(vo.getStages());
        for (String code : StudentDeliverySchedule.STAGES) if (all.stream().noneMatch(s -> code.equals(s.getStageCode()))) {
            var pending = new StudentDeliveryPlanRespVO.Stage(); pending.setStageCode(code); pending.setStatus("WAITING_PREDECESSOR");
            pending.setAgreement(sources.snapshot(source,code)); pending.setDefers(List.of()); all.add(pending);
        }
        all.sort(java.util.Comparator.comparing(StudentDeliveryPlanRespVO.Stage::getStageCode)); vo.setStages(all);
    }
}
