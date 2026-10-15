package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentContactRecordMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.StudentInfoFormMapper;
import cn.iocoder.yudao.module.zsjos.service.order.*;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadObjectPermissionService;
import cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactReadPolicy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StudentServiceSummaryReader {
    @Resource private BusinessTaskMapper tasks;
    @Resource private StudentContactRecordMapper contacts;
    @Resource private DeliveryClassMapper classes;
    @Resource private SalesOrderMapper orders;
    @Resource private SalesOrderApprovalRoundMapper rounds;
    @Resource private SalesOrderObjectPermissionService orderPermission;
    @Resource private LeadMapper leads;
    @Resource private LeadObjectPermissionService leadPermission;
    @Resource private StudentInfoFormMapper forms;
    @Resource private PermissionApi permission;
    @Resource private AdminUserApi users;

    public Map<Long, StudentServiceSummaryVO> read(Long actor, List<ServiceRelationDO> relations, LocalDateTime now) {
        if (relations.isEmpty()) return Map.of();
        var ids = relations.stream().map(ServiceRelationDO::getId).toList();
        var taskRows = tasks.selectList(new LambdaQueryWrapper<BusinessTaskDO>()
                .select(BusinessTaskDO::getId, BusinessTaskDO::getBizId, BusinessTaskDO::getTaskType, BusinessTaskDO::getStatus, BusinessTaskDO::getDueAt)
                .eq(BusinessTaskDO::getBizType, "student_service").eq(BusinessTaskDO::getStatus, "pending")
                .in(BusinessTaskDO::getBizId, ids).in(BusinessTaskDO::getTaskType, StudentContactReadPolicy.TASK_TYPES));
        var byTask = taskRows.stream().collect(Collectors.groupingBy(BusinessTaskDO::getBizId));
        // Fetch only each service's latest record, not its full history or attachments.
        var latest = contacts.selectList(new LambdaQueryWrapper<StudentContactRecordDO>()
                .select(StudentContactRecordDO::getId, StudentContactRecordDO::getServiceRelationId, StudentContactRecordDO::getContactType,
                        StudentContactRecordDO::getSuccessful, StudentContactRecordDO::getUnsuccessfulReasonLabelSnapshot,
                        StudentContactRecordDO::getRemark, StudentContactRecordDO::getNextContactAt, StudentContactRecordDO::getOperatorUserId, StudentContactRecordDO::getSubmittedAt)
                .in(StudentContactRecordDO::getServiceRelationId, ids)
                .apply("NOT EXISTS (SELECT 1 FROM zsjos_student_contact_record newer WHERE newer.service_relation_id=zsjos_student_contact_record.service_relation_id AND newer.tenant_id=zsjos_student_contact_record.tenant_id AND newer.deleted=0 AND (newer.submitted_at > zsjos_student_contact_record.submitted_at OR (newer.submitted_at <=> zsjos_student_contact_record.submitted_at) AND newer.id > zsjos_student_contact_record.id OR zsjos_student_contact_record.submitted_at IS NULL AND newer.submitted_at IS NOT NULL))"));
        var contactMap = index(latest, StudentContactRecordDO::getServiceRelationId);
        var userIds = latest.stream().map(StudentContactRecordDO::getOperatorUserId).filter(Objects::nonNull).distinct().toList();
        var userMap = userIds.isEmpty() ? Map.<Long,cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO>of() : users.getUserMap(userIds);
        var classIds = relations.stream().map(ServiceRelationDO::getClassId).filter(Objects::nonNull).distinct().toList();
        var classMap = index(classIds.isEmpty() ? List.<cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO>of() : classes.selectBatchIds(classIds), cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO::getId);
        var orderIds = relations.stream().map(ServiceRelationDO::getOrderId).filter(Objects::nonNull).distinct().toList();
        var orderRows = orderIds.isEmpty() ? List.<cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO>of() : orders.selectBatchIds(orderIds);
        var orderMap = index(orderRows, cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO::getId);
        var readableOrders = readableOrders(orderRows, actor);
        var roundIds = orderRows.stream().filter(o -> readableOrders.contains(o.getId())).map(o -> o.getCurrentApprovalRoundId()).filter(Objects::nonNull).distinct().toList();
        var roundMap = index(roundIds.isEmpty() ? List.<cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderApprovalRoundDO>of() : rounds.selectBatchIds(roundIds), cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderApprovalRoundDO::getId);
        var leadIds = orderRows.stream().map(o -> o.getLeadId()).filter(Objects::nonNull).distinct().toList();
        Set<Long> readableInfo = permission.hasAnyPermissions(actor, "zsjos:student-info-form:read") && !leadIds.isEmpty()
                ? leadPermission.filterReadableDetails(leads.selectBatchIds(leadIds), actor) : Set.of();
        // Metadata-only projection deliberately excludes token hashes, field values and attachments.
        var formRows = readableInfo.isEmpty() ? List.<StudentInfoFormDO>of() : forms.selectList(new LambdaQueryWrapper<StudentInfoFormDO>()
                .select(StudentInfoFormDO::getId, StudentInfoFormDO::getLeadId, StudentInfoFormDO::getStatus, StudentInfoFormDO::getSubmittedAt, StudentInfoFormDO::getRevokedAt, StudentInfoFormDO::getExpiresAt)
                .in(StudentInfoFormDO::getLeadId, readableInfo).orderByDesc(StudentInfoFormDO::getId));
        Map<Long, StudentInfoFormDO> formMap = new HashMap<>();
        for (var form : formRows) {
            var previous = formMap.get(form.getLeadId());
            if (previous == null || !"SUBMITTED".equals(previous.getStatus()) && "SUBMITTED".equals(form.getStatus())) formMap.put(form.getLeadId(), form);
        }
        Map<Long, StudentServiceSummaryVO> result = new LinkedHashMap<>();
        for (var relation : relations) {
            var value = new StudentServiceSummaryVO();
            value.setDeliveryStage(relation.getDeliveryStage() == null || relation.getDeliveryStage().isBlank() ? "first_contact" : relation.getDeliveryStage());
            value.setDeliveryStageLabel(StudentContactReadPolicy.stageLabel(value.getDeliveryStage()));
            value.setExamDate(relation.getExamDate());
            value.setPausedAt(relation.getPausedAt()); value.setPauseReason(relation.getPauseReason());
            value.setCompletedAt(relation.getCompletedAt()); value.setTerminatedAt(relation.getTerminatedAt()); value.setTerminationReason(relation.getTerminationReason());
            var task = StudentContactReadPolicy.currentTask(byTask.getOrDefault(relation.getId(), List.of()));
            if (task != null) {
                var current = new StudentContactContextRespVO.CurrentTaskVO();
                current.setId(task.getId()); current.setType(task.getTaskType()); current.setStatus(task.getStatus()); current.setDueAt(task.getDueAt());
                current.setOverdue(task.getDueAt() != null && task.getDueAt().isBefore(now)); value.setCurrentTask(current);
            }
            var contact = contactMap.get(relation.getId());
            if (contact != null) {
                var record = BeanUtils.toBean(contact, StudentContactRecordRespVO.class);
                record.setUnsuccessfulReasonLabel(contact.getUnsuccessfulReasonLabelSnapshot());
                var user = userMap.get(contact.getOperatorUserId()); record.setOperatorUserName(user == null ? null : user.getNickname());
                value.setLatestContact(record);
            }
            var clazz = classMap.get(relation.getClassId());
            if (clazz != null) { value.setClassNo(clazz.getClassNo()); value.setSystemClass(clazz.getSystemClass()); value.setClassDepartment(clazz.getDeptNameSnapshot()); value.setClassExamSchedule(clazz.getExamScheduleSnapshot()); }
            var handoff = new StudentServiceSummaryVO.OrderHandoff(); value.setOrderHandoff(handoff);
            var order = orderMap.get(relation.getOrderId());
            handoff.setAccess(relation.getOrderId() == null ? "absent" : "denied");
            if (order != null && readableOrders.contains(order.getId())) {
                handoff.setAccess("readable"); handoff.setOrderType(order.getOrderType()); handoff.setStatus(order.getStatus());
                handoff.setFormalOwnerIdentity(order.getFormalOwnerIdentity()); handoff.setSubmittedAt(order.getSubmittedAt()); handoff.setEffectiveAt(order.getEffectiveAt());
                handoff.setClassType(order.getClassType()); handoff.setAgreedExamTime(order.getAgreedExamTime());
                handoff.setStudentSpecialRequirements(order.getStudentSpecialRequirements()); handoff.setRemark(order.getRemark()); handoff.setMaterialDeliveryContact(order.getMaterialDeliveryContact());
                var round = roundMap.get(order.getCurrentApprovalRoundId());
                var snapshot = SalesOrderSnapshot.read(round == null ? null : round.getOrderSnapshot(), order);
                handoff.setStudentNatureLabel(snapshot.label("studentNature")); handoff.setStudentSourceLabel(snapshot.label("studentSource")); handoff.setServicePeriodLabel(snapshot.label("servicePeriod"));
                handoff.setFormalSalesUserName(snapshot.getFormalSales() == null ? null : snapshot.getFormalSales().name());
                handoff.setSubmitterUserName(snapshot.getSubmitter() == null ? null : snapshot.getSubmitter().name());
            }
            var info = new StudentServiceSummaryVO.InfoSubmission(); value.setInfoSubmission(info);
            var leadId = order == null ? null : order.getLeadId();
            info.setAccess(leadId == null ? "absent" : readableInfo.contains(leadId) ? "readable" : "denied");
            if ("readable".equals(info.getAccess())) {
                var form = formMap.get(leadId);
                info.setStatus(form == null ? "NONE" : "DRAFT".equals(form.getStatus()) && (form.getRevokedAt() != null || form.getExpiresAt() == null || !form.getExpiresAt().isAfter(now)) ? "EXPIRED" : form.getStatus());
                info.setSubmittedAt(form == null ? null : form.getSubmittedAt());
            }
            result.put(relation.getId(), value);
        }
        return result;
    }
    private static <T> Map<Long,T> index(List<T> rows, Function<T,Long> key) {
        return rows.stream().collect(Collectors.toMap(key, Function.identity()));
    }
    public Set<Long> readableOrders(Collection<cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO> rows, Long actor) {
        if (!permission.hasAnyPermissions(actor, "zsjos:sales-order:query", "zsjos:sales-order:query-management",
                "zsjos:sales-order:query-team", "zsjos:sales-order:review", "zsjos:sales-order:supervisor-confirm", "zsjos:sales-order:create")) return Set.of();
        return permission.hasTenantReadAllAccess(actor) ? rows.stream().map(o -> o.getId()).collect(Collectors.toSet())
                : orderPermission.filterReadable(rows, actor);
    }
}
