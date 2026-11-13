package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentSummaryVO.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentArchiveExtraVO.Value;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentServicePageMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterQuery;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.*;

/** Explicit management projection: query grants tenant-wide reads, never legacy commands. */
@Service
public class StudentSummaryService {
    public static final String QUERY = "zsjos:student-summary:query", EXPORT = "zsjos:student-summary:export";
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper people;
    @Resource private PermissionApi permissions;
    @Resource private StudentServicePageMapper services;
    @Resource private DeliveryClassMapper classMapper;
    @Resource private StudentInfoFields fields;
    @Resource private StudentInfoFormConfigMapper configs;
    @Resource private StudentInfoFormMapper forms;
    @Resource private StudentSummaryMapper summaryMapper;
    @Resource private StudentInfoFormValueMapper answers;
    @Resource private StudentArchiveRevisionMapper revisions;
    @Resource private StudentInfoImageMapper images;
    @Resource private StudentArchiveExtraRevisionMapper extras;
    @Resource private StudentArchiveAttachmentMapper attachments;
    @Resource private StudentServiceArchiveRevisionMapper serviceRevisions;
    @Resource private ExamCollectionSubmissionMapper examSubmissions;
    @Resource private cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionPermissionProvider examPermissions;

    private void require(String code) {
        TenantContextHolder.getRequiredTenantId();
        if (!permissions.hasAnyPermissions(getLoginUserId(),code))
            throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED);
    }
    public List<Column> columns() {
        require(QUERY);
        return catalog();
    }
    @cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission(bizType="exam-collection", bizId="#taskId", action="query")
    public List<Column> columnsForExam(Long taskId) {
        examPermissions.check(taskId, "query", getLoginUserId()); return catalog();
    }
    @cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission(bizType="exam-collection", bizId="#taskId", action="query")
    public List<Row> projectForExam(Long taskId, List<ServiceRelationDO> selected) {
        examPermissions.check(taskId, "query", getLoginUserId());
        if (selected.stream().anyMatch(r -> !Objects.equals(r.getTenantId(), TenantContextHolder.getRequiredTenantId()))) throw exception(LINK_INVALID);
        return project(selected);
    }
    @Resource private cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionPlannerPermissionProvider examPlannerPermissions;

    @cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission(bizType="exam-collection-planner", bizId="#taskId", action="query")
    public List<Row> projectForExamPlanner(Long taskId, List<ServiceRelationDO> selected) {
        examPlannerPermissions.check(taskId, "query", getLoginUserId());
        if (selected.stream().anyMatch(r -> !Objects.equals(r.getTenantId(), TenantContextHolder.getRequiredTenantId()))) throw exception(LINK_INVALID);
        return project(selected);
    }
    private List<Column> catalog() {
        var result=new ArrayList<Column>();
        var published=configs.state("PUBLISHED");
        var definitions=new LinkedHashMap<String,cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field>();
        fields.presets().forEach(f->definitions.put(f.getKey(),f));
        if(published!=null) fields.parse(published.getFieldsJson()).forEach(f->definitions.put(f.getKey(),f));
        result.add(new Column("student.name","学员·姓名","学员信息"));
        result.add(new Column("student.mobile","学员·手机号","学员信息"));
        for(var key:List.of("name","mobile")) {
            var field=definitions.remove(key);
            if(field!=null) result.add(new Column("info."+key,field.getLabel(),"报名资料"));
        }
        definitions.values().forEach(f->result.add(new Column("info."+f.getKey(),f.getLabel(),"报名资料")));
        result.add(new Column("info.wechat","微信号","报名资料"));
        StudentArchiveExtraService.definitions().forEach(f->result.add(new Column("extra."+f.key(),f.label(),"扩展档案")));
        result.add(4,new Column("collectionStatus","填写状态","档案状态",List.of(new StatusOption("已填写","true"),new StatusOption("未填写","false"))));
        result.add(new Column("submittedAt","资料提交时间","档案状态"));
        return result;
    }
    public List<ClassOption> classes() {
        require(QUERY);
        return classMapper.selectList(new LambdaQueryWrapperX<DeliveryClassDO>().orderByDesc(DeliveryClassDO::getId)).stream()
            .map(c->new ClassOption(c.getId(),text(c.getClassName())+" · "+text(c.getHomeroomUserNameSnapshot())
                +(c.getExamScheduleSnapshot()==null ? "" : " · "+c.getExamScheduleSnapshot()))).toList();
    }
    private AdvancedFilterQuery query(Query request, int offset, int limit) {
        var params=new HashMap<String,Object>();
        params.put("tenantId",TenantContextHolder.getRequiredTenantId()); params.put("offset",offset); params.put("limit",limit);
        String where="1=1";
        if(request.getClassIds()!=null && !request.getClassIds().isEmpty()) {
            var keys=new ArrayList<String>();
            for(int i=0;i<request.getClassIds().size();i++) { String key="selectedClass"+i; params.put(key,request.getClassIds().get(i)); keys.add("#{query.parameters."+key+"}"); }
            where+=" AND sr.class_id IN ("+String.join(",",keys)+")";
        }
        if(request.getClassId()!=null) { where+=" AND sr.class_id=#{query.parameters.classId}"; params.put("classId",request.getClassId()); }
        if(request.getKeyword()!=null && !request.getKeyword().isBlank()) {
            where+=" AND (LOCATE(#{query.parameters.keyword},sr.student_name_snapshot)>0 OR LOCATE(#{query.parameters.keyword},p.mobile)>0 OR LOCATE(#{query.parameters.keyword},p.person_no)>0)";
            params.put("keyword",request.getKeyword().trim());
        }
        return new AdvancedFilterQuery(where,params);
    }
    private AdvancedFilterQuery submittedQuery(Query request,int offset,int limit) {
        var base=query(request,offset,limit);
        return new AdvancedFilterQuery(base.getWhereSql()+" " + """
          AND EXISTS (SELECT 1 FROM zsjos_student_info_form submitted
          WHERE submitted.tenant_id=sr.tenant_id AND submitted.deleted=0 AND submitted.status='SUBMITTED'
          AND submitted.id=(SELECT MAX(f.id) FROM zsjos_student_info_form f
          WHERE f.deleted=0 AND f.tenant_id=sr.tenant_id AND
          (f.registration_case_id=sr.registration_case_id OR EXISTS
            (SELECT 1 FROM zsjos_student_info_task t JOIN zsjos_registration_case c
             ON c.order_item_id=t.order_item_id AND c.tenant_id=t.tenant_id AND c.deleted=0
             WHERE c.id=sr.registration_case_id AND t.form_id=f.id AND t.tenant_id=f.tenant_id
               AND t.person_id=f.person_id AND t.deleted=0)))
          UNION ALL SELECT 1 FROM zsjos_student_service_archive_revision ar
          WHERE ar.service_relation_id=sr.id AND ar.person_id=sr.person_id AND ar.tenant_id=sr.tenant_id AND ar.deleted=0)
          """,base.getParameters());
    }
    private AdvancedFilterQuery filteredQuery(Query request,int offset,int limit) {
        if(request.getFilled()==null) return query(request,offset,limit);
        var submitted=submittedQuery(request,offset,limit);
        return Boolean.TRUE.equals(request.getFilled()) ? submitted : new AdvancedFilterQuery(
            submitted.getWhereSql().replaceFirst("AND EXISTS", "AND NOT EXISTS"), submitted.getParameters());
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Coverage coverage(Query request) {
        require(QUERY);
        var scope=new Query(); scope.setClassId(request.getClassId()); scope.setClassIds(request.getClassIds());
        return new Coverage(services.count(submittedQuery(scope,0,1)),services.count(query(scope,0,1)));
    }
    public PageResult<Row> page(Query request) {
        require(QUERY);
        var query=filteredQuery(request,Math.multiplyExact(request.getPageNo()-1,request.getPageSize()),request.getPageSize());
        long count=services.count(query);
        return new PageResult<>(count==0 ? List.of() : project(services.page(query)),count);
    }
    static String text(Object value) { return value==null ? "" : value instanceof java.time.LocalDateTime date ? date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : value.toString(); }
    static String answerText(StudentInfoFormValueDO value) {
        return text(value.getValueText()!=null ? value.getValueText() : value.getValueLabelSnapshot()!=null ? value.getValueLabelSnapshot() : value.getAreaLabelSnapshot());
    }
    private List<Row> project(List<ServiceRelationDO> relations) {
        if(relations.isEmpty()) return List.of();
        var personMap=people.selectByIds(relations.stream().map(ServiceRelationDO::getPersonId).distinct().toList()).stream()
            .collect(Collectors.toMap(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO::getId,Function.identity()));
        var relationIds=relations.stream().map(ServiceRelationDO::getId).toList();
        var serviceRevisionMap=serviceRevisions.selectList(new LambdaQueryWrapperX<StudentServiceArchiveRevisionDO>()
            .in(StudentServiceArchiveRevisionDO::getServiceRelationId,relationIds)
            .apply("revision=(SELECT MAX(r.revision) FROM zsjos_student_service_archive_revision r WHERE r.service_relation_id=zsjos_student_service_archive_revision.service_relation_id AND r.tenant_id=zsjos_student_service_archive_revision.tenant_id AND r.deleted=0)"))
            .stream().collect(Collectors.toMap(StudentServiceArchiveRevisionDO::getServiceRelationId,Function.identity()));
        var lastExamRevisions=serviceRevisionMap.isEmpty()?List.<StudentServiceArchiveRevisionDO>of():serviceRevisions.selectList(new LambdaQueryWrapperX<StudentServiceArchiveRevisionDO>()
            .in(StudentServiceArchiveRevisionDO::getServiceRelationId,serviceRevisionMap.keySet()).eq(StudentServiceArchiveRevisionDO::getSource,"exam_submission")
            .apply("revision=(SELECT MAX(r.revision) FROM zsjos_student_service_archive_revision r WHERE r.service_relation_id=zsjos_student_service_archive_revision.service_relation_id AND r.tenant_id=zsjos_student_service_archive_revision.tenant_id AND r.deleted=0 AND r.source='exam_submission' AND EXISTS (SELECT 1 FROM zsjos_exam_collection_submission s WHERE s.id=r.submission_id AND s.tenant_id=r.tenant_id AND s.task_id=r.task_id AND s.service_relation_id=r.service_relation_id AND s.status='ARCHIVED' AND s.deleted=0))"));
        var submissionIds=lastExamRevisions.stream().map(StudentServiceArchiveRevisionDO::getSubmissionId).filter(Objects::nonNull).toList();
        var submissionMap=submissionIds.isEmpty()?Map.<Long,ExamCollectionSubmissionDO>of():examSubmissions.selectByIds(submissionIds).stream().collect(Collectors.toMap(ExamCollectionSubmissionDO::getId,Function.identity()));
        var submittedTimes=new HashMap<Long,java.time.LocalDateTime>();
        for(var revision:lastExamRevisions) { var submission=submissionMap.get(revision.getSubmissionId()); if(submission!=null && Objects.equals(submission.getServiceRelationId(),revision.getServiceRelationId())) submittedTimes.put(revision.getServiceRelationId(),submission.getSubmittedAt()); }
        var bindings=summaryMapper.bindings(relationIds,TenantContextHolder.getRequiredTenantId()).stream()
            .filter(b->b.getFormId()!=null).collect(Collectors.toMap(StudentSummaryMapper.Binding::getServiceId,StudentSummaryMapper.Binding::getFormId));
        var formIds=new ArrayList<>(new HashSet<>(bindings.values()));
        var formMap=formIds.isEmpty()?Map.<Long,StudentInfoFormDO>of():forms.selectByIds(formIds).stream().collect(Collectors.toMap(StudentInfoFormDO::getId,Function.identity()));
        var submittedIds=formMap.values().stream().filter(f->"SUBMITTED".equals(f.getStatus())).map(StudentInfoFormDO::getId).toList();
        var revisionMap=submittedIds.isEmpty()?Map.<Long,StudentArchiveRevisionDO>of():revisions.selectList(new LambdaQueryWrapperX<StudentArchiveRevisionDO>()
            .in(StudentArchiveRevisionDO::getFormId,submittedIds)
            .apply("revision=(SELECT MAX(r.revision) FROM zsjos_student_archive_revision r WHERE r.form_id=zsjos_student_archive_revision.form_id AND r.tenant_id=zsjos_student_archive_revision.tenant_id AND r.deleted=0)"))
            .stream().collect(Collectors.toMap(StudentArchiveRevisionDO::getFormId,Function.identity()));
        var answerMap=submittedIds.isEmpty()?Map.<Long,List<StudentInfoFormValueDO>>of():answers.selectList(new LambdaQueryWrapperX<StudentInfoFormValueDO>().in(StudentInfoFormValueDO::getFormId,submittedIds))
            .stream().collect(Collectors.groupingBy(StudentInfoFormValueDO::getFormId));
        var imageMap=submittedIds.isEmpty()?Map.<Long,List<StudentInfoImageDO>>of():images.selectList(new LambdaQueryWrapperX<StudentInfoImageDO>().in(StudentInfoImageDO::getFormId,submittedIds))
            .stream().collect(Collectors.groupingBy(StudentInfoImageDO::getFormId));
        var extraMap=extras.selectList(new LambdaQueryWrapperX<StudentArchiveExtraRevisionDO>().in(StudentArchiveExtraRevisionDO::getServiceRelationId,relationIds)
            .apply("revision=(SELECT MAX(r.revision) FROM zsjos_student_archive_extra_revision r WHERE r.service_relation_id=zsjos_student_archive_extra_revision.service_relation_id AND r.tenant_id=zsjos_student_archive_extra_revision.tenant_id AND r.deleted=0)"))
            .stream().collect(Collectors.toMap(StudentArchiveExtraRevisionDO::getServiceRelationId,Function.identity()));
        var attachmentMap=attachments.selectList(new LambdaQueryWrapperX<StudentArchiveAttachmentDO>().in(StudentArchiveAttachmentDO::getServiceRelationId,relationIds))
            .stream().collect(Collectors.groupingBy(StudentArchiveAttachmentDO::getServiceRelationId));
        var result=new ArrayList<Row>();
        for(var relation:relations) {
            var values=new LinkedHashMap<String,String>();
            // Reuse the owning form resolver, including task-backed registration forms; never substitute another course's latest person archive.
            var form=bindings.containsKey(relation.getId())?formMap.get(bindings.get(relation.getId())):null;
            if(form!=null && (!Objects.equals(form.getPersonId(),relation.getPersonId()) || !Objects.equals(form.getOrderItemId(),relation.getOrderItemId()))) throw exception(LINK_INVALID);
            // Keep student contacts separate from submitted answers so discrepancies remain visible.
            values.put("student.name",text(relation.getStudentNameSnapshot()));
            var person=personMap.get(relation.getPersonId());
            values.put("student.mobile",person==null?"":text(person.getMobile()));
            values.put("collectionStatus",form!=null && "SUBMITTED".equals(form.getStatus())?"已填写":"未填写");
            if(form!=null && "SUBMITTED".equals(form.getStatus())) {
                values.put("submittedAt",text(form.getSubmittedAt()));
                var revision=revisionMap.get(form.getId());
                var content=revision==null?answerMap.getOrDefault(form.getId(),List.of()):JsonUtils.parseArray(revision.getAnswersJson(),StudentInfoFormValueDO.class);
                content.forEach(a->values.put("info."+a.getFieldKey(),answerText(a)));
                var selected=revision==null?null:JsonUtils.parseArray(revision.getImageIdsJson(),Long.class);
                var material=imageMap.getOrDefault(form.getId(),List.of());
                material.stream().filter(i->selected==null?!Boolean.TRUE.equals(i.getArchiveOnly()):selected.contains(i.getId()))
                    .collect(Collectors.groupingBy(StudentInfoImageDO::getFieldKey,Collectors.mapping(i->i.getOriginalName()==null || i.getOriginalName().isBlank()?i.getFieldKey()+"-"+i.getId()+StudentInfoAttachmentValidator.extension(i.getContentType()):i.getOriginalName(),Collectors.joining("；"))))
                    .forEach((key,value)->values.put("info."+key,value));
            }
            var serviceRevision=serviceRevisionMap.get(relation.getId());
            if(serviceRevision!=null) {
                if(!Objects.equals(serviceRevision.getPersonId(),relation.getPersonId())) throw exception(LINK_INVALID);
                values.keySet().removeIf(key->key.startsWith("info."));
                JsonUtils.parseArray(serviceRevision.getAnswersJson(),StudentInfoFormValueDO.class).forEach(a->values.put("info."+a.getFieldKey(),answerText(a)));
                JsonUtils.parseArray(serviceRevision.getAttachmentsJson(),cn.iocoder.yudao.module.zsjos.service.examcollection.StudentServiceArchiveStore.Attachment.class)
                    .stream().collect(Collectors.groupingBy(cn.iocoder.yudao.module.zsjos.service.examcollection.StudentServiceArchiveStore.Attachment::fieldKey,
                        Collectors.mapping(a->a.name()==null||a.name().isBlank()?a.fieldKey()+"-"+a.id()+StudentInfoAttachmentValidator.extension(a.contentType()):a.name(),Collectors.joining("；"))))
                    .forEach((key,value)->values.put("info."+key,value));
                values.put("collectionStatus","已填写"); values.put("submittedAt",text(submittedTimes.get(relation.getId())));
            }
            var extra=extraMap.get(relation.getId());
            if(extra!=null) {
                var content=JsonUtils.parseObject(extra.getValuesJson(),new tools.jackson.core.type.TypeReference<Map<String,Value>>(){});
                var files=attachmentMap.getOrDefault(relation.getId(),List.of()).stream().collect(Collectors.toMap(StudentArchiveAttachmentDO::getId,Function.identity()));
                content.forEach((key,value)->{
                    var parts=new ArrayList<String>(); parts.add(text(value.getText())); parts.addAll(value.getLinks());
                    for(var id:value.getAttachmentIds()) {
                        var file=files.get(id);
                        if(file==null || !key.equals(file.getFieldKey())) throw exception(LINK_INVALID);
                        var label=StudentArchiveExtraService.definitions().stream().filter(f->f.key().equals(key)).map(f->f.label()).findFirst().orElse(key);
                        parts.add(label+"-"+id+StudentInfoAttachmentValidator.extension(file.getContentType()));
                    }
                    values.put("extra."+key,String.join("；",parts));
                });
            }
            values.putIfAbsent("extra.certificate_shipping_address",values.getOrDefault("info.certificate_shipping_address",""));
            result.add(new Row(relation.getId(),values));
        }
        return result;
    }
    public static List<Column> selectedColumns(List<Column> catalog,List<String> keys) {
        var map=catalog.stream().collect(Collectors.toMap(Column::key,Function.identity()));
        if(keys==null || keys.isEmpty() || new HashSet<>(keys).size()!=keys.size() || !map.keySet().containsAll(keys)) throw exception(FIELD_INVALID,"导出列无效，请刷新列设置");
        return keys.stream().map(map::get).toList();
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public void export(Export request,jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        require(QUERY); require(EXPORT);
        var selected=selectedColumns(columns(),request.getColumns());
        long total=services.count(filteredQuery(request,0,100));
        if(total>1_048_575) throw exception(FIELD_INVALID,"记录超过 Excel 单表上限，请按班级筛选后导出");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition","attachment;filename="+cn.iocoder.yudao.framework.common.util.http.HttpUtils.encodeUtf8("学员档案汇总.xlsx"));
        try(var writer=cn.idev.excel.FastExcelFactory.write(response.getOutputStream()).autoCloseStream(false)
                .head(selected.stream().map(c->List.of(c.label())).toList()).build()) {
            var sheet=cn.idev.excel.FastExcelFactory.writerSheet("学员档案汇总").build();
            if(total==0) writer.write(List.of(),sheet);
            for(int offset=0;offset<total;offset+=100) {
                var rows=project(services.page(filteredQuery(request,offset,100)));
                writer.write(rows.stream().map(row->selected.stream().map(c->row.values().getOrDefault(c.key(),"")).toList()).toList(),sheet);
            }
        }
    }
}
