package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionVO.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormValueDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.registration.TeachingServiceLifecycle;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoImageService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.FIELD_INVALID;

@Service
public class ExamCollectionQueryService {
    @Resource private ExamCollectionTaskMapper tasks;
    @Resource private ExamCollectionSubmissionMapper submissions;
    @Resource private ExamCollectionAttachmentMapper attachments;
    @Resource private ExamCollectionEventMapper events;
    @Resource private ExamCollectionMemberMapper members;
    @Resource private ServiceRelationMapper relations;
    @Resource private PersonMapper people;
    @Resource private DeliveryClassMapper classes;
    @Resource private ExamCollectionPermissionProvider objects;
    @Resource private StudentServiceArchiveStore archives;
    @Resource private StudentInfoImageService cipher;
    @Resource private FileApi files;
    @Resource private AdminUserApi users;

    @ZsjosPermission(bizType="exam-collection", bizId="#taskId", action="query")
    public PageResult<Submission> submissions(Long taskId, SubmissionQuery query) {
        require(taskId);
        var page = submissions.selectPage(query, new LambdaQueryWrapperX<ExamCollectionSubmissionDO>()
                .eq(ExamCollectionSubmissionDO::getTaskId, taskId)
                .eqIfPresent(ExamCollectionSubmissionDO::getStatus, query.getStatus())
                .eqIfPresent(ExamCollectionSubmissionDO::getServiceRelationId, query.getServiceRelationId())
                .isNotNull(ExamCollectionSubmissionDO::getSubmittedAt)
                .orderByDesc(ExamCollectionSubmissionDO::getSequenceNo));
        return new PageResult<>(page.getList().stream().map(this::project).toList(), page.getTotal());
    }
    @ZsjosPermission(bizType="exam-collection", bizId="#taskId", action="query")
    public SubmissionDetail submission(Long taskId, Long id) {
        require(taskId); var row = requireSubmission(taskId, id);
        var result = BeanUtils.toBean(project(row), SubmissionDetail.class);
        result.setFields(JsonUtils.parseArray(tasks.selectById(taskId).getFieldsSnapshot(), StudentInfoVO.Field.class));
        var ids = JsonUtils.parseArray(row.getAttachmentIdsJson(), Long.class);
        result.setAttachments(ids.stream().map(fileId -> image(requireFile(row, fileId))).toList());
        return result;
    }
    @ZsjosPermission(bizType="exam-collection", bizId="#taskId", action="query")
    public byte[] attachment(Long taskId, Long submissionId, Long fileId) {
        require(taskId); var row = requireSubmission(taskId, submissionId);
        var ids = JsonUtils.parseArray(row.getAttachmentIdsJson(), Long.class);
        if (!ids.contains(fileId)) throw exception(NOT_FOUND);
        var file = requireFile(row, fileId);
        try { return cipher.decryptPrivateMaterial(files.getFileContent(file.getInfraFileId())); }
        catch (Exception error) { throw exception(FIELD_INVALID, "材料读取失败，请重试"); }
    }
    @ZsjosPermission(bizType="exam-collection", bizId="#taskId", action="query")
    public PageResult<Candidate> candidates(Long taskId, CandidateQuery query) {
        require(taskId);
        var filter = new LambdaQueryWrapperX<ServiceRelationDO>().eqIfPresent(ServiceRelationDO::getClassId, query.getClassId())
                .orderByDesc(ServiceRelationDO::getId);
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String keyword = query.getKeyword().trim();
            filter.and(q -> q.like(ServiceRelationDO::getStudentNameSnapshot, keyword).or().apply(
                    "EXISTS (SELECT 1 FROM zsjos_person p WHERE p.id=zsjos_service_relation.person_id AND p.tenant_id=zsjos_service_relation.tenant_id AND p.deleted=0 AND (LOCATE({0},p.mobile)>0 OR LOCATE({0},p.person_no)>0))", keyword));
        }
        var page = relations.selectPage(query, filter);
        var personIds = page.getList().stream().map(ServiceRelationDO::getPersonId).filter(Objects::nonNull).distinct().toList();
        var classIds = page.getList().stream().map(ServiceRelationDO::getClassId).filter(Objects::nonNull).distinct().toList();
        var personMap = personIds.isEmpty() ? Map.<Long, cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO>of()
                : people.selectByIds(personIds).stream().collect(Collectors.toMap(p -> p.getId(), Function.identity()));
        var classMap = classIds.isEmpty() ? Map.<Long, cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO>of()
                : classes.selectByIds(classIds).stream().collect(Collectors.toMap(c -> c.getId(), Function.identity()));
        return new PageResult<>(page.getList().stream().map(r -> new Candidate(r.getId(), r.getPersonId(), r.getStudentNameSnapshot(),
                r.getPersonId() != null && personMap.containsKey(r.getPersonId()) ? personMap.get(r.getPersonId()).getMobile() : null,
                r.getClassId(), r.getClassId() != null && classMap.containsKey(r.getClassId()) ? classMap.get(r.getClassId()).getClassName() : null,
                TeachingServiceLifecycle.status(r))).toList(), page.getTotal());
    }
    @ZsjosPermission(bizType="exam-collection", bizId="#taskId", action="query")
    public Archive archive(Long taskId, Long relationId) {
        require(taskId); var current = archives.current(relationId);
        var result = new Archive(); result.setServiceRelationId(relationId); result.setRevision(current.revision());
        result.setFields(current.fields()); result.setValues(display(current.answers()));
        result.setAttachments(current.attachments().stream().map(a -> new ArchiveAttachment(a.source(), a.id(), a.fieldKey(), a.side(), a.name(), a.contentType(), a.size())).toList());
        return result;
    }
    @ZsjosPermission(bizType="exam-collection", bizId="#taskId", action="query")
    public PageResult<Event> events(Long taskId, PageParam query) {
        require(taskId);
        var page = events.selectPage(query, new LambdaQueryWrapperX<ExamCollectionEventDO>()
                .eq(ExamCollectionEventDO::getTaskId, taskId).orderByDesc(ExamCollectionEventDO::getId));
        var userIds = page.getList().stream().map(ExamCollectionEventDO::getOperatorUserId).filter(Objects::nonNull).distinct().toList();
        var operators = userIds.isEmpty() ? Map.<Long, cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO>of() : users.getUserMap(userIds);
        return new PageResult<>(page.getList().stream().map(e -> new Event(e.getId(), e.getSubmissionId(), e.getAction(), e.getReason(),
                e.getOperatorUserId() == null ? "学员提交" : operators.containsKey(e.getOperatorUserId()) ? operators.get(e.getOperatorUserId()).getNickname() : "已离职或不可用用户",
                e.getCreateTime())).toList(), page.getTotal());
    }
    @ZsjosPermission(bizType="exam-collection", bizId="#taskId", action="query")
    public PageResult<Member> members(Long taskId, MemberQuery query) {
        require(taskId);
        var filter = new LambdaQueryWrapperX<ExamCollectionMemberDO>().eq(ExamCollectionMemberDO::getTaskId, taskId)
                .eqIfPresent(ExamCollectionMemberDO::getActive, query.getActive());
        if (query.getFilled() != null) {
            if (query.getFilled()) filter.isNotNull(ExamCollectionMemberDO::getLastSubmissionId);
            else filter.isNull(ExamCollectionMemberDO::getLastSubmissionId);
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) filter.apply(
                "EXISTS (SELECT 1 FROM zsjos_service_relation s JOIN zsjos_person p ON p.id=s.person_id AND p.tenant_id=s.tenant_id AND p.deleted=0 "
                + "WHERE s.id=zsjos_exam_collection_member.service_relation_id AND s.tenant_id=zsjos_exam_collection_member.tenant_id AND s.deleted=0 "
                + "AND (LOCATE({0},s.student_name_snapshot)>0 OR LOCATE({0},p.mobile)>0))", query.getKeyword().trim());
        filter.orderByAsc(ExamCollectionMemberDO::getId);
        var page = members.selectPage(query, filter);
        if (page.getList().isEmpty()) return new PageResult<>(List.of(), page.getTotal());
        var relationIds = page.getList().stream().map(ExamCollectionMemberDO::getServiceRelationId).toList();
        var personIds = page.getList().stream().map(ExamCollectionMemberDO::getPersonId).distinct().toList();
        var plannerIds = page.getList().stream().map(ExamCollectionMemberDO::getPlannerUserId).filter(Objects::nonNull).distinct().toList();
        var serviceMap = relations.selectByIds(relationIds).stream().collect(Collectors.toMap(r -> r.getId(), Function.identity()));
        var personMap = people.selectByIds(personIds).stream().collect(Collectors.toMap(p -> p.getId(), Function.identity()));
        var planners = plannerIds.isEmpty() ? Map.<Long, cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO>of() : users.getUserMap(plannerIds);
        return new PageResult<>(page.getList().stream().map(m -> new Member(m.getServiceRelationId(), m.getPersonId(),
                serviceMap.containsKey(m.getServiceRelationId()) ? serviceMap.get(m.getServiceRelationId()).getStudentNameSnapshot() : "已删除学员服务",
                personMap.containsKey(m.getPersonId()) ? personMap.get(m.getPersonId()).getMobile() : null,
                m.getClassId(), m.getClassNameSnapshot(), m.getPlannerUserId() != null && planners.containsKey(m.getPlannerUserId()) ? planners.get(m.getPlannerUserId()).getNickname() : "未分配或不可用",
                Boolean.TRUE.equals(m.getActive()), m.getLastSubmissionId())).toList(), page.getTotal());
    }
    static Map<String, String> display(List<StudentInfoFormValueDO> answers) {
        var values = new LinkedHashMap<String, String>();
        for (var value : answers) values.put(value.getFieldKey(), value.getValueText() != null ? value.getValueText()
                : value.getValueLabelSnapshot() != null ? value.getValueLabelSnapshot() : Objects.requireNonNullElse(value.getAreaLabelSnapshot(), ""));
        return values;
    }
    private Submission project(ExamCollectionSubmissionDO row) {
        var result = BeanUtils.toBean(row, Submission.class);
        result.setValues(display(JsonUtils.parseArray(row.getAnswersJson(), StudentInfoFormValueDO.class))); return result;
    }
    private void require(Long taskId) { objects.check(taskId, "query", getLoginUserId()); }
    private ExamCollectionSubmissionDO requireSubmission(Long taskId, Long id) {
        var row = submissions.selectById(id);
        if (row == null || row.getSubmittedAt() == null || !Objects.equals(row.getTaskId(), taskId)
                || !Objects.equals(row.getTenantId(), TenantContextHolder.getRequiredTenantId())) throw exception(NOT_FOUND);
        return row;
    }
    private ExamCollectionAttachmentDO requireFile(ExamCollectionSubmissionDO submission, Long id) {
        var row = attachments.selectById(id);
        if (row == null || !Objects.equals(row.getTaskId(), submission.getTaskId())
                || !Objects.equals(row.getSubmissionId(), submission.getId()) || !Objects.equals(row.getTenantId(), submission.getTenantId()))
            throw exception(NOT_FOUND);
        return row;
    }
    private StudentInfoVO.Image image(ExamCollectionAttachmentDO row) {
        var value = new StudentInfoVO.Image(); value.setId(row.getId()); value.setFieldKey(row.getFieldKey()); value.setSide(row.getSide());
        value.setName(row.getOriginalName()); value.setContentType(row.getContentType()); value.setSize(row.getFileSize()); return value;
    }
}
