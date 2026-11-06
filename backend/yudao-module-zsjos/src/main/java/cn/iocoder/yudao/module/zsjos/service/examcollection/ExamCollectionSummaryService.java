package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentSummaryVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentSummaryService;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.FIELD_INVALID;

@Service
public class ExamCollectionSummaryService {
    @Resource private ExamCollectionPermissionProvider permissions;
    @Resource private ExamCollectionTaskMapper tasks;
    @Resource private ExamCollectionMemberMapper members;
    @Resource private ExamCollectionSubmissionMapper submissions;
    @Resource private ServiceRelationMapper relations;
    @Resource private DeliveryClassMapper classes;
    @Resource private StudentSummaryService summary;
    @ZsjosPermission(bizType="exam-collection",bizId="#taskId",action="query")
    public List<Column> columns(Long taskId) { check(taskId,"query"); return summary.columnsForExam(taskId); }
    @ZsjosPermission(bizType="exam-collection",bizId="#taskId",action="query")
    public List<ClassOption> classes(Long taskId) {
        check(taskId,"query"); var task=tasks.selectById(taskId);
        var ids=task.getClassIdsJson()==null?List.<Long>of():JsonUtils.parseArray(task.getClassIdsJson(),Long.class);
        if(ids.isEmpty()) return List.of();
        var names=new HashMap<Long,String>(); classes.selectByIds(ids).forEach(c->names.put(c.getId(),c.getClassName()));
        members.selectList(new LambdaQueryWrapperX<ExamCollectionMemberDO>().eq(ExamCollectionMemberDO::getTaskId,taskId))
            .forEach(m->names.put(m.getClassId(),m.getClassNameSnapshot()));
        return ids.stream().map(id->new ClassOption(id,names.getOrDefault(id,"已删除班级"))).toList();
    }
    private LambdaQueryWrapperX<ExamCollectionMemberDO> scope(Long taskId, Query request, boolean filters) {
        var q=new LambdaQueryWrapperX<ExamCollectionMemberDO>().eq(ExamCollectionMemberDO::getTaskId,taskId)
            .eq(ExamCollectionMemberDO::getActive,true).eqIfPresent(ExamCollectionMemberDO::getClassId,request.getClassId());
        if(request.getClassIds()!=null&&!request.getClassIds().isEmpty()) q.in(ExamCollectionMemberDO::getClassId,request.getClassIds());
        if(filters) {
            if(request.getFilled()!=null) { if(request.getFilled())q.isNotNull(ExamCollectionMemberDO::getLastSubmissionId);else q.isNull(ExamCollectionMemberDO::getLastSubmissionId); }
            if(request.getKeyword()!=null&&!request.getKeyword().isBlank())q.apply("EXISTS (SELECT 1 FROM zsjos_service_relation s JOIN zsjos_person p ON p.id=s.person_id AND p.tenant_id=s.tenant_id AND p.deleted=0 WHERE s.id=zsjos_exam_collection_member.service_relation_id AND s.tenant_id=zsjos_exam_collection_member.tenant_id AND s.deleted=0 AND (LOCATE({0},s.student_name_snapshot)>0 OR LOCATE({0},p.mobile)>0 OR LOCATE({0},p.person_no)>0))",request.getKeyword().trim());
        }
        return q;
    }
    @ZsjosPermission(bizType="exam-collection",bizId="#taskId",action="query")
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Coverage coverage(Long taskId, Query request) {
        check(taskId,"query");
        return new Coverage(members.selectCount(scope(taskId,request,false).isNotNull(ExamCollectionMemberDO::getLastSubmissionId)),members.selectCount(scope(taskId,request,false)));
    }
    @ZsjosPermission(bizType="exam-collection",bizId="#taskId",action="query")
    public PageResult<Row> page(Long taskId, Query request) {
        check(taskId,"query");var paging=new PageParam();paging.setPageNo(request.getPageNo());paging.setPageSize(request.getPageSize());
        var page=members.selectPage(paging,scope(taskId,request,true).orderByAsc(ExamCollectionMemberDO::getId));
        if(page.getList().isEmpty())return new PageResult<>(List.of(),page.getTotal());
        var ids=page.getList().stream().map(ExamCollectionMemberDO::getServiceRelationId).toList();
        var current=relations.selectByIds(ids).stream().collect(Collectors.toMap(ServiceRelationDO::getId,Function.identity()));
        var ordered=new ArrayList<ServiceRelationDO>();
        for(var member:page.getList()) {
            var relation=current.get(member.getServiceRelationId());
            if(relation==null) { relation=new ServiceRelationDO();relation.setId(member.getServiceRelationId());relation.setPersonId(member.getPersonId());relation.setTenantId(TenantContextHolder.getRequiredTenantId());relation.setStudentNameSnapshot("已删除学员服务"); }
            ordered.add(relation);
        }
        var projected=summary.projectForExam(taskId,ordered);
        var submissionIds=page.getList().stream().map(ExamCollectionMemberDO::getLastSubmissionId).filter(Objects::nonNull).toList();
        var submitted=submissionIds.isEmpty()?Map.<Long,ExamCollectionSubmissionDO>of():submissions.selectByIds(submissionIds).stream().collect(Collectors.toMap(ExamCollectionSubmissionDO::getId,Function.identity()));
        var roster=page.getList().stream().collect(Collectors.toMap(ExamCollectionMemberDO::getServiceRelationId,Function.identity()));
        for(var row:projected) {
            var member=roster.get(row.serviceRelationId());
            row.values().put("collectionStatus",member.getLastSubmissionId()==null?"未填写":"已填写");
            var input=member.getLastSubmissionId()==null?null:submitted.get(member.getLastSubmissionId());
            row.values().put("submittedAt",input==null||input.getSubmittedAt()==null?"":input.getSubmittedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        return new PageResult<>(projected,page.getTotal());
    }
    @ZsjosPermission(bizType="exam-collection",bizId="#taskId",action="export")
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public void export(Long taskId,Export request,jakarta.servlet.http.HttpServletResponse response)throws java.io.IOException {
        check(taskId,"export");var selected=StudentSummaryService.selectedColumns(columns(taskId),request.getColumns());
        var query=BeanUtils.toBean(request,Query.class);query.setPageNo(1);query.setPageSize(100);var first=page(taskId,query);
        if(first.getTotal()>1_048_575)throw exception(FIELD_INVALID,"记录超过 Excel 单表上限，请缩小筛选范围");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition","attachment;filename="+cn.iocoder.yudao.framework.common.util.http.HttpUtils.encodeUtf8("考试资料收集汇总.xlsx"));
        try(var writer=cn.idev.excel.FastExcelFactory.write(response.getOutputStream()).autoCloseStream(false).head(selected.stream().map(c->List.of(c.label())).toList()).build()) {
            var sheet=cn.idev.excel.FastExcelFactory.writerSheet("考试资料收集汇总").build();
            var current=first;
            do {writer.write(current.getList().stream().map(r->selected.stream().map(c->r.values().getOrDefault(c.key(),"")).toList()).toList(),sheet);
                if((long)query.getPageNo()*100>=first.getTotal())break;query.setPageNo(query.getPageNo()+1);current=page(taskId,query);
            } while(true);
        }
    }
    private void check(Long id,String action){permissions.check(id,action,getLoginUserId());}
}
