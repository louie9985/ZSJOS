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
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassGroupDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassGroupMapper;
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
    @Resource private ExamCollectionBindingMapper bindings;
    @Resource private DeliveryClassMapper classes;
    @Resource private DeliveryClassGroupMapper groups;
    @Resource private ServiceRelationMapper relations;
    @Resource private AdminUserApi users;
    @Resource private PermissionApi permissions;
    @Resource private ExamCollectionPermissionProvider objects;
    @Resource private StudentServiceArchiveStore archives;
    @Resource private ExamCollectionTokens tokens;
    @Resource private ExamCollectionTodoService todos;

    public List<ClassOption> classes() {
        if (!permissions.hasAnyPermissions(getLoginUserId(), QUERY)) throw exception(DENIED);
        Long tenant = TenantContextHolder.getRequiredTenantId();
        var available = classes.selectList(new LambdaQueryWrapperX<DeliveryClassDO>().eq(DeliveryClassDO::getStatus, "SERVING")
                .orderByDesc(DeliveryClassDO::getId)).stream().filter(DeliveryClassKind::assignable).toList();
        var groupIds = available.stream().map(DeliveryClassDO::getGroupId).filter(Objects::nonNull).distinct().toList();
        var groupRows = groupIds.isEmpty() ? List.<DeliveryClassGroupDO>of()
                : groups.selectByIds(groupIds);
        var groupMap = new HashMap<Long, DeliveryClassGroupDO>();
        for (var group : groupRows) if (Objects.equals(group.getTenantId(), tenant) && !Boolean.TRUE.equals(group.getDeleted())) groupMap.put(group.getId(), group);
        if (!groupMap.keySet().containsAll(groupIds)) throw exception(FIELD_INVALID, "班级组已变化，请刷新班级配置后重试");
        var orderedGroups = groupMap.values().stream().sorted(Comparator
                .comparing(DeliveryClassGroupDO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(DeliveryClassGroupDO::getId, Comparator.reverseOrder())).toList();
        var groupOrder = new HashMap<Long, Integer>();
        for (int index = 0; index < orderedGroups.size(); index++) groupOrder.put(orderedGroups.get(index).getId(), index);
        // Sort is stable: children keep the authoritative class-ID order, ungrouped classes remain independent.
        return available.stream().sorted(Comparator.comparingInt(c -> groupOrder.getOrDefault(c.getGroupId(), Integer.MAX_VALUE)))
                .map(c -> new ClassOption(c.getId(), c.getClassName(), c.getHomeroomUserId(), c.getHomeroomUserNameSnapshot(),
                        c.getGroupId(), c.getGroupId() == null ? null : groupMap.get(c.getGroupId()).getName(), c.getHomeroomUserId() != null)).toList();
    }
    @ZsjosPermission(bizType="exam-collection", bizId="#id", action="query")
    public List<ClassOption> classOptions(Long id) {
        objects.check(id,"query",getLoginUserId());
        var task = tasks.selectById(id);
        if (task == null || !Objects.equals(task.getTenantId(),TenantContextHolder.getRequiredTenantId())) throw exception(NOT_FOUND);
        var options = new LinkedHashMap<Long,ClassOption>(); classes().forEach(option -> options.put(option.id(),option));
        var selected = task.getClassIdsJson() == null ? List.<Long>of() : JsonUtils.parseArray(task.getClassIdsJson(),Long.class);
        var missing = selected.stream().filter(classId -> !options.containsKey(classId)).toList();
        if (!missing.isEmpty()) {
            var frozen = members.selectList(new LambdaQueryWrapperX<ExamCollectionMemberDO>().eq(ExamCollectionMemberDO::getTaskId,id));
            var historical = classes.selectByIds(missing).stream().collect(java.util.stream.Collectors.toMap(DeliveryClassDO::getId,c -> c));
            for (Long classId : missing) {
                var clazz = historical.get(classId);
                String name = clazz == null ? frozen.stream().filter(m -> classId.equals(m.getClassId())).map(ExamCollectionMemberDO::getClassNameSnapshot)
                        .filter(Objects::nonNull).findFirst().orElse("已不可用班级") : clazz.getClassName();
                options.put(classId,new ClassOption(classId,name,null,null,null,null,false));
            }
        }
        var plannerIds = options.values().stream().map(ClassOption::plannerId).filter(Objects::nonNull).distinct().toList();
        var planners = plannerIds.isEmpty() ? Map.<Long,cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO>of() : users.getUserMap(plannerIds);
        return options.values().stream().map(option -> new ClassOption(option.id(),option.name(),option.plannerId(),option.plannerName(),option.groupId(),option.groupName(),
                option.selectable() && planners.containsKey(option.plannerId()) && Objects.equals(planners.get(option.plannerId()).getStatus(),0))).toList();
    }

    @ZsjosPermission(bizType="exam-collection", bizId="#id", action="members")
    @Transactional(rollbackFor=Exception.class)
    public void adjustClasses(Long id, ClassChange request) {
        objects.check(id,"members",getLoginUserId());
        var task = lock(id,request.getRevision()); ExamCollectionPublicService.requireOpen(task);
        if (task.getClassIdsJson() == null) throw exception(CONFLICT);
        if (request.getClassIds() == null || request.getClassIds().isEmpty() || request.getClassIds().size()>100
                || request.getReason() == null || request.getReason().isBlank() || request.getReason().length()>500) throw exception(FIELD_INVALID,"请选择班级并填写调整原因");
        var selected = new TreeSet<>(request.getClassIds());
        if (selected.size()!=request.getClassIds().size()) throw exception(FIELD_INVALID,"重复班级");
        var previous = new TreeSet<>(JsonUtils.parseArray(task.getClassIdsJson(),Long.class));
        var additions = new TreeSet<>(selected); additions.removeAll(previous);
        var removals = new TreeSet<>(previous); removals.removeAll(selected);
        if (additions.isEmpty() && removals.isEmpty()) return;
        var existing = members.selectList(new LambdaQueryWrapperX<ExamCollectionMemberDO>().eq(ExamCollectionMemberDO::getTaskId,id))
                .stream().collect(java.util.stream.Collectors.toMap(ExamCollectionMemberDO::getServiceRelationId,m -> m));
        for (var member : existing.values()) if (removals.contains(member.getClassId()) && Boolean.TRUE.equals(member.getActive())) {
            member.setActive(false); members.updateById(member);
        }
        for (Long classId : additions) {
            var clazz = classes.selectByIdForUpdate(classId,task.getTenantId());
            if (clazz == null || !Objects.equals(clazz.getTenantId(),task.getTenantId()) || !"SERVING".equals(clazz.getStatus())
                    || !DeliveryClassKind.assignable(clazz) || clazz.getHomeroomUserId()==null) throw exception(CLASS_INVALID);
            var planner = users.getUser(clazz.getHomeroomUserId());
            if (planner == null || !Objects.equals(planner.getStatus(),0)) throw exception(CLASS_INVALID);
            // Re-adding a class restores its original frozen rows, including prior collected state.
            for (var member : existing.values()) if (classId.equals(member.getClassId()) && !Boolean.TRUE.equals(member.getActive())) {
                member.setActive(true); members.updateById(member);
            }
            for (var relation : relations.selectClassRelationsForUpdate(classId,task.getTenantId())) {
                if (TeachingServiceLifecycle.closed(relation)) continue;
                var member = existing.get(relation.getId());
                if (member != null) continue;
                member = new ExamCollectionMemberDO(); member.setTenantId(task.getTenantId());member.setTaskId(id);
                member.setServiceRelationId(relation.getId());member.setPersonId(relation.getPersonId());member.setClassId(classId);
                member.setPlannerUserId(clazz.getHomeroomUserId());member.setClassNameSnapshot(clazz.getClassName());member.setActive(true);
                String identity = archives.current(relation.getId()).answers().stream().filter(a -> "id_card".equals(a.getFieldKey()))
                        .map(a -> a.getValueText()).filter(Objects::nonNull).findFirst().orElse(null);
                member.setIdentityHash(tokens.identityHash(identity));
                var collected = bindings.selectOne(new LambdaQueryWrapperX<ExamCollectionBindingDO>().eq(ExamCollectionBindingDO::getTaskId,id)
                        .eq(ExamCollectionBindingDO::getServiceRelationId,relation.getId()).isNotNull(ExamCollectionBindingDO::getLastSubmissionId)
                        .orderByDesc(ExamCollectionBindingDO::getLastSequenceNo).last("LIMIT 1"));
                if (collected != null) member.setLastSubmissionId(collected.getLastSubmissionId());
                members.insert(member);existing.put(relation.getId(),member);
            }
        }
        task.setClassIdsJson(JsonUtils.toJsonString(selected));task.setRevision(task.getRevision()+1);tasks.updateById(task);
        record(task,"ADJUST_CLASSES",request.getReason(),Map.of("addedClassIds",additions,"removedClassIds",removals));todos.synchronize(task,true);
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
                row.setIdentityHash(tokens.identityHash(identity));
                // The shared link can already have manually resolved submissions before initial notification.
                var collected = bindings.selectOne(new LambdaQueryWrapperX<ExamCollectionBindingDO>()
                        .eq(ExamCollectionBindingDO::getTaskId, id).eq(ExamCollectionBindingDO::getServiceRelationId, relation.getId())
                        .isNotNull(ExamCollectionBindingDO::getLastSubmissionId).orderByDesc(ExamCollectionBindingDO::getLastSequenceNo).last("LIMIT 1"));
                if (collected != null) row.setLastSubmissionId(collected.getLastSubmissionId());
                members.insert(row);
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
    @ZsjosPermission(bizType="exam-collection", bizId="#id", action="members")
    @Transactional(rollbackFor=Exception.class)
    public void adjust(Long id, RosterChange request) {
        objects.check(id, "members", getLoginUserId()); var task = lock(id, request.getRevision());
        ExamCollectionPublicService.requireOpen(task);
        if (task.getClassIdsJson() == null) throw exception(CONFLICT);
        var additions = new TreeSet<>(request.getAddServiceIds()); var removals = new TreeSet<>(request.getRemoveServiceIds());
        if (additions.isEmpty() && removals.isEmpty() || additions.stream().anyMatch(removals::contains)
                || additions.size() != request.getAddServiceIds().size() || removals.size() != request.getRemoveServiceIds().size())
            throw exception(FIELD_INVALID, "请明确选择加入或移出的学员，不能重复或交叉");
        var existing = members.selectList(new LambdaQueryWrapperX<ExamCollectionMemberDO>().eq(ExamCollectionMemberDO::getTaskId, id))
                .stream().collect(java.util.stream.Collectors.toMap(ExamCollectionMemberDO::getServiceRelationId, m -> m));
        for (Long relationId : removals) {
            var row = existing.get(relationId);
            if (row == null || !Boolean.TRUE.equals(row.getActive())) throw exception(CONFLICT);
            row.setActive(false); members.updateById(row);
        }
        var classIds = new TreeSet<>(JsonUtils.parseArray(task.getClassIdsJson(), Long.class));
        for (Long relationId : additions) {
            var row = existing.get(relationId);
            if (row != null) {
                if (Boolean.TRUE.equals(row.getActive())) throw exception(CONFLICT);
                row.setActive(true); members.updateById(row); classIds.add(row.getClassId()); continue;
            }
            Long tenant = task.getTenantId(); var relation = relations.selectByIdForUpdate(relationId, tenant);
            if (relation == null || !Objects.equals(relation.getTenantId(), tenant) || !Boolean.TRUE.equals(relation.getTeachingDelivery())
                    || TeachingServiceLifecycle.closed(relation) || relation.getClassId() == null) throw exception(CLASS_INVALID);
            var clazz = classes.selectById(relation.getClassId());
            if (clazz == null || !Objects.equals(clazz.getTenantId(), tenant) || !"SERVING".equals(clazz.getStatus()) || !DeliveryClassKind.assignable(clazz)
                    || clazz.getHomeroomUserId() == null) throw exception(CLASS_INVALID);
            var planner = users.getUser(clazz.getHomeroomUserId());
            if (planner == null || !Objects.equals(planner.getStatus(), 0)) throw exception(CLASS_INVALID);
            row = new ExamCollectionMemberDO(); row.setTenantId(tenant); row.setTaskId(id); row.setServiceRelationId(relationId);
            row.setPersonId(relation.getPersonId()); row.setClassId(clazz.getId()); row.setClassNameSnapshot(clazz.getClassName());
            row.setPlannerUserId(clazz.getHomeroomUserId()); row.setActive(true);
            var archive = archives.current(relationId);
            String identity = archive.answers().stream().filter(a -> "id_card".equals(a.getFieldKey())).map(a -> a.getValueText())
                    .filter(Objects::nonNull).findFirst().orElse(null); row.setIdentityHash(tokens.identityHash(identity));
            var last = bindings.selectOne(new LambdaQueryWrapperX<ExamCollectionBindingDO>()
                    .eq(ExamCollectionBindingDO::getTaskId, id).eq(ExamCollectionBindingDO::getServiceRelationId, relationId)
                    .isNotNull(ExamCollectionBindingDO::getLastSubmissionId).orderByDesc(ExamCollectionBindingDO::getLastSequenceNo).last("LIMIT 1"));
            if (last != null) row.setLastSubmissionId(last.getLastSubmissionId());
            members.insert(row); classIds.add(clazz.getId());
        }
        task.setClassIdsJson(JsonUtils.toJsonString(classIds)); task.setRevision(task.getRevision() + 1); tasks.updateById(task);
        record(task, "ADJUST_MEMBERS", request.getReason(), Map.of("added", additions, "removed", removals)); todos.synchronize(task, true);
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
