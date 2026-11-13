package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionPlannerVO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;

@Service
public class ExamCollectionReminderService {
    @Resource private PermissionApi permissions;
    @Resource private ExamCollectionRecipientScope recipientScope;
    @Resource private BusinessTaskMapper businessTasks;
    @Resource private ExamCollectionTaskMapper tasks;
    @Resource private ExamCollectionMemberMapper members;
    @Resource private ExamCollectionEventMapper events;
    @Resource private ExamCollectionTaskService taskService;
    @Resource private ExamCollectionPlannerPermissionProvider plannerPermissions;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper relations;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper people;
    @Resource private cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentSummaryService summary;
    public record Todo(Long todoId, Long collectionTaskId, String title, List<String> classes,
                       long expected, long collected, String url) {}
    public record Seen(Long businessTaskId) {}
    public record State(List<Todo> pending, List<Long> unreadTodoIds) {}

    @Transactional(readOnly=true)
    public State state() {
        var pending=pending();if(pending.isEmpty())return new State(List.of(),List.of());
        var seen=seen(pending,getLoginUserId());
        return new State(pending,pending.stream().map(Todo::todoId).filter(id->!seen.contains(id)).toList());
    }
    @cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission(bizType="exam-collection-planner",bizId="#taskId",action="query")
    public PageResult<ExamCollectionPlannerVO.Member> members(Long taskId,ExamCollectionPlannerVO.Query query) {
        Long user=actor();plannerPermissions.check(taskId,"query",user);
        var filter=recipientScope.filter(taskId, user);
        if(query.getFilled()!=null){if(query.getFilled())filter.isNotNull(ExamCollectionMemberDO::getLastSubmissionId);else filter.isNull(ExamCollectionMemberDO::getLastSubmissionId);}
        if(query.getKeyword()!=null&&!query.getKeyword().isBlank())filter.apply(
                "EXISTS (SELECT 1 FROM zsjos_service_relation s JOIN zsjos_person p ON p.id=s.person_id AND p.tenant_id=s.tenant_id AND p.deleted=0 "
                +"WHERE s.id=zsjos_exam_collection_member.service_relation_id AND s.tenant_id=zsjos_exam_collection_member.tenant_id AND s.deleted=0 "
                +"AND (LOCATE({0},s.student_name_snapshot)>0 OR LOCATE({0},p.mobile)>0))",query.getKeyword().trim());
        // Archive columns are projected values. Sort the authorized result before slicing pages.
        boolean sorted = query.getSortField() != null;
        filter.orderByAsc(ExamCollectionMemberDO::getId);
        var page = sorted ? new PageResult<>(members.selectList(filter), 0L) : members.selectPage(query, filter);
        if(page.getList().isEmpty())return new PageResult<>(List.of(),page.getTotal());
        var serviceMap=relations.selectByIds(page.getList().stream().map(ExamCollectionMemberDO::getServiceRelationId).toList()).stream()
                .collect(Collectors.toMap(r->r.getId(),java.util.function.Function.identity()));
        var personMap=people.selectByIds(page.getList().stream().map(ExamCollectionMemberDO::getPersonId).distinct().toList()).stream()
                .collect(Collectors.toMap(p->p.getId(),java.util.function.Function.identity()));
        // Use the same formal archive precedence as the collection management roster.
        var archiveRows = summary.projectForExamPlanner(taskId, new ArrayList<>(serviceMap.values())).stream()
                .collect(Collectors.toMap(r -> r.serviceRelationId(), r -> r.values()));
        var projected = page.getList().stream().map(m->
                new ExamCollectionPlannerVO.Member(m.getServiceRelationId(),
                    serviceMap.containsKey(m.getServiceRelationId())?serviceMap.get(m.getServiceRelationId()).getStudentNameSnapshot():"已删除学员服务",
                    personMap.containsKey(m.getPersonId())?personMap.get(m.getPersonId()).getMobile():null,
                    m.getClassNameSnapshot(),m.getLastSubmissionId()!=null,
                    archiveRows.getOrDefault(m.getServiceRelationId(), Map.of()).get("info.name"),
                    archiveRows.getOrDefault(m.getServiceRelationId(), Map.of()).get("info.mobile"),
                    archiveRows.getOrDefault(m.getServiceRelationId(), Map.of()).get("info.id_card"))).toList();
        if (!sorted) return new PageResult<>(projected, page.getTotal());
        java.util.function.Function<ExamCollectionPlannerVO.Member, String> value = switch (query.getSortField()) {
            case "studentName" -> ExamCollectionPlannerVO.Member::studentName;
            case "mobile" -> ExamCollectionPlannerVO.Member::mobile;
            case "archiveName" -> ExamCollectionPlannerVO.Member::archiveName;
            case "archiveMobile" -> ExamCollectionPlannerVO.Member::archiveMobile;
            case "archiveIdCard" -> ExamCollectionPlannerVO.Member::archiveIdCard;
            case "className" -> ExamCollectionPlannerVO.Member::className;
            case "filled" -> row -> Boolean.toString(row.filled());
            default -> throw new IllegalArgumentException("Unsupported planner member sort field");
        };
        Comparator<String> textOrder = java.text.Collator.getInstance(Locale.CHINA)::compare;
        if ("desc".equals(query.getSortOrder())) textOrder = textOrder.reversed();
        var ordered = projected.stream().sorted(Comparator.comparing(value, Comparator.nullsLast(textOrder))).toList();
        int start = (int) Math.min((long) (query.getPageNo() - 1) * query.getPageSize(), ordered.size());
        int end = (int) Math.min((long) start + query.getPageSize(), ordered.size());
        return new PageResult<>(ordered.subList(start, end), (long) ordered.size());
    }

    private Long actor() {
        Long user = getLoginUserId(); TenantContextHolder.getRequiredTenantId();
        if (user == null || !permissions.hasAnyPermissions(user, "zsjos:business-task:query")) throw exception(DENIED);
        return user;
    }
    public List<Todo> pending() {
        Long user = actor();
        var pending = businessTasks.selectMyPendingByBusiness(user, BIZ_TYPE, null);
        var result = new ArrayList<Todo>();
        for (var todo : pending) {
            if (!TASK_TYPE.equals(todo.getTaskType()) || !BIZ_TYPE.equals(todo.getBizType()) || !"pending".equals(todo.getStatus())
                    || Boolean.TRUE.equals(todo.getDeleted()) || !Objects.equals(todo.getAssigneeId(), user)
                    || !Objects.equals(todo.getTenantId(), TenantContextHolder.getTenantId())) continue;
            var task = tasks.selectById(todo.getBizId());
            if (task == null || !Objects.equals(task.getTenantId(),TenantContextHolder.getTenantId()) || Boolean.TRUE.equals(task.getDeleted()) || !OPEN.equals(task.getStatus())) continue;
            var own = members.selectList(recipientScope.filter(task.getId(), user));
            long collected = own.stream().filter(m -> m.getLastSubmissionId() != null).count();
            if (own.isEmpty() || collected == own.size()) continue;
            result.add(new Todo(todo.getId(), task.getId(), task.getTitle(),
                    own.stream().map(ExamCollectionMemberDO::getClassNameSnapshot).distinct().toList(), own.size(), collected,
                    taskService.publicUrl(task.getTokenCiphertext())));
        }
        return result;
    }
    public List<Todo> reminders() {
        var pending = pending(); if (pending.isEmpty()) return List.of();
        var seen = seen(pending, getLoginUserId());
        return pending.stream().filter(t -> !seen.contains(t.todoId())).toList();
    }
    private Set<Long> seen(List<Todo> pending, Long user) {
        var rows = events.selectList(new LambdaQueryWrapperX<ExamCollectionEventDO>()
                .in(ExamCollectionEventDO::getTaskId, pending.stream().map(Todo::collectionTaskId).distinct().toList())
                .eq(ExamCollectionEventDO::getOperatorUserId, user).eq(ExamCollectionEventDO::getAction, "REMINDER_SEEN"));
        return rows.stream().map(e -> JsonUtils.parseObject(e.getDetailsJson(), Seen.class).businessTaskId()).collect(Collectors.toSet());
    }
    @Transactional(rollbackFor=Exception.class)
    public void acknowledge(List<Long> ids) {
        Long user = actor(); var pending = pending();
        var allowed = pending.stream().collect(Collectors.toMap(Todo::todoId, t -> t));
        for (Long id : ids) if (!allowed.containsKey(id)) throw exception(DENIED);
        var seen = pending.isEmpty() ? Set.<Long>of() : seen(pending, user);
        for (Long id : new LinkedHashSet<>(ids)) {
            if (seen.contains(id)) continue;
            var row = new ExamCollectionEventDO(); row.setTenantId(TenantContextHolder.getRequiredTenantId());
            row.setTaskId(allowed.get(id).collectionTaskId()); row.setOperatorUserId(user); row.setAction("REMINDER_SEEN");
            row.setReason(""); row.setDetailsJson(JsonUtils.toJsonString(new Seen(id))); events.insert(row);
        }
    }
}
