package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;

@Service
public class ExamCollectionResolutionService {
    @Resource private ExamCollectionTaskMapper tasks;
    @Resource private ExamCollectionSubmissionMapper submissions;
    @Resource private ExamCollectionBindingMapper bindings;
    @Resource private ExamCollectionEventMapper events;
    @Resource private ServiceRelationMapper relations;
    @Resource private ExamCollectionPermissionProvider objects;
    @Resource private StudentServiceArchiveStore archives;
    @Resource private ExamCollectionMatchingService matching;

    @ZsjosPermission(bizType="exam-collection", bizId="#taskId", action="resolve")
    @Transactional(rollbackFor=Exception.class)
    public void resolve(Long taskId, Long submissionId, Resolve request) {
        objects.check(taskId, "resolve", getLoginUserId());
        var task = task(taskId); var row = submission(taskId, submissionId, request.getRevision());
        if (!EXCEPTION.equals(row.getStatus())) throw exception(CONFLICT);
        var target = relations.selectByIdForUpdate(request.getServiceRelationId(), task.getTenantId());
        if (target == null || Boolean.TRUE.equals(target.getDeleted()) || !Objects.equals(target.getTenantId(), task.getTenantId())) throw exception(NOT_FOUND);
        if (archives.current(target.getId()).revision() != request.getArchiveRevision()) throw exception(CONFLICT);
        var binding = bindings.selectOne(new LambdaQueryWrapperX<ExamCollectionBindingDO>()
                .eq(ExamCollectionBindingDO::getTaskId, taskId).eq(ExamCollectionBindingDO::getIdentityHash, row.getIdentityHash()));
        if (binding != null && !Objects.equals(binding.getServiceRelationId(), target.getId()))
            throw exception(CONFLICT); // A previous archive write needs the explicit correction path, never silent reassignment.
        if (binding == null) {
            binding = new ExamCollectionBindingDO(); binding.setTenantId(task.getTenantId()); binding.setTaskId(taskId);
            binding.setIdentityHash(row.getIdentityHash()); binding.setServiceRelationId(target.getId()); binding.setManual(true);
            binding.setRevision(0); bindings.insert(binding);
        } else { binding.setManual(true); binding.setRevision(binding.getRevision() + 1); bindings.updateById(binding); }
        var group = submissions.selectList(new LambdaQueryWrapperX<ExamCollectionSubmissionDO>()
                .eq(ExamCollectionSubmissionDO::getTaskId, taskId).eq(ExamCollectionSubmissionDO::getIdentityHash, row.getIdentityHash())
                .eq(ExamCollectionSubmissionDO::getStatus, EXCEPTION).orderByDesc(ExamCollectionSubmissionDO::getSequenceNo));
        // Newest first guarantees one effective update; older submissions retain their own immutable contents.
        for (var pending : group) matching.match(task, pending, getLoginUserId(), request.getReason());
        record(task, row, "RESOLVE", request.getReason(), Map.of("serviceRelationId", target.getId(), "submissionIds", group.stream().map(ExamCollectionSubmissionDO::getId).toList()));
    }
    @ZsjosPermission(bizType="exam-collection", bizId="#taskId", action="resolve")
    @Transactional(rollbackFor=Exception.class)
    public void invalidate(Long taskId, Long submissionId, Invalidate request) {
        objects.check(taskId, "resolve", getLoginUserId()); var task = task(taskId);
        var row = submission(taskId, submissionId, request.getRevision());
        if (!EXCEPTION.equals(row.getStatus())) throw exception(CONFLICT);
        row.setStatus(INVALID); row.setRevision(row.getRevision() + 1); submissions.updateById(row);
        record(task, row, "INVALIDATE", request.getReason(), Map.of());
    }
    private ExamCollectionTaskDO task(Long id) {
        var task = tasks.lock(id);
        if (task == null || !Objects.equals(task.getTenantId(), TenantContextHolder.getRequiredTenantId())) throw exception(NOT_FOUND);
        // Completed tasks retain resolvable exceptions without accepting new public submissions.
        return task;
    }
    private ExamCollectionSubmissionDO submission(Long taskId, Long id, Integer revision) {
        var row = submissions.lock(id);
        if (row == null || !Objects.equals(row.getTaskId(), taskId)
                || !Objects.equals(row.getTenantId(), TenantContextHolder.getRequiredTenantId())) throw exception(NOT_FOUND);
        if (!Objects.equals(row.getRevision(), revision)) throw exception(CONFLICT);
        return row;
    }
    private void record(ExamCollectionTaskDO task, ExamCollectionSubmissionDO submission, String action, String reason, Map<String, ?> details) {
        var event = new ExamCollectionEventDO(); event.setTenantId(task.getTenantId()); event.setTaskId(task.getId());
        event.setSubmissionId(submission.getId()); event.setOperatorUserId(getLoginUserId()); event.setAction(action);
        event.setReason(reason); event.setDetailsJson(JsonUtils.toJsonString(details)); events.insert(event);
    }
}
