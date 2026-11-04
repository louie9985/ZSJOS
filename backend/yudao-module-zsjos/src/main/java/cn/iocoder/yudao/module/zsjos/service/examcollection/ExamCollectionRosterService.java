package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassKind;
import cn.iocoder.yudao.module.zsjos.service.registration.TeachingServiceLifecycle;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.FIELD_INVALID;

@Service
public class ExamCollectionRosterService {
    @Resource private ExamCollectionTaskMapper tasks;
    @Resource private ExamCollectionMemberMapper members;
    @Resource private ExamCollectionEventMapper events;
    @Resource private ExamCollectionSubmissionMapper submissions;
    @Resource private DeliveryClassMapper classes;
    @Resource private ServiceRelationMapper relations;
    @Resource private AdminUserApi users;
    @Resource private PermissionApi permissions;
    @Resource private ExamCollectionPermissionProvider objects;
    @Resource private StudentServiceArchiveStore archives;
    @Resource private ExamCollectionTokens tokens;
    @Resource private ExamCollectionTodoService todos;

    public List<ClassOption> classes() {
        if (!permissions.hasAnyPermissions(getLoginUserId(), QUERY)) throw exception(DENIED);
        TenantContextHolder.getRequiredTenantId();
        return classes.selectList(new LambdaQueryWrapperX<DeliveryClassDO>().eq(DeliveryClassDO::getStatus, "SERVING")
                .orderByDesc(DeliveryClassDO::getId)).stream().filter(DeliveryClassKind::assignable)
                .map(c -> new ClassOption(c.getId(), c.getClassName(), c.getHomeroomUserId(), c.getHomeroomUserNameSnapshot())).toList();
    }
    @ZsjosPermission(bizType="exam-collection", bizId="#id", action="notify")
    @Transactional(rollbackFor=Exception.class)
    public void notifyClasses(Long id, Notify request) {
        objects.check(id, "notify", getLoginUserId());
        var task = lock(id, request.getRevision()); ExamCollectionPublicService.requireOpen(task);
        // Initial notification freezes membership. Later additions must use the explicit roster command.
        if (task.getClassIdsJson() != null) throw exception(CONFLICT);
        var selected = new TreeSet<>(request.getClassIds());
        if (selected.size() != request.getClassIds().size()) throw exception(FIELD_INVALID, "重复班级");
        Long tenant = TenantContextHolder.getRequiredTenantId();
        for (Long classId : selected) {
            var clazz = classes.selectByIdForUpdate(classId, tenant);
            if (clazz == null || !Objects.equals(clazz.getTenantId(), tenant) || !"SERVING".equals(clazz.getStatus())
                    || !DeliveryClassKind.assignable(clazz) || clazz.getHomeroomUserId() == null) throw exception(CLASS_INVALID);
            var planner = users.getUser(clazz.getHomeroomUserId());
            if (planner == null || !Objects.equals(planner.getStatus(), 0)) throw exception(FIELD_INVALID, "班级学习规划师不可用，请先维护班级负责人");
            for (var relation : relations.selectClassRelationsForUpdate(classId, tenant)) {
                if (TeachingServiceLifecycle.closed(relation)) continue;
                var row = new ExamCollectionMemberDO(); row.setTenantId(tenant); row.setTaskId(id);
                row.setServiceRelationId(relation.getId()); row.setPersonId(relation.getPersonId()); row.setClassId(classId);
                row.setPlannerUserId(clazz.getHomeroomUserId()); row.setClassNameSnapshot(clazz.getClassName()); row.setActive(true);
                var archive = archives.current(relation.getId());
                String identity = archive.answers().stream().filter(a -> "id_card".equals(a.getFieldKey()))
                        .map(a -> a.getValueText()).filter(Objects::nonNull).findFirst().orElse(null);
                row.setIdentityHash(tokens.identityHash(identity)); members.insert(row);
            }
        }
        task.setClassIdsJson(JsonUtils.toJsonString(selected)); task.setRevision(task.getRevision() + 1); tasks.updateById(task);
        record(task, "NOTIFY", "", Map.of("classIds", selected)); todos.synchronize(task, true);
    }
    @ZsjosPermission(bizType="exam-collection", bizId="#id", action="complete")
    @Transactional(rollbackFor=Exception.class)
    public void complete(Long id, Transition request) {
        objects.check(id, "complete", getLoginUserId()); var task = lock(id, request.getRevision());
        ExamCollectionPublicService.requireOpen(task);
        long missing = members.selectCount(new LambdaQueryWrapperX<ExamCollectionMemberDO>().eq(ExamCollectionMemberDO::getTaskId, id)
                .eq(ExamCollectionMemberDO::getActive, true).isNull(ExamCollectionMemberDO::getLastSubmissionId));
        long exceptions = submissions.selectCount(new LambdaQueryWrapperX<ExamCollectionSubmissionDO>()
                .eq(ExamCollectionSubmissionDO::getTaskId, id).eq(ExamCollectionSubmissionDO::getStatus, EXCEPTION));
        if ((missing > 0 || exceptions > 0) && (request.getReason() == null || request.getReason().isBlank()))
            throw exception(FIELD_INVALID, "仍有缺交或异常，请填写结束原因");
        task.setStatus(COMPLETED); task.setCompletedAt(ExamCollectionPublicService.now()); task.setCompletionReason(request.getReason());
        task.setRevision(task.getRevision() + 1); tasks.updateById(task);
        record(task, "COMPLETE", request.getReason(), Map.of("missing", missing, "exceptions", exceptions)); todos.synchronize(task, true);
    }
    @ZsjosPermission(bizType="exam-collection", bizId="#id", action="reopen")
    @Transactional(rollbackFor=Exception.class)
    public void reopen(Long id, Transition request) {
        objects.check(id, "reopen", getLoginUserId()); var task = lock(id, request.getRevision());
        if (!COMPLETED.equals(task.getStatus())) throw exception(CONFLICT);
        task.setStatus(OPEN); task.setRevision(task.getRevision() + 1); tasks.updateById(task);
        // Keep the last completion time/reason as history; status controls current public availability.
        record(task, "REOPEN", request.getReason(), Map.of()); todos.synchronize(task, true);
    }
    private ExamCollectionTaskDO lock(Long id, Integer revision) {
        var task = tasks.lock(id);
        if (task == null || !Objects.equals(task.getTenantId(), TenantContextHolder.getRequiredTenantId())) throw exception(NOT_FOUND);
        if (!Objects.equals(task.getRevision(), revision)) throw exception(CONFLICT);
        return task;
    }
    void record(ExamCollectionTaskDO task, String action, String reason, Map<String, ?> details) {
        var event = new ExamCollectionEventDO(); event.setTenantId(task.getTenantId()); event.setTaskId(task.getId());
        event.setOperatorUserId(getLoginUserId()); event.setAction(action); event.setReason(Objects.requireNonNullElse(reason, ""));
        event.setDetailsJson(JsonUtils.toJsonString(details)); events.insert(event);
    }
}
