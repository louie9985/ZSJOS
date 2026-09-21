package cn.iocoder.yudao.module.zsjos.service.delivery;

import static cn.iocoder.yudao.module.zsjos.enums.MediaNotificationScenes.*;
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
     @Resource private cn.iocoder.yudao.module.zsjos.service.media.MediaCollaborationNotifyPublisher collaborationNotify;
@Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper accounts;
 @Resource private DeliveryPositioningSource sources;
 @Resource private StudentDeliveryPlanMapper plans;
 @Resource private StudentDeliverySubmissionMapper submissionMapper; @Resource private StudentDeliveryStageMapper stageMapper;
 @Resource private StudentDeliveryPlanService planService;
 @Resource private BusinessTaskCommandService taskService;
 @Override @Transactional
 @cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission(bizType="student-delivery-stage",bizId="#req.stageId",action="submit")
 public StudentDeliverySubmissionDO submit(StudentDeliverySubmissionReqVO req) {
  var stage=stageMapper.selectByIdForUpdate(req.getStageId(),TenantContextHolder.getRequiredTenantId());
  Long operator=SecurityFrameworkUtils.getLoginUserId();
  if(stage==null || !java.util.Objects.equals(operator,stage.getDirectorUserId())) throw error(StudentDeliveryErrors.DEFER_PERMISSION_DENIED);
  var old=submissionMapper.selectOne(new LambdaQueryWrapper<StudentDeliverySubmissionDO>().eq(StudentDeliverySubmissionDO::getStageId,req.getStageId()).last("LIMIT 1"));
  if(old!=null) {
   if(!java.util.Objects.equals(old.getIdempotencyKey(),req.getIdempotencyKey()) || !java.util.Objects.equals(old.getFieldValuesJson(),req.getFieldValuesJson())) throw error(StudentDeliveryErrors.DEFER_CONFLICT);
   return old;
  }
  var plan=plans.selectById(stage.getPlanId());
  if(plan==null || !"ACTIVE".equals(plan.getStatus()) || !java.util.Set.of("PENDING","OVERDUE").contains(stage.getStatus()) || "S6".equals(stage.getStageCode()) || !java.util.Objects.equals(stage.getVersion(),req.getVersion())) throw error(StudentDeliveryErrors.DEFER_CONFLICT);
  java.util.Map<String,Object> form;
  try { form=DeliveryPositioningSource.parse(req.getFieldValuesJson()); } catch(RuntimeException e) {throw error(StudentDeliveryErrors.CYCLE_INVALID);}
  if(!"是".equals(form.get("deliveryCompleted")) || !(form.get("diagnosis") instanceof String text) || text.isBlank() || text.length()>10000) throw error(StudentDeliveryErrors.CYCLE_INVALID);
  var frozen=sources.snapshot(sources.latest(accounts.selectById(stage.getAccountId())),stage.getStageCode());
  if(!(frozen.get("agreement") instanceof String agreement) || agreement.isBlank()) throw error(StudentDeliveryErrors.SOURCE_REQUIRED);
  LocalDateTime now=LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
  var row=new StudentDeliverySubmissionDO().setStageId(stage.getId()).setTemplateVersionId(req.getTemplateVersionId()).setFieldValuesJson(req.getFieldValuesJson()).setDictionarySnapshotJson("{}")
   .setAttachmentSnapshotJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(frozen)).setSubmittedBy(operator).setSubmittedAt(now).setIdempotencyKey(req.getIdempotencyKey());
  submissionMapper.insert(row);
  stage.setStatus("COMPLETED").setCompletedAt(now).setCompletedBy(operator).setVersion(stage.getVersion()+1);stageMapper.updateById(stage);
  taskService.completeByKey("student-delivery:"+stage.getPlanId()+":"+stage.getStageCode(),now);
  collaborationNotify.account(STUDENT_DELIVERY_COMPLETED, accounts.selectById(stage.getAccountId()), operator,
      "delivery-completed:"+stage.getId(), java.util.Map.of("stageCode", stage.getStageCode()));
  planService.createNextStages(stage.getPlanId(),stage.getAccountId(),stage.getDirectorUserId(),stage.getStageCode(),now);return row;
 }
 private RuntimeException error(cn.iocoder.yudao.framework.common.exception.ErrorCode code) {return cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(code);}
}
