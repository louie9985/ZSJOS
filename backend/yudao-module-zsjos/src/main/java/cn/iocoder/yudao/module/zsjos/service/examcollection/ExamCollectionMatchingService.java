package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;

/** Must run under the owning task lock, shared by public submission and manual resolution. */
@Service
public class ExamCollectionMatchingService {
    @Resource private ExamCollectionMemberMapper members;
    @Resource private ExamCollectionBindingMapper bindings;
    @Resource private ExamCollectionSubmissionMapper submissions;
    @Resource private ServiceRelationMapper relations;
    @Resource private StudentServiceArchiveStore archives;

    @Transactional(propagation=Propagation.MANDATORY)
    public void match(ExamCollectionTaskDO task, ExamCollectionSubmissionDO submission) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        if (!Objects.equals(task.getTenantId(), tenant) || !Objects.equals(submission.getTenantId(), tenant)
                || !Objects.equals(task.getId(), submission.getTaskId()) || submission.getIdentityHash() == null)
            throw exception(SESSION_INVALID);
        var binding = bindings.selectOne(new LambdaQueryWrapperX<ExamCollectionBindingDO>()
                .eq(ExamCollectionBindingDO::getTaskId, task.getId()).eq(ExamCollectionBindingDO::getIdentityHash, submission.getIdentityHash()));
        Long relationId = binding == null ? null : binding.getServiceRelationId();
        if (binding == null) {
            var candidates = members.selectList(new LambdaQueryWrapperX<ExamCollectionMemberDO>()
                    .eq(ExamCollectionMemberDO::getTaskId, task.getId()).eq(ExamCollectionMemberDO::getActive, true)
                    .eq(ExamCollectionMemberDO::getIdentityHash, submission.getIdentityHash()));
            var ids = candidates.stream().map(ExamCollectionMemberDO::getServiceRelationId).distinct().toList();
            if (ids.size() != 1) { unmatched(submission, ids.isEmpty() ? "UNMATCHED" : "AMBIGUOUS"); return; }
            relationId = ids.getFirst();
        }
        var relation = relations.selectByIdForUpdate(relationId, tenant);
        if (relation == null || !Objects.equals(relation.getTenantId(), tenant) || Boolean.TRUE.equals(relation.getDeleted())) {
            unmatched(submission, "INVALID_BINDING"); return;
        }
        if (binding == null) {
            binding = new ExamCollectionBindingDO(); binding.setTenantId(tenant); binding.setTaskId(task.getId());
            binding.setIdentityHash(submission.getIdentityHash()); binding.setServiceRelationId(relationId);
            binding.setManual(false); binding.setRevision(0); bindings.insert(binding);
        }
        if (ExamCollectionPolicy.shouldApply(submission.getSequenceNo(), binding.getLastSequenceNo())) {
            archives.apply(task, submission, relationId, null, "学员提交考试资料");
            binding.setLastSequenceNo(submission.getSequenceNo()); binding.setLastSubmissionId(submission.getId());
            binding.setRevision(binding.getRevision() + 1); bindings.updateById(binding);
            var member = members.selectOne(new LambdaQueryWrapperX<ExamCollectionMemberDO>()
                    .eq(ExamCollectionMemberDO::getTaskId, task.getId()).eq(ExamCollectionMemberDO::getServiceRelationId, relationId));
            if (member != null) { member.setLastSubmissionId(submission.getId()); members.updateById(member); }
        }
        submission.setStatus(ARCHIVED); submission.setServiceRelationId(relationId); submission.setExceptionCode("");
        submissions.updateById(submission);
    }
    private void unmatched(ExamCollectionSubmissionDO submission, String code) {
        submission.setStatus(EXCEPTION); submission.setExceptionCode(code); submissions.updateById(submission);
    }
}
