package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.registration.TeachingServiceLifecycle;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.StudentServiceArchiveStore.Snapshot;

@Service
public class ExamCollectionCorrectionService {
    @Resource private ExamCollectionTaskMapper tasks;
    @Resource private ExamCollectionSubmissionMapper submissions;
    @Resource private ExamCollectionBindingMapper bindings;
    @Resource private ExamCollectionMemberMapper members;
    @Resource private ExamCollectionEventMapper events;
    @Resource private ServiceRelationMapper relations;
    @Resource private ExamCollectionPermissionProvider objects;
    @Resource private StudentServiceArchiveStore archives;
    @Resource private ExamCollectionTodoService todos;

    private record Context(ExamCollectionTaskDO task, ExamCollectionSubmissionDO row, ExamCollectionBindingDO binding,
                           List<ExamCollectionSubmissionDO> group, Snapshot source, Snapshot target, Map<Long,String> studentNames) {
        Set<Long> ids() { return new LinkedHashSet<>(group.stream().map(ExamCollectionSubmissionDO::getId).toList()); }
    }

    @ZsjosPermission(bizType="exam-collection", bizId="#taskId", action="resolve")
    @Transactional(rollbackFor=Exception.class)
    public CorrectionPreview preview(Long taskId, Long submissionId, Long targetId) {
        var context = prepare(taskId, submissionId, targetId);
        var restoration = archives.restoration(context.source().relationId(), taskId, context.ids());
        var result = new CorrectionPreview(); result.setRevision(context.row().getRevision());
        result.setTaskRevision(context.task().getRevision()); result.setBindingRevision(context.binding().getRevision());
        result.setLatestSubmissionId(context.group().getFirst().getId()); result.setSubmissionCount(context.group().size());
        result.setSourceArchive(project(context.source())); result.setRestoredArchive(project(restoration.snapshot()));
        result.setSourceStudentName(context.studentNames().get(context.source().relationId()));
        result.setTargetStudentName(context.studentNames().get(context.target().relationId()));
        result.setTargetArchive(project(context.target()));
        result.setRestoredFields(labels(context.source(), restoration.restoredKeys()));
        result.setRetainedFields(labels(context.source(), restoration.retainedLaterKeys())); return result;
    }

    @ZsjosPermission(bizType="exam-collection", bizId="#taskId", action="resolve")
    @Transactional(rollbackFor=Exception.class)
    public void correct(Long taskId, Long submissionId, Correction request) {
        var context = prepare(taskId, submissionId, request.getServiceRelationId());
        var task = context.task(); var binding = context.binding(); var latest = context.group().getFirst();
        if (request.getReason() == null || request.getReason().isBlank() || request.getReason().length() > 500) throw exception(CONFLICT);
        if (!Objects.equals(request.getRevision(), context.row().getRevision())
                || !Objects.equals(request.getTaskRevision(), task.getRevision())
                || !Objects.equals(request.getBindingRevision(), binding.getRevision())
                || !Objects.equals(request.getLatestSubmissionId(), latest.getId())) throw exception(CONFLICT);
        if (!Objects.equals(request.getSourceArchiveRevision(), context.source().revision())
                || !Objects.equals(request.getTargetArchiveRevision(), context.target().revision())) throw exception(ARCHIVE_CONFLICT);
        Long sourceId = context.source().relationId(), targetId = context.target().relationId();
        int nextBindingRevision = Math.addExact(binding.getRevision(), 1);
        var restoration = archives.restore(task, context.ids(), sourceId, context.source().revision(), getLoginUserId(),
                request.getReason(), binding.getId(), nextBindingRevision);
        int targetRevision = archives.applyCorrection(task, latest, targetId, getLoginUserId(), request.getReason(), binding.getId(), nextBindingRevision);
        binding.setServiceRelationId(targetId); binding.setManual(true); binding.setRevision(nextBindingRevision);
        binding.setLastSequenceNo(latest.getSequenceNo()); binding.setLastSubmissionId(latest.getId()); bindings.updateById(binding);
        for (var row : context.group()) {
            row.setServiceRelationId(targetId); row.setStatus(ARCHIVED); row.setExceptionCode("");
            row.setRevision(Math.addExact(row.getRevision(), 1)); submissions.updateById(row);
        }
        refreshMember(task, sourceId); refreshMember(task, targetId);
        task.setRevision(Math.addExact(task.getRevision(), 1)); tasks.updateById(task);
        // A previously collected planner can now have a missing member; use a new generation to reopen that todo.
        todos.synchronize(task, true);
        var details = new LinkedHashMap<String,Object>(); details.put("sourceServiceRelationId", sourceId); details.put("targetServiceRelationId", targetId);
        details.put("submissionIds", context.ids()); details.put("latestSubmissionId", latest.getId());
        details.put("sourceArchiveRevision", context.source().revision() + 1); details.put("targetArchiveRevision", targetRevision);
        details.put("restoredKeys", restoration.restoredKeys()); details.put("retainedLaterKeys", restoration.retainedLaterKeys());
        var event = new ExamCollectionEventDO(); event.setTenantId(task.getTenantId()); event.setTaskId(taskId);
        event.setSubmissionId(submissionId); event.setAction("CORRECT_BINDING"); event.setOperatorUserId(getLoginUserId());
        event.setReason(request.getReason()); event.setDetailsJson(JsonUtils.toJsonString(details)); events.insert(event);
    }

    private Context prepare(Long taskId, Long submissionId, Long targetId) {
        objects.check(taskId, "resolve", getLoginUserId());
        Long tenant = TenantContextHolder.getRequiredTenantId();
        var task = tasks.lock(taskId);
        if (task == null || Boolean.TRUE.equals(task.getDeleted()) || !Objects.equals(task.getTenantId(), tenant)) throw exception(NOT_FOUND);
        var row = submissions.lock(submissionId);
        if (row == null || row.getSubmittedAt() == null || Boolean.TRUE.equals(row.getDeleted())
                || !Objects.equals(row.getTaskId(), taskId) || !Objects.equals(row.getTenantId(), tenant)) throw exception(NOT_FOUND);
        if (!(ARCHIVED.equals(row.getStatus()) || EXCEPTION.equals(row.getStatus())) || row.getIdentityHash() == null) throw exception(CONFLICT);
        var binding = bindings.selectOne(new LambdaQueryWrapperX<ExamCollectionBindingDO>()
                .eq(ExamCollectionBindingDO::getTaskId, taskId).eq(ExamCollectionBindingDO::getIdentityHash, row.getIdentityHash()));
        if (binding == null || !Objects.equals(binding.getTenantId(), tenant) || binding.getServiceRelationId() == null
                || binding.getLastSubmissionId() == null || targetId == null || targetId.equals(binding.getServiceRelationId())) throw exception(CONFLICT);
        // All correction transactions take service locks in the same order, including cross-task corrections.
        var studentNames = new HashMap<Long,String>();
        for (Long id : java.util.stream.Stream.of(binding.getServiceRelationId(), targetId).sorted().toList()) {
            var relation = relations.selectByIdForUpdate(id, tenant);
            if (relation == null || Boolean.TRUE.equals(relation.getDeleted()) || !Objects.equals(relation.getTenantId(), tenant)) throw exception(NOT_FOUND);
            TeachingServiceLifecycle.requireOpen(relation);
            studentNames.put(id, relation.getStudentNameSnapshot());
        }
        var group = submissions.selectList(new LambdaQueryWrapperX<ExamCollectionSubmissionDO>()
                .eq(ExamCollectionSubmissionDO::getTaskId, taskId).eq(ExamCollectionSubmissionDO::getIdentityHash, row.getIdentityHash())
                .in(ExamCollectionSubmissionDO::getStatus, ARCHIVED, EXCEPTION).orderByDesc(ExamCollectionSubmissionDO::getSequenceNo));
        if (group.isEmpty() || group.stream().noneMatch(s -> Objects.equals(s.getId(), row.getId()))
                || group.stream().anyMatch(s -> !Objects.equals(s.getTenantId(), tenant) || s.getSubmittedAt() == null
                || ARCHIVED.equals(s.getStatus()) && !Objects.equals(s.getServiceRelationId(), binding.getServiceRelationId()))) throw exception(CONFLICT);
        return new Context(task, row, binding, group, archives.current(binding.getServiceRelationId()), archives.current(targetId), studentNames);
    }

    private void refreshMember(ExamCollectionTaskDO task, Long relationId) {
        var latest = submissions.selectOne(new LambdaQueryWrapperX<ExamCollectionSubmissionDO>()
                .eq(ExamCollectionSubmissionDO::getTaskId, task.getId()).eq(ExamCollectionSubmissionDO::getServiceRelationId, relationId)
                .eq(ExamCollectionSubmissionDO::getStatus, ARCHIVED).orderByDesc(ExamCollectionSubmissionDO::getSequenceNo).last("LIMIT 1"));
        // Explicit SQL SET is necessary: updateById ignores null and would leave the old collected marker.
        members.update(null, new LambdaUpdateWrapper<ExamCollectionMemberDO>()
                .eq(ExamCollectionMemberDO::getTenantId, task.getTenantId()).eq(ExamCollectionMemberDO::getTaskId, task.getId())
                .eq(ExamCollectionMemberDO::getServiceRelationId, relationId)
                .set(ExamCollectionMemberDO::getLastSubmissionId, latest == null ? null : latest.getId()));
    }
    private static List<String> labels(Snapshot snapshot, Set<String> keys) {
        return snapshot.fields().stream().filter(f -> keys.contains(f.getKey())).map(f -> f.getLabel()).distinct().toList();
    }
    private static Archive project(Snapshot snapshot) {
        var result = new Archive(); result.setServiceRelationId(snapshot.relationId()); result.setRevision(snapshot.revision());
        result.setFields(snapshot.fields()); result.setValues(ExamCollectionQueryService.display(snapshot.answers()));
        result.setAttachments(snapshot.attachments().stream().map(a -> new ArchiveAttachment(a.source(),a.id(),a.fieldKey(),a.side(),a.name(),a.contentType(),a.size())).toList()); return result;
    }
}
