package cn.iocoder.yudao.module.zsjos.service.delivery;
import cn.iocoder.yudao.module.zsjos.controller.admin.delivery.vo.StudentDeliverySubmissionReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
@Service public class StudentDeliverySubmissionServiceImpl implements StudentDeliverySubmissionService {
 @Resource private StudentDeliverySubmissionMapper submissionMapper; @Resource private StudentDeliveryStageMapper stageMapper;
 @Resource private StudentDeliveryPlanService planService;
 @Resource private BusinessTaskCommandService taskService;
 @Override @Transactional public StudentDeliverySubmissionDO submit(StudentDeliverySubmissionReqVO req) {
  StudentDeliveryStageDO stage=stageMapper.selectByIdForUpdate(req.getStageId(), TenantContextHolder.getRequiredTenantId()); if(stage==null||!"PENDING".equals(stage.getStatus())) throw new IllegalStateException("阶段任务当前不可提交");
  Long operator = SecurityFrameworkUtils.getLoginUserId();
  if (operator == null || (!operator.equals(stage.getDirectorUserId()))) throw new IllegalStateException("当前用户无权提交该阶段");
  StudentDeliverySubmissionDO old=submissionMapper.selectOne(new LambdaQueryWrapper<StudentDeliverySubmissionDO>().eq(StudentDeliverySubmissionDO::getStageId,req.getStageId())); if(old!=null)return old;
  LocalDateTime submittedAt=LocalDateTime.now(); StudentDeliverySubmissionDO row=new StudentDeliverySubmissionDO().setStageId(req.getStageId()).setTemplateVersionId(req.getTemplateVersionId()).setFieldValuesJson(req.getFieldValuesJson()).setDictionarySnapshotJson(req.getDictionarySnapshotJson()).setAttachmentSnapshotJson(req.getAttachmentSnapshotJson()).setSubmittedBy(operator).setSubmittedAt(submittedAt); submissionMapper.insert(row);
  if (stageMapper.update(null,new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<StudentDeliveryStageDO>().eq(StudentDeliveryStageDO::getId,stage.getId()).eq(StudentDeliveryStageDO::getStatus,"PENDING").set(StudentDeliveryStageDO::getStatus,"COMPLETED").set(StudentDeliveryStageDO::getCompletedAt,submittedAt).set(StudentDeliveryStageDO::getCompletedBy,operator)) != 1) throw new IllegalStateException("阶段提交并发冲突");
  taskService.completeByKey("student-delivery:" + stage.getPlanId() + ":" + stage.getStageCode(), submittedAt);
  planService.createNextStages(stage.getPlanId(), stage.getAccountId(), stage.getDirectorUserId(), stage.getStageCode(), submittedAt); return row;
 }
}
