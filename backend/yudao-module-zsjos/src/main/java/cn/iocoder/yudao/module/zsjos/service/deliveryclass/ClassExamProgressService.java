package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.ClassExamProgressVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactReadPolicy;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.deliveryclass.ClassExamProgressPolicy.*;

@Service
public class ClassExamProgressService {
    public static final String OUTCOME_DICT = "zsjos_exam_outcome";
    public static final String PERMISSION = "zsjos:delivery-class:complete";
    @Resource private DeliveryClassMapper classes;
    @Resource private ServiceRelationMapper relations;
    @Resource private ClassExamProgressMapper progress;
    @Resource private DictDataApi dictionaries;
    @Resource private PermissionApi permissions;
    @Resource private DeliveryClassObjectPermissionProvider objects;

    private Long tenant() { return TenantContextHolder.getRequiredTenantId(); }
    private List<ServiceRelationDO> lock(Long id) {
        var row = classes.selectByIdForUpdate(id, tenant());
        if (row == null) throw exception(DELIVERY_CLASS_NOT_EXISTS);
        if (Boolean.TRUE.equals(row.getSystemClass()) || !DeliveryClassKind.NORMAL.equals(row.getClassKind()))
            throw exception(DELIVERY_CLASS_STATE_INVALID);
        return relations.selectClassRelationsForUpdate(id, tenant());
    }
    private View view(Long id, Long userId, List<ServiceRelationDO> rows) {
        var state = progress.get(id, tenant());
        String stage = state == null ? STUDY : state.stage();
        var students = rows.stream().map(r -> {
            var p = progress.member(r.getId(), tenant());
            String s = p == null ? STUDY : p.stage();
            return new Student(r.getId(), r.getStudentNameSnapshot(), s,
                    STUDY.equals(s) ? StudentContactReadPolicy.stageLabel(r.getDeliveryStage()) : label(s),
                    p == null ? null : p.outcome(), p == null ? null : p.outcomeLabel(),
                    "supervision".equals(r.getDeliveryStage()));
        }).toList();
        boolean canChange = permissions.hasAnyPermissions(userId, PERMISSION) && objects.hasPermission(id, "complete", userId);
        var options = dictionaries.getDictDataList(OUTCOME_DICT).stream()
                .filter(d -> Integer.valueOf(0).equals(d.getStatus())).map(d -> new Option(d.getValue(), d.getLabel())).toList();
        return new View(stage, label(stage), state == null ? 0 : state.version(), canChange ? actions(stage) : List.of(), students, options);
    }

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "delivery-class", bizId = "#id", action = "read")
    public View get(Long id, Long userId) { return view(id, userId, lock(id)); }

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "delivery-class", bizId = "#id", action = "complete")
    @ZsjosAudit(action = "delivery-class.exam-progress", targetType = "delivery-class")
    public Result transition(Long id, Long userId, Command command) {
        var rows = lock(id);
        var current = view(id, userId, rows);
        if (!Objects.equals(current.version(), command.version())) throw exception(DELIVERY_CLASS_VERSION_CONFLICT);
        String target = target(current.stage(), command.action());
        if (target == null || !current.actions().contains(command.action())) throw exception(DELIVERY_CLASS_STATE_INVALID);
        if (rows.isEmpty()) return new Result(false, "空班级无法推进或回退考试进度", current, List.of());
        var blockers = current.students().stream().filter(s -> switch (command.action()) {
            case "SPRINT" -> !s.supervisionReady() || !STUDY.equals(s.stage());
            case "EXAM" -> !SPRINT.equals(s.stage());
            case "END" -> !EXAM.equals(s.stage()) || s.outcome() == null;
            case "BACK" -> !ENDED.equals(current.stage()) && (s.outcome() != null || !current.stage().equals(s.stage()));
            default -> true;
        }).toList();
        if (!blockers.isEmpty()) return new Result(false, switch (command.action()) {
            case "SPRINT" -> "以下学员尚未进入常规督学或考试进度不一致，无法进入考前冲刺";
            case "EXAM" -> "以下学员尚未进入考前冲刺，无法完成考试";
            case "END" -> "以下学员尚未填写考后结果，无法结束服务";
            default -> "请先回退以下学员的考后结果，并核对考试进度";
        }, current, blockers);
        // Ending/reopening the class preserves each student's examination outcome.
        if (!ENDED.equals(target) && !ENDED.equals(current.stage()))
            rows.forEach(r -> progress.saveMember(r.getId(), tenant(), target));
        progress.save(id, tenant(), target);
        return new Result(true, "状态已更新", view(id, userId, rows), List.of());
    }

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "delivery-class", bizId = "#id", action = "complete")
    @ZsjosAudit(action = "delivery-class.exam-outcome", targetType = "delivery-class")
    public View outcome(Long id, Long userId, Outcome command) {
        var rows = lock(id);
        var current = view(id, userId, rows);
        if (!Objects.equals(current.version(), command.version())) throw exception(DELIVERY_CLASS_VERSION_CONFLICT);
        if (!EXAM.equals(current.stage()) || current.actions().isEmpty()) throw exception(DELIVERY_CLASS_STATE_INVALID);
        var student = current.students().stream().filter(s -> s.serviceRelationId().equals(command.serviceRelationId()))
                .findFirst().orElseThrow(() -> exception(DELIVERY_CLASS_STATE_INVALID));
        if (!EXAM.equals(student.stage())) throw exception(DELIVERY_CLASS_STATE_INVALID);
        String value = command.value();
        if (Objects.equals(value, student.outcome())) return current;
        String snapshot = null;
        if (value != null) {
            dictionaries.validateDictDataList(OUTCOME_DICT, List.of(value));
            snapshot = current.outcomes().stream().filter(o -> o.value().equals(value)).map(Option::label).findFirst()
                    .orElseThrow(() -> exception(new ErrorCode(1_900_019_090, "考后结果选项已失效，请刷新后重试")));
        }
        if (progress.outcome(student.serviceRelationId(), tenant(), value, snapshot) != 1) throw exception(DELIVERY_CLASS_VERSION_CONFLICT);
        progress.save(id, tenant(), current.stage());
        return view(id, userId, rows);
    }
}
