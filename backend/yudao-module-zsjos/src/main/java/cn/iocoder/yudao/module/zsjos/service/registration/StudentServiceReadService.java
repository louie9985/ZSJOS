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

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResult<StudentServiceRespVO> page(Long actor, StudentServicePageReqVO req) {
        var query = query(actor, req);
        long count = pageMapper.count(query);
        if (count == 0) return PageResult.empty();
        return new PageResult<>(pageMapper.page(query).stream().map(r -> convert(actor, r)).toList(), count);
    }

    @ZsjosPermission(bizType = "student-service", bizId = "#relationId", action = "read")
    public StudentServiceRespVO detail(Long actor, Long relationId) {
        var relation = relationMapper.selectById(relationId);
        if (relation == null) throw exception(STUDENT_NOT_EXISTS);
        var req = new StudentServicePageReqVO();
        if (permissionApi.hasTenantReadAllAccess(actor)) req.setReadScope("ALL");
        var scoped = query(actor, req);
        scoped.getParameters().put("relationId", relationId);
        if (pageMapper.count(new AdvancedFilterQuery(scoped.getWhereSql()
                + " AND sr.id=#{query.parameters.relationId}", scoped.getParameters())) == 0) throw exception(STUDENT_NOT_EXISTS);
        return convert(actor, relation);
    }

    private StudentServiceRespVO convert(Long actor, ServiceRelationDO relation) {
        var person = projection.convert(actor, relation.getPersonId(), List.of(relation));
        var result = BeanUtils.toBean(person, StudentServiceRespVO.class);
        result.setServiceRelationId(relation.getId());
        result.setService(person.getServices().getFirst());
        result.setLeadId(result.getService().getLeadId());
        result.setLeadNo(result.getService().getLeadNo());
        return result;
    }

    AdvancedFilterQuery query(Long actor, StudentServicePageReqVO req) {
        var filter = filters.buildStudentServiceQuery(req.getAdvancedFilter());
        Map<String,Object> params = new LinkedHashMap<>(filter == null ? Map.of() : filter.getParameters());
        params.put("tenantId", TenantContextHolder.getRequiredTenantId());
        List<String> clauses = new ArrayList<>();
        clauses.add(filter == null ? "1=1" : "(" + filter.getWhereSql() + ")");
        Long subject = actor;
        boolean all = false;
        Set<Long> owners = null;
        if (req.getReadScope() != null || req.getTargetUserId() != null) {
            subject = readScope.resolve(req.getReadScope(), req.getTargetUserId(), actor);
            all = subject == null;
        } else if (permissionApi.hasAnyPermissions(actor, "zsjos:delivery-class:query", DeliveryClassService.PERMISSION_QUERY_MANAGED)) {
            var scope = classScope.resolve(actor); all = scope.allDepartments();
            owners = new LinkedHashSet<>(); owners.add(actor);
            if (!all && !scope.deptIds().isEmpty()) {
                for (var user : userApi.getUserListByDeptIds(scope.deptIds())) owners.add(user.getId());
            }
        }
        if (!all && owners != null) {
            List<String> keys = new ArrayList<>();
            for (Long owner : owners) { String key="owner"+keys.size(); params.put(key,owner); keys.add("#{query.parameters."+key+"}"); }
            clauses.add("sr.owner_user_id IN ("+String.join(",",keys)+")");
        } else if (!all) {
            params.put("subject", subject);
            clauses.add("(sr.owner_user_id=#{query.parameters.subject} OR ((sr.content_director_user_id=#{query.parameters.subject} OR sr.career_planner_user_id=#{query.parameters.subject}) AND sr.acceptance_status='accepted' AND sr.status='active'))");
        }
        if (req.getServiceStatus() != null) add(clauses,params,"sr.status","status",req.getServiceStatus());
        else if (!all || !permissionApi.hasTenantReadAllAccess(actor)) clauses.add("sr.status IN ('active','paused','completed')");
        add(clauses,params,"sr.class_id","classId",req.getClassId());
        add(clauses,params,"sr.person_id","personId",req.getPersonId());
        add(clauses,params,"p.in_service_period","inServicePeriod",req.getInServicePeriod());
        if (req.getKeyword()!=null && !req.getKeyword().isBlank()) {
            params.put("keyword","%"+req.getKeyword().trim()+"%");
            clauses.add("(p.name LIKE #{query.parameters.keyword} OR p.mobile LIKE #{query.parameters.keyword} OR p.wechat_id LIKE #{query.parameters.keyword} OR p.person_no LIKE #{query.parameters.keyword} OR rl.lead_no LIKE #{query.parameters.keyword})");
        }
        params.put("sortField",req.getSortField()); params.put("sortOrder",req.getSortOrder());
        params.put("limit",req.getPageSize()); params.put("offset",(long)(req.getPageNo()-1)*req.getPageSize());
        return new AdvancedFilterQuery(String.join(" AND ",clauses),params);
    }
    private static void add(List<String> clauses,Map<String,Object> params,String column,String key,Object value) {
        if(value!=null) { params.put(key,value); clauses.add(column+"=#{query.parameters."+key+"}"); }
    }
}
