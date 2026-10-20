package cn.iocoder.yudao.module.hrm.service.employee.info;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.hrm.api.employee.event.HrmEmployeeLifecycleEventType;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.employee.HrmEmployeeSaveReqVO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.employment.HrmEmployeeChangeRecordDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.info.HrmEmployeeMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.employment.HrmEmployeeChangeRecordMapper;
import cn.iocoder.yudao.module.hrm.enums.employee.employment.HrmEmployeeChangeTypeEnum;
import cn.iocoder.yudao.module.hrm.enums.employee.info.HrmEmployeeStatusEnum;
import cn.iocoder.yudao.module.hrm.enums.employee.info.HrmEmployeeEntryStatusEnum;
import cn.iocoder.yudao.module.hrm.service.employee.api.HrmEmployeeLifecycleEventPublisher;
import cn.iocoder.yudao.module.hrm.service.recruit.candidate.HrmRecruitCandidateService;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** Runs inside the employee command transaction with the employee row locked. */
@Service
public class HrmEmployeeManualLifecycle {
    public static final ErrorCode CONFLICT = new ErrorCode(1_050_100_070, "员工或计划已被修改，请刷新核对后重试");
    public static final ErrorCode INVALID = new ErrorCode(1_050_100_071, "人事变更参数不合法：{}");
    public static final ErrorCode KEY_REUSED = new ErrorCode(1_050_100_072, "操作标识已用于其他请求，请刷新后重试");
    public static final ErrorCode PLAN_CLOSED = new ErrorCode(1_050_100_073, "计划已处理或取消，请刷新");
    public static final ErrorCode PLAN_CHANGED = new ErrorCode(1_050_100_074, "档案与计划原快照不同，请重新编辑计划并核对目标值");
    @Resource private HrmEmployeeMapper employees;
    @Resource private HrmEmployeeChangeRecordMapper records;
    @Resource private DictDataApi dictionaries;
    @Resource private HrmEmployeeLifecycleEventPublisher events;
    // 招聘服务会回调员工服务，延迟解析以保证其注入的是已完成包装的员工代理。
    @Resource @Lazy private HrmRecruitCandidateService candidates;

    public boolean replay(HrmEmployeeSaveReqVO request) {
        if (request.getOperationKey() == null || request.getOperationKey().isBlank()) throw exception(INVALID, "缺少操作标识");
        HrmEmployeeChangeRecordDO previous = records.selectOne(new LambdaQueryWrapperX<HrmEmployeeChangeRecordDO>()
                .eq(HrmEmployeeChangeRecordDO::getOperationKey, request.getOperationKey()));
        if (previous == null) return false;
        if (!Objects.equals(previous.getEmployeeId(), request.getId()) || !Objects.equals(previous.getRequestHash(), hash(request))) {
            throw exception(KEY_REUSED);
        }
        return true;
    }

    public void snapshotLabels(HrmEmployeeDO before, HrmEmployeeDO after) {
        after.setTypeLabel(label("hrm_employee_type", before == null ? null : before.getType(), after.getType(), before == null ? null : before.getTypeLabel()));
        after.setStatusLabel(label("hrm_employee_status", before == null ? null : before.getStatus(), after.getStatus(), before == null ? null : before.getStatusLabel()));
        after.setEntryStatusLabel(label("hrm_employee_entry_status", before == null ? null : before.getEntryStatus(), after.getEntryStatus(), before == null ? null : before.getEntryStatusLabel()));
    }

    private String label(String type, Integer oldValue, Integer value, String oldLabel) {
        if (Objects.equals(oldValue, value)) return oldLabel;
        if (value == null) return null;
        String code = value.toString();
        dictionaries.validateDictDataList(type, List.of(code));
        return dictionaries.getDictDataList(type).stream().filter(d -> code.equals(d.getValue()))
                .map(d -> d.getLabel()).findFirst().orElseThrow(() -> exception(INVALID, "字典选项不存在"));
    }

    public void write(HrmEmployeeSaveReqVO r, HrmEmployeeDO before, HrmEmployeeDO after) {
        if (!Objects.equals(r.getVersion(), before.getVersion())) throw exception(CONFLICT);
        String mode = r.getChangeMode() == null ? "CORRECTION" : r.getChangeMode();
        if (!Set.of("CORRECTION", "BUSINESS", "PLAN").contains(mode)) throw exception(INVALID, "修改性质");
        boolean changed = !HrmEmployeeLifecycleValues.of(before).equals(HrmEmployeeLifecycleValues.of(after))
                || !Objects.equals(before.getPlannedRegularTime(), after.getPlannedRegularTime())
                || !Objects.equals(before.getPlannedLeaveTime(), after.getPlannedLeaveTime());
        if ((changed || !mode.equals("CORRECTION")) && (r.getChangeReason() == null || r.getChangeReason().isBlank())) {
            throw exception(INVALID, "请填写修改原因");
        }
        if (!mode.equals("CORRECTION") && (r.getChangeType() == null || HrmEmployeeChangeTypeEnum.valueOf(r.getChangeType()) == null)) {
            throw exception(INVALID, "请选择变更类别");
        }
        if (mode.equals("BUSINESS")) validateBusiness(r, before, after);
        if (!mode.equals("PLAN") && ((after.getActualTrialEndTime() != null && after.getActualTrialEndTime().isAfter(LocalDateTime.now()))
                || (after.getRegularTime() != null && after.getRegularTime().isAfter(LocalDateTime.now()))
                || (after.getLeaveTime() != null && after.getLeaveTime().isAfter(LocalDateTime.now())))) {
            throw exception(INVALID, "实际日期不能在未来，请填写计划日期");
        }
        if (after.getStatus() == null || !(Objects.equals(after.getType(), 1)
                ? HrmEmployeeStatusEnum.FULL_TIME_STATUSES : HrmEmployeeStatusEnum.INFORMAL_STATUSES).contains(after.getStatus())) throw exception(INVALID, "员工状态与聘用形式不匹配");
        snapshotLabels(before, after);
        HrmEmployeeChangeRecordDO linked = null;
        if (r.getPlanId() != null) {
            linked = plan(r.getPlanId(), before.getId(), r.getPlanVersion());
            if (!mode.equals("PLAN") && (linked.getBeforeSnapshot() == null || !Objects.equals(linked.getBeforeSnapshot(), snapshot(before)))) throw exception(PLAN_CHANGED);
            if (!Objects.equals(linked.getType(), r.getChangeType())) throw exception(INVALID, "计划与变更类别不一致");
        }
        LocalDateTime target = r.getActualTime();
        if (mode.equals("PLAN")) {
            target = r.getChangeType() == 4 ? r.getPlannedRegularTime()
                    : r.getChangeType() == 11 ? r.getPlannedLeaveTime() : r.getPlannedTime();
            if (target == null) throw exception(INVALID, "请填写计划日期");
            if (r.getChangeType() == 4 || r.getChangeType() == 11) {
                final Long linkedId = linked == null ? null : linked.getId();
                boolean duplicate = records.selectList(new LambdaQueryWrapperX<HrmEmployeeChangeRecordDO>()
                        .eq(HrmEmployeeChangeRecordDO::getEmployeeId, before.getId()).eq(HrmEmployeeChangeRecordDO::getState, "PENDING")
                        .eq(HrmEmployeeChangeRecordDO::getType, r.getChangeType())).stream().anyMatch(p -> !Objects.equals(p.getId(), linkedId));
                if (duplicate) throw exception(INVALID, "已有同类计划，请编辑现有计划");
            }
        }
        HrmEmployeeChangeRecordDO record = new HrmEmployeeChangeRecordDO().setEmployeeId(before.getId())
                .setType(r.getChangeType() == null ? 12 : r.getChangeType()).setChangeMode(mode)
                .setState(mode.equals("PLAN") ? "PENDING" : "APPLIED").setVersion(0L)
                .setOperationKey(r.getOperationKey()).setRequestHash(hash(r)).setRemark(r.getChangeReason())
                .setBeforeSnapshot(snapshot(before)).setAfterSnapshot(snapshot(after))
                .setOldDeptId(before.getDeptId()).setNewDeptId(after.getDeptId())
                .setOldPostName(before.getPostName()).setNewPostName(after.getPostName())
                .setOldPostLevel(before.getPostLevel()).setNewPostLevel(after.getPostLevel())
                .setOldWorkAddress(before.getWorkAddress()).setNewWorkAddress(after.getWorkAddress())
                .setOldLeaderEmployeeId(before.getLeaderEmployeeId()).setNewLeaderEmployeeId(after.getLeaderEmployeeId())
                .setProbation(after.getProbation()).setEffectTime(target == null ? LocalDateTime.now() : target)
                .setPlannedTime(mode.equals("PLAN") ? target : null).setActualTime(mode.equals("BUSINESS") ? target : null)
                .setAppliedTime(mode.equals("PLAN") ? null : LocalDateTime.now());
        if (mode.equals("PLAN")) {
            // Saving a plan changes its scheduling fields only, never the proposed employee values.
            HrmEmployeeDO scheduled = BeanUtils.toBean(before, HrmEmployeeDO.class);
            if (r.getChangeType() == 4) scheduled.setPlannedRegularTime(target);
            if (r.getChangeType() == 11) scheduled.setPlannedLeaveTime(target);
            persist(scheduled, before.getVersion());
        } else {
            if (mode.equals("BUSINESS") && r.getChangeType() == 4) after.setPlannedRegularTime(null);
            if (mode.equals("BUSINESS") && r.getChangeType() == 11) after.setPlannedLeaveTime(null);
            persist(after, before.getVersion());
        }
        // Every request keeps an immutable receipt; plan revisions close the former revision.
        if (linked != null) records.updateById(new HrmEmployeeChangeRecordDO().setId(linked.getId())
                .setState(mode.equals("PLAN") ? "SUPERSEDED" : "CONFIRMED").setVersion(linked.getVersion() + 1));
        records.insert(record);
        if (!mode.equals("PLAN")) syncPlanDates(after);
        if (mode.equals("BUSINESS")) publish(r, record.getId(), before, after);
    }

    public void persist(HrmEmployeeDO after, Long version) {
        after.setVersion(version + 1);
        int count = employees.update(after, new LambdaUpdateWrapper<HrmEmployeeDO>()
                .eq(HrmEmployeeDO::getId, after.getId()).eq(HrmEmployeeDO::getVersion, version)
                .set(after.getPlannedRegularTime() == null, HrmEmployeeDO::getPlannedRegularTime, null)
                .set(after.getPlannedLeaveTime() == null, HrmEmployeeDO::getPlannedLeaveTime, null)
                .set(after.getRegularTime() == null, HrmEmployeeDO::getRegularTime, null).set(after.getLeaveTime() == null, HrmEmployeeDO::getLeaveTime, null)
                .set(after.getTrialDays() == null, HrmEmployeeDO::getTrialDays, null)
                .set(after.getPlannedTrialEndTime() == null, HrmEmployeeDO::getPlannedTrialEndTime, null)
                .set(after.getActualTrialEndTime() == null, HrmEmployeeDO::getActualTrialEndTime, null)
                .set(after.getProbation() == null, HrmEmployeeDO::getProbation, null)
                .set(after.getDeptId() == null, HrmEmployeeDO::getDeptId, null)
                .set(after.getLeaderEmployeeId() == null, HrmEmployeeDO::getLeaderEmployeeId, null)
                .set(after.getPostName() == null, HrmEmployeeDO::getPostName, null)
                .set(after.getPostLevel() == null, HrmEmployeeDO::getPostLevel, null)
                .set(after.getWorkAddress() == null, HrmEmployeeDO::getWorkAddress, null)
                .set(after.getTypeLabel() == null, HrmEmployeeDO::getTypeLabel, null)
                .set(after.getStatusLabel() == null, HrmEmployeeDO::getStatusLabel, null)
                .set(after.getEntryStatusLabel() == null, HrmEmployeeDO::getEntryStatusLabel, null));
        if (count != 1) throw exception(CONFLICT);
    }

    public HrmEmployeeChangeRecordDO plan(Long id, Long employeeId, Long version) {
        HrmEmployeeChangeRecordDO p = records.selectOneForUpdate(HrmEmployeeChangeRecordDO::getId, id);
        if (p == null || !Objects.equals(p.getEmployeeId(), employeeId)) throw exception(INVALID, "计划不存在");
        if (!"PENDING".equals(p.getState())) throw exception(PLAN_CLOSED);
        if (!Objects.equals(p.getVersion(), version)) throw exception(CONFLICT);
        return p;
    }

    public void syncPlanDates(HrmEmployeeDO e) {
        for (HrmEmployeeChangeRecordDO p : records.selectList(new LambdaQueryWrapperX<HrmEmployeeChangeRecordDO>()
                .eq(HrmEmployeeChangeRecordDO::getEmployeeId, e.getId()).eq(HrmEmployeeChangeRecordDO::getState, "PENDING"))) {
            if (p.getType() != 4 && p.getType() != 11) continue;
            LocalDateTime date = p.getType() == 4 ? e.getPlannedRegularTime() : e.getPlannedLeaveTime();
            boolean eligible = p.getType() == 4 ? Objects.equals(e.getStatus(), 2) && Set.of(1, 3).contains(e.getEntryStatus())
                    : Set.of(1, 3).contains(e.getEntryStatus());
            if (eligible && date != null && Objects.equals(date, p.getPlannedTime())) continue;
            records.update(null, new LambdaUpdateWrapper<HrmEmployeeChangeRecordDO>().eq(HrmEmployeeChangeRecordDO::getId, p.getId())
                    .set(HrmEmployeeChangeRecordDO::getPlannedTime, date).set(HrmEmployeeChangeRecordDO::getVersion, p.getVersion() + 1)
                    .set(HrmEmployeeChangeRecordDO::getState, date == null || !eligible ? "CANCELLED" : "PENDING"));
        }
        for (int type : List.of(4, 11)) {
            LocalDateTime date = type == 4 ? e.getPlannedRegularTime() : e.getPlannedLeaveTime();
            if (date == null || !Set.of(1,3).contains(e.getEntryStatus()) || (type == 4 && !Objects.equals(e.getStatus(),2))) continue;
            boolean exists = !records.selectList(new LambdaQueryWrapperX<HrmEmployeeChangeRecordDO>()
                    .eq(HrmEmployeeChangeRecordDO::getEmployeeId,e.getId()).eq(HrmEmployeeChangeRecordDO::getType,type)
                    .eq(HrmEmployeeChangeRecordDO::getState,"PENDING")).isEmpty();
            if (exists) continue;
            HrmEmployeeDO target = BeanUtils.toBean(e,HrmEmployeeDO.class);
            if (type == 4) target.setStatus(1); else target.setEntryStatus(4);
            records.insert(new HrmEmployeeChangeRecordDO().setEmployeeId(e.getId()).setType(type)
                    .setState("PENDING").setVersion(0L).setChangeMode("PLAN").setPlannedTime(date).setEffectTime(date)
                    .setBeforeSnapshot(snapshot(e)).setAfterSnapshot(snapshot(target)).setRemark("员工档案计划日期"));
        }
    }

    private void validateBusiness(HrmEmployeeSaveReqVO r, HrmEmployeeDO before, HrmEmployeeDO after) {
        if (r.getActualTime() == null || r.getActualTime().isAfter(LocalDateTime.now())) throw exception(INVALID, "实际发生日期不能缺失或在未来");
        if (Set.of(4, 5, 6, 7, 8).contains(r.getChangeType())
                && !HrmEmployeeEntryStatusEnum.ACTIVE_STATUSES.contains(before.getEntryStatus())) {
            throw exception(INVALID, "员工当前不在职，不能执行该人事变更");
        }
        switch (r.getChangeType()) {
            case 4 -> { if (!Objects.equals(before.getStatus(), 2) || !Objects.equals(after.getStatus(), 1)
                    || !Objects.equals(after.getType(), 1)) throw exception(INVALID, "转正应从试用变为正式"); after.setRegularTime(r.getActualTime()); }
            case 8 -> { if (!Objects.equals(before.getType(), 2) || !Objects.equals(after.getType(), 1)) throw exception(INVALID, "转全职应从非正式聘用变为正式聘用"); }
            case 9 -> { if (!Objects.equals(before.getEntryStatus(), 4) || !Objects.equals(after.getEntryStatus(), 1)) throw exception(INVALID, "再入职应从离职变为在职"); }
            case 10 -> { if (!Objects.equals(before.getEntryStatus(), 2) || !Objects.equals(after.getEntryStatus(), 1)) throw exception(INVALID, "入职应从待入职变为在职"); }
            case 11 -> { if (!Set.of(1, 3).contains(before.getEntryStatus()) || !Objects.equals(after.getEntryStatus(), 4)) throw exception(INVALID, "离职应从在职或待离职变为离职"); after.setLeaveTime(r.getActualTime()); }
            case 13 -> { if (!Objects.equals(before.getEntryStatus(), 3) || !Objects.equals(after.getEntryStatus(), 1)) throw exception(INVALID, "取消离职应从待离职变为在职"); after.setPlannedLeaveTime(null); }
            default -> { }
        }
        if (after.getStatus() == null || !(Objects.equals(after.getType(), 1)
                ? HrmEmployeeStatusEnum.FULL_TIME_STATUSES : HrmEmployeeStatusEnum.INFORMAL_STATUSES).contains(after.getStatus())) throw exception(INVALID, "员工状态与聘用形式不匹配");
    }

    private void publish(HrmEmployeeSaveReqVO r, Long receiptId, HrmEmployeeDO before, HrmEmployeeDO after) {
        HrmEmployeeLifecycleEventType type = switch (r.getChangeType()) {
            case 9 -> HrmEmployeeLifecycleEventType.REHIRED;
            case 10 -> HrmEmployeeLifecycleEventType.ENTRY_CONFIRMED;
            case 11 -> HrmEmployeeLifecycleEventType.LEFT;
            case 13 -> HrmEmployeeLifecycleEventType.QUIT_CANCELLED;
            default -> Objects.equals(after.getEntryStatus(), 3) && !Objects.equals(before.getEntryStatus(), 3)
                    ? HrmEmployeeLifecycleEventType.QUIT_PLANNED : HrmEmployeeLifecycleEventType.CHANGE_EFFECTIVE;
        };
        if ((type == HrmEmployeeLifecycleEventType.ENTRY_CONFIRMED || type == HrmEmployeeLifecycleEventType.REHIRED)
                && after.getCandidateId() != null) candidates.confirmRecruitCandidateEntry(after.getCandidateId(), after.getEntryTime());
        events.publish(type, receiptId, before, after.getId());
    }

    public static String snapshot(HrmEmployeeDO e) { return JsonUtils.toJsonString(HrmEmployeeLifecycleValues.of(e)); }
    private static String hash(HrmEmployeeSaveReqVO r) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((r.getCommandRequestHash() == null ? JsonUtils.toJsonString(r) : r.getCommandRequestHash()).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
