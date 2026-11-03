package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoTaskVO.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.*;

@Service
public class StudentInfoTaskService {
    @Resource private StudentInfoTaskMapper tasks;
    @Resource private SalesOrderMapper orders;
    @Resource private SalesOrderApprovalRoundMapper approvalRounds;
    @Resource private SalesOrderItemMapper items;
    @Resource private StudentInfoFormMapper forms;
    @Resource private StudentInfoFormValueMapper answers;
    @Resource private StudentArchiveRevisionMapper revisions;
    @Resource private StudentInfoFormService collection;
    @Resource private StudentInfoImageService images;
    @Resource private StudentInfoTaskPermissionProvider permission;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCaseMapper registrationCases;

    @ZsjosPermission(bizType="student-info",bizId="#caseId",action="read")
    public Row byRegistration(Long caseId) {
        if(!permission.canList(getLoginUserId())) throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED);
        var item=registrationCases.selectById(caseId);
        if(item==null || item.getOrderItemId()==null) throw exception(FIELD_INVALID,"当前报名任务没有明确的订单商品，无法定位收集任务");
        var task=tasks.byItem(item.getOrderItemId());
        if(task==null) throw exception(FIELD_INVALID,"当前报名任务尚未关联收集任务");
        if(!Objects.equals(task.getOrderId(),item.getOrderId())) throw exception(LINK_INVALID);
        return row(task);
    }

    /** Called inside order submission, after its items exist; order lock serializes retries. */
    @Transactional(rollbackFor=Exception.class)
    public void ensureForOrder(Long orderId) {
        var order=orders.selectByIdForUpdate(orderId,TenantContextHolder.getRequiredTenantId());
        if (order==null || order.getPersonId()==null || !"pending_approval".equals(order.getStatus())) throw exception(CONFIG_INVALID);
        for (var item:items.selectListByOrderId(orderId)) {
            if (tasks.byItem(item.getId())!=null) continue;
            var task=new StudentInfoTaskDO(); task.setOrderId(orderId); task.setOrderItemId(item.getId());
            task.setPersonId(order.getPersonId()); task.setVersion(0); tasks.insert(task);
        }
    }
    public PageResult<Row> page(Query query) {
        if (!permission.canList(getLoginUserId())) throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED);
        var filter=new LambdaQueryWrapperX<StudentInfoTaskDO>().eqIfPresent(StudentInfoTaskDO::getPersonId,query.getPersonId());
        if (query.getOrderStatus()!=null) filter.apply("EXISTS (SELECT 1 FROM zsjos_order o WHERE o.id=zsjos_student_info_task.order_id AND o.tenant_id=zsjos_student_info_task.tenant_id AND o.deleted=0 AND o.status={0})",query.getOrderStatus());
        String cancelledOrder = "EXISTS (SELECT 1 FROM zsjos_order o WHERE o.id=zsjos_student_info_task.order_id AND o.tenant_id=zsjos_student_info_task.tenant_id AND o.deleted=0 AND (o.status IN ('revision_required','superseded') OR (o.status='terminated' AND EXISTS (SELECT 1 FROM zsjos_order_approval_round r WHERE r.order_id=o.id AND r.tenant_id=o.tenant_id AND r.deleted=0 AND r.status='rejected'))))";
        if ("CANCELLED".equals(query.getStatus())) filter.apply(cancelledOrder);
        else if (query.getStatus()!=null) filter.apply("NOT " + cancelledOrder);
        if ("PENDING".equals(query.getStatus())) filter.isNull(StudentInfoTaskDO::getFormId);
        else if (query.getStatus()!=null && !"CANCELLED".equals(query.getStatus())) {
            String condition=switch(query.getStatus()) {
                case "UNSENT" -> "f.status='DRAFT' AND f.sent_at IS NULL AND f.revoked_at IS NULL AND f.expires_at>NOW()";
                case "DRAFT" -> "f.status='DRAFT' AND f.sent_at IS NOT NULL AND f.revoked_at IS NULL AND f.expires_at>NOW()";
                case "REUSED" -> "f.status='SUBMITTED' AND f.submit_source='reuse'";
                case "SUBMITTED" -> "f.status='SUBMITTED' AND (f.submit_source IS NULL OR f.submit_source<>'reuse')";
                default -> throw exception(FIELD_INVALID,"收集状态");
            };
            filter.apply("EXISTS (SELECT 1 FROM zsjos_student_info_form f WHERE f.id=zsjos_student_info_task.form_id AND f.tenant_id=zsjos_student_info_task.tenant_id AND f.deleted=0 AND "+condition+")");
        }
        if (query.getKeyword()!=null && !query.getKeyword().isBlank()) filter.apply(
                "EXISTS (SELECT 1 FROM zsjos_order o WHERE o.id=zsjos_student_info_task.order_id AND o.tenant_id=zsjos_student_info_task.tenant_id AND o.deleted=0 AND (o.order_no LIKE {0} OR o.student_name LIKE {0}))", "%"+query.getKeyword().trim()+"%");
        var page=tasks.selectPage(query,filter.orderByDesc(StudentInfoTaskDO::getId));
        return new PageResult<>(page.getList().stream().map(this::row).toList(),page.getTotal());
    }
    @ZsjosPermission(bizType="student-info-task",bizId="#id",action="read")
    public Row detail(Long id) { return row(tasks.selectById(id)); }
    @ZsjosPermission(bizType="student-info-task",bizId="#id",action="read")
    public StudentInfoVO.Detail information(Long id,boolean sensitive) {
        if (sensitive && !collection.canReadSensitive()) throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED);
        var task=tasks.selectById(id); return projectInformation(boundForm(task),sensitive);
    }
    private StudentInfoVO.Detail projectInformation(StudentInfoFormDO form,boolean sensitive) {
        var result=new StudentInfoVO.Detail(); result.setCanReadSensitive(collection.canReadSensitive()); result.setCanExport(false);
        result.setStatus(form==null ? "NONE" : StudentInfoFormService.state(form));
        if (form==null) return result;
        result.setId(form.getId()); result.setCreatedAt(form.getCreateTime()); result.setSubmittedAt(form.getSubmittedAt());
        result.setExpiresAt(form.getExpiresAt()); result.setFields(collection.definitions(form));
        if (!"SUBMITTED".equals(form.getStatus())) {
            if (form.getDraftSnapshot()!=null) {
                var draft=JsonUtils.parseObject(form.getDraftSnapshot(),StudentInfoVO.Draft.class);
                Map<String,String> display=new LinkedHashMap<>();
                for (var field:result.getFields()) {
                    String value=draft.getValues().get(field.getKey());
                    var selection=draft.getSelections().get(field.getKey());
                    if(selection!=null) value=selection.getLabel();
                    if(value!=null) display.put(field.getKey(),!sensitive && Boolean.TRUE.equals(field.getSensitive()) ? "已隐藏" : value);
                }
                result.setValues(display);
            }
            result.setImages(images.list(form.getId())); return result;
        }
        var revision=revisions.latest(form.getId()); Map<String,String> display=new LinkedHashMap<>();
        for (var answer:content(form,revision)) {
            String value=answer.getValueText()!=null ? answer.getValueText() : answer.getValueLabelSnapshot()!=null ? answer.getValueLabelSnapshot() : answer.getAreaLabelSnapshot();
            if (value==null) value="";
            if (!sensitive && Boolean.TRUE.equals(answer.getSensitive()) && !value.isEmpty()) value="mobile".equals(answer.getFieldKey())
                    ? cn.hutool.core.util.DesensitizedUtil.mobilePhone(value) : "已隐藏";
            display.put(answer.getFieldKey(),value);
        }
        result.setValues(display); result.setImages(materials(form,revision)); return result;
    }
    @ZsjosPermission(bizType="student-info-task",bizId="#id",action="read")
    public byte[] image(Long id,Long imageId) {
        if (!collection.canReadSensitive()) throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED);
        var form=boundForm(tasks.selectById(id));
        if (form==null || materials(form,"SUBMITTED".equals(form.getStatus()) ? revisions.latest(form.getId()) : null).stream().noneMatch(i->Objects.equals(i.getId(),imageId))) throw exception(LINK_INVALID);
        return images.archiveRead(form.getId(),imageId);
    }
    @ZsjosPermission(bizType="student-info-task",bizId="#id",action="read")
    public StudentInfoVO.Detail archive(Long id,Long formId,Integer revision,boolean sensitive) {
        if(sensitive && !collection.canReadSensitive()) throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED);
        return projectInformation(currentArchive(id,formId,revision),sensitive);
    }
    @ZsjosPermission(bizType="student-info-task",bizId="#id",action="read")
    public byte[] archiveImage(Long id,Long formId,Integer revision,Long imageId) {
        if(!collection.canReadSensitive()) throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED);
        var form=currentArchive(id,formId,revision);
        if(materials(form,revisions.latest(form.getId())).stream().noneMatch(i->Objects.equals(i.getId(),imageId))) throw exception(LINK_INVALID);
        return images.archiveRead(form.getId(),imageId);
    }
    private StudentInfoFormDO currentArchive(Long id,Long formId,Integer revision) {
        var task=tasks.selectById(id);if(task==null)throw exception(LINK_INVALID);
        var form=forms.currentArchive(task.getPersonId());
        if(form==null || !Objects.equals(form.getId(),formId) || !Objects.equals(form.getPersonId(),task.getPersonId())) throw exception(VERSION_CONFLICT);
        var latest=revisions.latest(form.getId());
        if(!Objects.equals(revision,latest==null?0:latest.getRevision()))throw exception(VERSION_CONFLICT);
        return form;
    }
    private StudentInfoFormDO boundForm(StudentInfoTaskDO task) {
        if (task==null) throw exception(LINK_INVALID);
        var form=task.getFormId()==null ? null : forms.selectById(task.getFormId());
        if (task.getFormId()!=null && (form==null || !Objects.equals(task.getPersonId(),form.getPersonId()) || !Objects.equals(task.getOrderItemId(),form.getOrderItemId()))) throw exception(LINK_INVALID);
        return form;
    }
    private Row row(StudentInfoTaskDO task) {
        var order=orders.selectById(task.getOrderId()); if (order==null) throw exception(LINK_INVALID);
        var row=new Row(); row.setId(task.getId()); row.setOrderId(task.getOrderId()); row.setOrderItemId(task.getOrderItemId());
        row.setPersonId(task.getPersonId()); row.setFormId(task.getFormId()); row.setVersion(task.getVersion());
        var registration=registrationCases.selectByOrderItemId(task.getOrderItemId());
        if(registration!=null && Objects.equals(registration.getOrderId(),task.getOrderId())) row.setRegistrationCaseId(registration.getId());
        row.setOrderNo(order.getOrderNo()); row.setStudentName(order.getStudentName()); row.setOrderStatus(order.getStatus());
        row.setCanProcess(open(order));
        var form=task.getFormId()==null ? null : forms.selectById(task.getFormId());
        var target=form==null ? collection.prepareTaskForm(task) : form;
        row.setProductName(target.getProductName());
        var orderItem = items.selectById(task.getOrderItemId());
        if (orderItem != null && Objects.equals(orderItem.getOrderId(), task.getOrderId())) {
            var snapshot = cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot.readHistoricalQuietly(orderItem.getProductSnapshot());
            if (snapshot != null) row.setSpecs(snapshot.displaySpecs());
        }
        row.setStatus(form==null ? "PENDING" : "reuse".equals(form.getSubmitSource()) ? "REUSED" : "SUBMITTED".equals(form.getStatus()) ? "SUBMITTED" : !"DRAFT".equals(StudentInfoFormService.state(form)) ? StudentInfoFormService.state(form) : form.getSentAt()==null ? "UNSENT" : "DRAFT");
        if (cancelled(order)) row.setStatus("CANCELLED");
        var candidates=new ArrayList<Candidate>();
        var source=forms.currentArchive(task.getPersonId());
        if(source!=null) {
            var revision=revisions.latest(source.getId()); var candidate=new Candidate();
            candidate.setFormId(source.getId()); candidate.setProductName(source.getProductName()); candidate.setRevision(revision==null ? 0 : revision.getRevision());
            candidate.setMissing(StudentInfoReusePolicy.missing(collection.definitions(target),collection.definitions(source),content(source,revision),materials(source,revision)));
            candidates.add(candidate);
        }
        row.setCandidates(candidates); row.setArchiveStatus(candidates.isEmpty() ? "NONE" : candidates.stream().anyMatch(c->c.getMissing().isEmpty()) ? "COMPLETE" : "INCOMPLETE");
        return row;
    }
    private List<StudentInfoFormValueDO> content(StudentInfoFormDO source,StudentArchiveRevisionDO revision) {
        return revision==null ? answers.byForm(source.getId()) : JsonUtils.parseArray(revision.getAnswersJson(),StudentInfoFormValueDO.class);
    }
    private List<StudentInfoVO.Image> materials(StudentInfoFormDO source,StudentArchiveRevisionDO revision) {
        return revision==null ? images.list(source.getId()) : images.archiveList(source.getId(),JsonUtils.parseArray(revision.getImageIdsJson(),Long.class));
    }
    // Resubmission creates a separate order/items. Old tasks stay cancelled without revoking public forms.
    private boolean cancelled(SalesOrderDO order) {
        if (Set.of("revision_required","superseded").contains(order.getStatus())) return true;
        return "terminated".equals(order.getStatus()) && approvalRounds.selectCount(
                new LambdaQueryWrapperX<cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderApprovalRoundDO>()
                        .eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderApprovalRoundDO::getOrderId, order.getId())
                        .eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderApprovalRoundDO::getStatus, "rejected")) > 0;
    }
    static boolean open(SalesOrderDO order) { return order!=null && Set.of("pending_approval","effective").contains(order.getStatus()); }
    private StudentInfoTaskDO lock(Long id) {
        var task=tasks.selectById(id); if (task==null) throw exception(LINK_INVALID);
        var order=orders.selectByIdForUpdate(task.getOrderId(),TenantContextHolder.getRequiredTenantId());
        if (!open(order)) throw exception(LINK_EXPIRED);
        task=tasks.lock(id);
        if (!Objects.equals(task.getPersonId(),order.getPersonId())) throw exception(CONFIG_INVALID);
        return task;
    }
    @ZsjosPermission(bizType="student-info-task",bizId="#id",action="create")
    @Transactional(rollbackFor=Exception.class)
    public GenerationConfig generationConfig(Long id) {
        var task=lock(id);
        if(task.getFormId()!=null) throw exception(VERSION_CONFLICT);
        var form=collection.prepareTaskForm(task);
        var result=new GenerationConfig(); result.setConfigVersionId(form.getConfigVersionId());
        result.setFields(collection.definitions(form));
        result.setSelectableMaterialKeys(StudentInfoMaterialSelection.selectable(result.getFields()));
        var order=orders.selectById(task.getOrderId());
        if(order==null) throw exception(LINK_INVALID);
        boolean hasGift=!cn.iocoder.yudao.module.zsjos.service.order.SalesOrderGiftSnapshot.read(order.getGiftItems()).isEmpty();
        result.setDefaultSelectedKeys(result.getSelectableMaterialKeys().stream()
                .filter(key -> hasGift && "gift_shipping_address".equals(key)).toList());
        return result;
    }
    @ZsjosPermission(bizType="student-info-task",bizId="#id",action="link-read")
    @Transactional(rollbackFor=Exception.class)
    public StudentInfoVO.Link link(Long id) {
        var task=lock(id); return collection.taskLink(task.getFormId()==null ? null : forms.selectById(task.getFormId()),task);
    }
    @ZsjosPermission(bizType="student-info-task",bizId="#id",action="sent")
    @Transactional(rollbackFor=Exception.class)
    public Row sent(Long id,Command request) {
        var task=lock(id);
        String fingerprint=cn.hutool.crypto.digest.DigestUtil.sha256Hex("sent:"+getLoginUserId());
        if (Objects.equals(task.getLastCommandKey(),request.getIdempotencyKey())) {
            if (!Objects.equals(task.getLastCommandFingerprint(),fingerprint)) throw exception(VERSION_CONFLICT);
            return row(task);
        }
        if (!Objects.equals(task.getVersion(),request.getVersion()) || task.getFormId()==null) throw exception(VERSION_CONFLICT);
        var form=forms.lock(task.getFormId());
        if (!Set.of("DRAFT","SUBMITTED").contains(StudentInfoFormService.state(form))) throw exception(LINK_EXPIRED);
        if (form.getSentAt()==null) {
            form.setSentAt(java.time.LocalDateTime.now()); form.setSentByUserId(getLoginUserId()); forms.updateById(form);
            task.setVersion(task.getVersion()+1);
        }
        task.setLastCommandKey(request.getIdempotencyKey()); task.setLastCommandFingerprint(fingerprint);
        task.setOperatorUserId(getLoginUserId()); tasks.updateById(task);
        return row(task);
    }
    @ZsjosPermission(bizType="student-info-task",bizId="#id",action="create")
    @Transactional(rollbackFor=Exception.class)
    public Row generate(Long id,Command request) { return create(id,request,false); }
    @ZsjosPermission(bizType="student-info-task",bizId="#id",action="regenerate")
    @Transactional(rollbackFor=Exception.class)
    public Row regenerate(Long id,Command request) { return changeLink(id,request,false); }
    @ZsjosPermission(bizType="student-info-task",bizId="#id",action="revoke")
    @Transactional(rollbackFor=Exception.class)
    public Row revoke(Long id,Command request) { return changeLink(id,request,true); }
    private Row changeLink(Long id,Command request,boolean revoke) {
        var task=lock(id); String fingerprint=cn.hutool.crypto.digest.DigestUtil.sha256Hex((revoke ? "revoke:" : "regenerate:")+getLoginUserId());
        if (Objects.equals(task.getLastCommandKey(),request.getIdempotencyKey())) {
            if (!Objects.equals(task.getLastCommandFingerprint(),fingerprint)) throw exception(VERSION_CONFLICT);
            return row(task);
        }
        if (!Objects.equals(task.getVersion(),request.getVersion())) throw exception(VERSION_CONFLICT);
        var located=boundForm(task); if (located==null) throw exception(LINK_INVALID);
        var form=forms.lock(located.getId()); if ("SUBMITTED".equals(form.getStatus())) throw exception(ALREADY_SUBMITTED);
        if (revoke) {
            forms.update(null,new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<StudentInfoFormDO>()
                    .eq(StudentInfoFormDO::getId,form.getId()).set(StudentInfoFormDO::getStatus,"REVOKED")
                    .set(StudentInfoFormDO::getRevokedAt,java.time.LocalDateTime.now())
                    .set(StudentInfoFormDO::getSentAt,null).set(StudentInfoFormDO::getSentByUserId,null));
        } else collection.rotateTaskForm(form);
        task.setVersion(task.getVersion()+1);
        task.setLastCommandKey(request.getIdempotencyKey()); task.setLastCommandFingerprint(fingerprint);
        task.setOperatorUserId(getLoginUserId()); tasks.updateById(task); return row(task);
    }
    @ZsjosPermission(bizType="student-info-task",bizId="#id",action="reuse")
    @Transactional(rollbackFor=Exception.class)
    public Row reuse(Long id,Command request) { return create(id,request,true); }
    private Row create(Long id,Command request,boolean reuse) {
        var task=lock(id);
        var materialKeys=request.getMaterialKeys()==null ? List.<String>of() : request.getMaterialKeys();
        if(reuse && (request.getMaterialKeys()!=null || request.getConfigVersionId()!=null)) throw exception(FIELD_INVALID,"复用不支持调整材料");
        String fingerprint=cn.hutool.crypto.digest.DigestUtil.sha256Hex(reuse+":"+request.getSourceFormId()+":"+request.getSourceRevision()+":"+getLoginUserId()
                +(!reuse ? ":"+request.getConfigVersionId()+":"+JsonUtils.toJsonString(materialKeys.stream().sorted().toList()) : ""));
        if (Objects.equals(task.getLastCommandKey(),request.getIdempotencyKey())) {
            if (!Objects.equals(task.getLastCommandFingerprint(),fingerprint)) throw exception(VERSION_CONFLICT);
            return row(task);
        }
        if (!Objects.equals(task.getVersion(),request.getVersion()) || !reuse && task.getFormId()!=null) throw exception(VERSION_CONFLICT);
        StudentInfoFormDO replaced=null;
        if(reuse && task.getFormId()!=null) {
            replaced=forms.lock(task.getFormId());
            if(replaced==null || !Objects.equals(replaced.getPersonId(),task.getPersonId()) || !Objects.equals(replaced.getOrderItemId(),task.getOrderItemId()))throw exception(LINK_INVALID);
            if("SUBMITTED".equals(replaced.getStatus()))throw exception(ALREADY_SUBMITTED);
        }
        var target=replaced==null ? collection.prepareTaskForm(task) : new StudentInfoFormDO();
        if(replaced!=null) {
            // Reusing an archive must not change this task's already-issued field contract.
            target.setPersonId(task.getPersonId());target.setOrderItemId(task.getOrderItemId());target.setTenantId(task.getTenantId());
            target.setSalesUserId(getLoginUserId());target.setProductId(replaced.getProductId());target.setProductName(replaced.getProductName());
            target.setConfigVersionId(replaced.getConfigVersionId());target.setFieldsSnapshot(replaced.getFieldsSnapshot());
            target.setCategoryPathSnapshot(replaced.getCategoryPathSnapshot());target.setDraftRevision(0L);
        }
        if(!reuse) {
            if(request.getMaterialKeys()!=null && !Objects.equals(request.getConfigVersionId(),target.getConfigVersionId())) throw exception(VERSION_CONFLICT);
            target.setFieldsSnapshot(JsonUtils.toJsonString(StudentInfoMaterialSelection.select(collection.definitions(target),materialKeys)));
        }
        StudentInfoFormDO source=null; StudentArchiveRevisionDO revision=null;
        if (request.getSourceFormId()!=null || !reuse) {
            var current=forms.currentArchive(task.getPersonId());
            if(request.getSourceFormId()!=null && (current==null || !Objects.equals(current.getId(),request.getSourceFormId()))) throw exception(VERSION_CONFLICT);
            if(current!=null) {
            source=forms.lock(current.getId());
            if (source==null || !Objects.equals(source.getPersonId(),task.getPersonId()) || !"SUBMITTED".equals(source.getStatus())) throw exception(LINK_INVALID);
            revision=revisions.latest(source.getId());
            if (request.getSourceFormId()!=null && !Objects.equals(request.getSourceRevision(),revision==null ? 0 : revision.getRevision())) throw exception(VERSION_CONFLICT);
            }
        }
        if (reuse && source==null) throw exception(FIELD_INVALID,"请选择已有资料");
        var targetFields=collection.definitions(target);
        if (source!=null) {
            var missing=StudentInfoReusePolicy.missing(targetFields,collection.definitions(source),content(source,revision),materials(source,revision));
            if (reuse && !missing.isEmpty()) throw exception(FIELD_INVALID,"缺少本次必填资料："+String.join("、",missing));
            target.setDraftSnapshot(JsonUtils.toJsonString(StudentInfoReusePolicy.prefill(targetFields,collection.definitions(source),content(source,revision))));
        }
        collection.issueTaskForm(target);
        if (source!=null) {
            images.copyForCollection(source,target,materials(source,revision),targetFields);
            task.setSourceFormId(source.getId()); task.setSourceRevision(revision==null ? 0 : revision.getRevision());
        }
        if (reuse) {
            var sourceFields=collection.definitions(source);
            for (var value:content(source,revision)) {
                if (targetFields.stream().noneMatch(f->Objects.equals(f.getKey(),value.getFieldKey())
                        && sourceFields.stream().anyMatch(s->StudentInfoReusePolicy.compatible(f,s)))) continue;
                var copy=JsonUtils.parseObject(JsonUtils.toJsonString(value),StudentInfoFormValueDO.class);
                copy.setId(null); copy.setFormId(target.getId()); answers.insert(copy);
            }
            target.setStatus("SUBMITTED"); target.setSubmitSource("reuse"); target.setSubmittedAt(java.time.LocalDateTime.now()); forms.updateById(target);
        }
        if(replaced!=null) forms.update(null,new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<StudentInfoFormDO>()
                .eq(StudentInfoFormDO::getId,replaced.getId()).set(StudentInfoFormDO::getStatus,"REVOKED")
                .set(StudentInfoFormDO::getRevokedAt,java.time.LocalDateTime.now()));
        task.setFormId(target.getId()); task.setVersion(task.getVersion()+1); task.setOperatorUserId(getLoginUserId());
        task.setLastCommandKey(request.getIdempotencyKey()); task.setLastCommandFingerprint(fingerprint); tasks.updateById(task);
        return row(task);
    }
}
