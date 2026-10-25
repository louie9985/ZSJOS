package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.*;
import cn.iocoder.yudao.module.zsjos.service.common.BusinessReadScopeService;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.STUDENT_NOT_EXISTS;

@Service
public class StudentServiceReadService {
    @Resource private StudentServicePageMapper pageMapper;
    @Resource private ServiceRelationMapper relationMapper;
    @Resource private MyStudentServiceImpl projection;
    @Resource private PermissionApi permissionApi;
    @Resource private AdminUserApi userApi;
    @Resource private DeliveryClassScopeService classScope;
    @Resource private BusinessReadScopeService readScope;
    @Resource private AdvancedFilterService filters;
    @Resource private StudentServiceSummaryReader summaryReader;
    @Resource private cn.iocoder.yudao.module.zsjos.service.task.MenuTaskSummaryService taskReminders;

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResult<StudentServiceRespVO> page(Long actor, StudentServicePageReqVO req) {
        var query = query(actor, req);
        long count = pageMapper.count(query);
        if (count == 0) return PageResult.empty();
        var relations = pageMapper.page(query);
        var reminders = taskReminders.getMyStudentReminders(actor, relations.stream().map(ServiceRelationDO::getId).toList());
        var summaries = summaryReader.read(actor, relations, (java.time.LocalDateTime) query.getParameters().get("now"));
        return new PageResult<>(relations.stream().map(r -> {
            var row = convert(actor, r); row.setSummary(summaries.get(r.getId()));
            row.setPendingTasks(reminders.getOrDefault(r.getId(), List.of())); return row;
        }).toList(), count);
    }

    @ZsjosPermission(bizType = "student-service", bizId = "#relationId", action = "read")
    public StudentServiceRespVO detail(Long actor, Long relationId) {
        var relation = relationMapper.selectById(relationId);
        if (!StudentDeliveryParticipation.includes(relation)) throw exception(STUDENT_NOT_EXISTS);
        var req = new StudentServicePageReqVO();
        req.setServiceStatus(TeachingServiceLifecycle.status(relation));
        if (permissionApi.hasTenantReadAllAccess(actor)) req.setReadScope("ALL");
        var scoped = query(actor, req);
        scoped.getParameters().put("relationId", relationId);
        if (pageMapper.count(new AdvancedFilterQuery(scoped.getWhereSql()
                + " AND sr.id=#{query.parameters.relationId}", scoped.getParameters())) == 0) throw exception(STUDENT_NOT_EXISTS);
        var result = convert(actor, relation);
        result.setPendingTasks(taskReminders.getMyStudentReminders(actor, List.of(relationId)).getOrDefault(relationId, List.of()));
        result.setSummary(summaryReader.read(actor, List.of(relation), java.time.LocalDateTime.now()).get(relationId));
        return result;
    }

    private StudentServiceRespVO convert(Long actor, ServiceRelationDO relation) {
        var person = projection.convert(actor, relation.getPersonId(), List.of(relation));
        var result = BeanUtils.toBean(person, StudentServiceRespVO.class);
        result.setServiceRelationId(relation.getId());
        result.setService(person.getServices().getFirst());
        result.getService().setStatus(TeachingServiceLifecycle.status(relation));
        result.getService().setTeachingClosed(relation.getTeachingClosed());
        result.setLeadId(result.getService().getLeadId());
        result.setLeadNo(result.getService().getLeadNo());
        return result;
    }

    AdvancedFilterQuery query(Long actor, StudentServicePageReqVO req) {
        var filter = filters.buildStudentServiceQuery(req.getAdvancedFilter());
        Map<String,Object> params = new LinkedHashMap<>(filter == null ? Map.of() : filter.getParameters());
        params.put("tenantId", TenantContextHolder.getRequiredTenantId());
        params.put("now", java.time.LocalDateTime.now());
        List<String> clauses = new ArrayList<>();
        clauses.add("sr." + StudentDeliveryParticipation.SQL);
        clauses.add(filter == null ? "1=1" : "(" + filter.getWhereSql() + ")");
        Long subject = actor;
        boolean all = false;
        Set<Long> owners = null;
        if (req.getReadScope() != null || req.getTargetUserId() != null) {
            subject = readScope.resolve(req.getReadScope(), req.getTargetUserId(), actor);
            all = subject == null;
        } else if (StudentReadScope.canReadAll(permissionApi, actor)) {
            all = true;
        } else if (permissionApi.hasAnyPermissions(actor, "zsjos:delivery-class:query", DeliveryClassService.PERMISSION_QUERY_MANAGED)) {
            var scope = classScope.resolveRead(actor); all = scope.allDepartments();
            owners = new LinkedHashSet<>(); owners.add(actor);
            if (!all && !scope.deptIds().isEmpty()) {
                for (var user : userApi.getUserListByDeptIds(scope.deptIds())) owners.add(user.getId());
            }
        }
        if (!all && owners != null) {
            List<String> keys = new ArrayList<>();
            for (Long owner : owners) { String key="owner"+keys.size(); params.put(key,owner); keys.add("#{query.parameters."+key+"}"); }
            Long pendingClassId = classScope.readablePendingClassId(actor);
            params.put("subject", actor);
            String ownerClause = "(sr.owner_user_id IN ("+String.join(",",keys)+") OR "
                    + "((sr.content_director_user_id=#{query.parameters.subject} OR sr.career_planner_user_id=#{query.parameters.subject})"
                    + " AND sr.acceptance_status='accepted' AND sr.status='active'))";
            if (pendingClassId != null) {
                params.put("pendingClassId", pendingClassId);
                // The tenant-scoped class join excludes missing/deleted classes and unrelated unowned services.
                clauses.add("(" + ownerClause + " OR (dc.system_class=1 AND dc.id=#{query.parameters.pendingClassId}"
                        + " AND sr.status IN ('active','paused','completed')))");
            } else clauses.add(ownerClause);
        } else if (!all) {
            params.put("subject", subject);
            clauses.add("(sr.owner_user_id=#{query.parameters.subject} OR ((sr.content_director_user_id=#{query.parameters.subject} OR sr.career_planner_user_id=#{query.parameters.subject}) AND sr.acceptance_status='accepted' AND sr.status='active'))");
        }
        if (req.getServiceStatus() != null) add(clauses,params,TeachingServiceLifecycle.STATUS_SQL,"status",req.getServiceStatus());
        else if (req.getClassId() == null && !TeachingServiceLifecycle.hasExplicitStatus(req.getAdvancedFilter())) clauses.add("sr.teaching_closed=0");
        if (!all || !permissionApi.hasTenantReadAllAccess(actor)) clauses.add("sr.status IN ('active','paused','completed')");
        add(clauses,params,"sr.class_id","classId",req.getClassId());
        add(clauses,params,"sr.owner_user_id","ownerUserId",req.getOwnerUserId());
        add(clauses,params,"sr.acceptance_status","acceptanceStatus",req.getAcceptanceStatus());
        add(clauses,params,"COALESCE(NULLIF(sr.delivery_stage,''),'first_contact')","deliveryStage",req.getDeliveryStage());
        add(clauses,params,StudentServiceSummarySql.TASK_STATE,"taskState",req.getTaskState());
        add(clauses,params,"sr.person_id","personId",req.getPersonId());
        add(clauses,params,"p.in_service_period","inServicePeriod",req.getInServicePeriod());
        if (req.getKeyword()!=null && !req.getKeyword().isBlank()) {
            params.put("keyword","%"+req.getKeyword().trim()+"%");
            clauses.add("(sr.student_name_snapshot LIKE #{query.parameters.keyword} OR p.mobile LIKE #{query.parameters.keyword} OR p.wechat_id LIKE #{query.parameters.keyword} OR p.person_no LIKE #{query.parameters.keyword} OR rl.lead_no LIKE #{query.parameters.keyword})");
        }
        params.put("sortField",req.getSortField()); params.put("sortOrder",req.getSortOrder());
        if (Boolean.TRUE.equals(req.getPendingTasksFirst())) {
            var ids = taskReminders.getMyStudentReminderIds(actor);
            params.put("priorityRelationCount", ids.size());
            for (int i = 0; i < ids.size(); i++) params.put("priorityRelation" + i, ids.get(i));
        }
        params.put("limit",req.getPageSize()); params.put("offset",(long)(req.getPageNo()-1)*req.getPageSize());
        if (usesOrderHandoff(req.getAdvancedFilter())) {
            var readable = summaryReader.readableOrders(pageMapper.matchingOrders(new AdvancedFilterQuery(String.join(" AND ", clauses), params)), actor);
            List<String> keys = new ArrayList<>();
            for (Long id : readable) { String key = "readableOrder" + keys.size(); params.put(key, id); keys.add("#{query.parameters." + key + "}"); }
            clauses.add(keys.isEmpty() ? "1=0" : "sr.order_id IN (" + String.join(",", keys) + ")");
        }
        return new AdvancedFilterQuery(String.join(" AND ",clauses),params);
    }
    private static boolean usesOrderHandoff(cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO group) {
        if (group == null) return false;
        if (group.getConditions() != null) for (var condition : group.getConditions()) {
            for (String key : java.util.Arrays.asList(condition.getFieldKey(), condition.getStartFieldKey(), condition.getEndFieldKey()))
                if (key != null && (key.startsWith("orderItem.")
                        || key.startsWith("order.") && !key.equals("order.orderNo"))) return true;
        }
        return group.getGroups() != null && group.getGroups().stream().anyMatch(StudentServiceReadService::usesOrderHandoff);
    }
    private static void add(List<String> clauses,Map<String,Object> params,String column,String key,Object value) {
        if(value!=null) { params.put(key,value); clauses.add(column+"=#{query.parameters."+key+"}"); }
    }
}
