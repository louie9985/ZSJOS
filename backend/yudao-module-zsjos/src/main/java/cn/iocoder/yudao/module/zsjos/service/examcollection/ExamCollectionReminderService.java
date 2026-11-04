package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
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
    @Resource private BusinessTaskMapper businessTasks;
    @Resource private ExamCollectionTaskMapper tasks;
    @Resource private ExamCollectionMemberMapper members;
    @Resource private ExamCollectionEventMapper events;
    @Resource private ExamCollectionTaskService taskService;
    public record Todo(Long todoId, Long collectionTaskId, String title, List<String> classes,
                       long expected, long collected, String url) {}
    public record Seen(Long businessTaskId) {}

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
            if (!TASK_TYPE.equals(todo.getTaskType()) || !Objects.equals(todo.getAssigneeId(), user)
                    || !Objects.equals(todo.getTenantId(), TenantContextHolder.getTenantId())) continue;
            var task = tasks.selectById(todo.getBizId());
            if (task == null || !OPEN.equals(task.getStatus())) continue;
            var own = members.selectList(new LambdaQueryWrapperX<ExamCollectionMemberDO>()
                    .eq(ExamCollectionMemberDO::getTaskId, task.getId()).eq(ExamCollectionMemberDO::getPlannerUserId, user)
                    .eq(ExamCollectionMemberDO::getActive, true));
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
