package cn.iocoder.yudao.module.zsjos.service.delivery;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountDetailSnapshotVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class DeliveryPositioningSyncService {
    @Resource private MediaAccountMapper accounts;
    @Resource private MediaAccountProfileEntryMapper entries;
    @Resource private DeliveryPositioningSource sources;
    @Resource private StudentDeliveryPlanMapper plans;
    @Resource private StudentDeliveryPlanService planService;

    @Transactional(rollbackFor=Exception.class)
    public void syncService(Long studentId, Long relationId) {
        if (studentId == null || relationId == null) return;
        for (var account : accounts.selectList(new LambdaQueryWrapper<MediaAccountDO>()
                .eq(MediaAccountDO::getStudentPersonId, studentId).eq(MediaAccountDO::getCreateServiceRelationId, relationId))) syncAccount(account.getId());
    }

    @Transactional(rollbackFor=Exception.class)
    public void syncAccount(Long accountId) {
        var account = accounts.selectByIdForUpdate(accountId, TenantContextHolder.getRequiredTenantId());
        if (account == null) return;
        var source = sources.latest(account);
        if (source == null) return;
        var latest = plans.selectOne(new LambdaQueryWrapper<StudentDeliveryPlanDO>().eq(StudentDeliveryPlanDO::getAccountId, accountId).orderByDesc(StudentDeliveryPlanDO::getId).last("LIMIT 1"));
        String key = "diagnosis-source-v1:" + accountId + ":" + source.getId();
        if (DeliveryPositioningSource.effectiveAt(source) != null && entries.replay(accountId, 0L, key) == null) {
            var values = DeliveryPositioningSource.parse(account.getDetailValuesJson());
            var context = DeliveryPositioningSource.parse(values.get("_diagnosisContext") instanceof String json ? json : null);
            boolean restart = latest != null && "REPOSITIONING".equals(latest.getStatus())
                    && !Objects.equals(latest.getSourceSubmissionId(), source.getId()) && latest.getRestartRequestedAt() != null
                    && source.getSubmittedAt() != null && !source.getSubmittedAt().isBefore(latest.getRestartRequestedAt());
            if (context.get("anchorAt") == null || restart) {
                var first = restart ? source : sources.firstProven(account);
                var anchor = DeliveryPositioningSource.effectiveAt(first);
                if (anchor != null) {
                    if (account.getCreateTime() != null && anchor.isBefore(account.getCreateTime())) anchor = account.getCreateTime();
                    context.put("anchorAt", anchor.toString()); context.put("roundKey", first.getId());
                }
            }
            context.put("submissionId", source.getId()); context.put("submissionNo", source.getSubmissionNo());
            context.put("syncedAt", LocalDateTime.now(ZoneId.of("Asia/Shanghai")).toString());
            context.put("effectiveAt", DeliveryPositioningSource.effectiveAt(source).toString());
            values.put("_diagnosisContext", JsonUtils.toJsonString(context));
            var sourceValues = DeliveryPositioningSource.parse(source.getValuesSnapshotJson());
            var snapshots = new ArrayList<>(account.getDetailSnapshotJson() == null ? List.<MediaAccountDetailSnapshotVO>of()
                    : JsonUtils.parseArray(account.getDetailSnapshotJson(), MediaAccountDetailSnapshotVO.class));
            // The pre-overwrite snapshot preserves manual edits even when the source clears a value.
            var before = JsonUtils.toJsonString(snapshots);
            var mapping = new LinkedHashMap<String, String>();
            mapping.put("diagnosis_7d_requirement", "pc_days7");
            mapping.put("diagnosis_14d_requirement", "pc_days14");
            mapping.put("diagnosis_28d_requirement", "pc_days28");
            mapping.put("student_commitments", "pc_student_duties");
            mapping.put("company_commitments", "pc_company_duties");
            mapping.put("delivery_goals", "pc_internal_goal");
            mapping.forEach((target, from) -> {
                Object value = sourceValues.get(from);
                values.remove(target); if (value != null) values.put(target, value);
                snapshots.removeIf(s -> target.equals(s.getKey()));
                if (value != null) snapshots.add(new MediaAccountDetailSnapshotVO().setKey(target).setLabel(switch(target) {
                    case "diagnosis_7d_requirement" -> "7天账号数据诊断";
                    case "diagnosis_14d_requirement" -> "14天验证指标诊断";
                    case "diagnosis_28d_requirement" -> "28天调整触发条件";
                    case "student_commitments" -> "学员承担事项";
                    case "company_commitments" -> "公司承担事项";
                    default -> "内部交付目标约定";
                }).setType("textarea").setValue(value).setDisplayValue(String.valueOf(value)));
            });
            account.setDetailValuesJson(JsonUtils.toJsonString(values)).setDetailSnapshotJson(JsonUtils.toJsonString(snapshots)).setVersion(account.getVersion()+1);
            accounts.updateById(account);
            var audit = new MediaAccountProfileEntryDO();
            audit.setAccountId(accountId); audit.setOperatedByUserId(0L); audit.setOperatedByName("系统"); audit.setKind("PROFILE");
            audit.setTitle("定位卡生效：更新诊断要求、承担事项与交付目标"); audit.setContent("来源提交：" + source.getSubmissionNo() + "；以下保留覆盖前资料快照");
            audit.setSnapshotJson(before); audit.setFilesJson("[]"); audit.setIdempotencyKey(key); audit.setFingerprint(key); audit.setResultVersion(account.getVersion()); entries.insert(audit);
        }

        if (latest != null && "REPOSITIONING".equals(latest.getStatus())) {
            // Appending evidence to the old version cannot restart a closed round.
            if (Objects.equals(latest.getSourceSubmissionId(), source.getId()) || source.getSubmittedAt() == null
                    || latest.getRestartRequestedAt() == null || source.getSubmittedAt().isBefore(latest.getRestartRequestedAt())) return;
            var evidence = cn.iocoder.yudao.module.zsjos.service.positioning.PositioningEvidenceService.evidence(source);
            LocalDateTime effectiveAt = evidence.stream().map(e -> e.uploadedAt()).min(LocalDateTime::compareTo).orElse(source.getStudentDecidedAt());
            if (effectiveAt == null) return;
            latest.setStatus("CLOSED"); plans.updateById(latest);
            var next = planService.ensurePlan(account.getStudentPersonId(), accountId, account.getCreateServiceRelationId(), account.getDirectorUserId(), effectiveAt);
            next.setRoundNo(Objects.requireNonNullElse(latest.getRoundNo(),1)+1).setSourceSubmissionId(source.getId()); plans.updateById(next);
        } else if (latest == null && account.getCreateTime() != null) {
            var plan = planService.ensurePlan(account.getStudentPersonId(), accountId, account.getCreateServiceRelationId(), account.getDirectorUserId(), account.getCreateTime());
            plan.setRoundNo(1).setSourceSubmissionId(source.getId()); plans.updateById(plan);
        } else if (latest != null && "ACTIVE".equals(latest.getStatus())) {
            latest.setSourceSubmissionId(source.getId()); plans.updateById(latest);
        }
    }
}
