package cn.iocoder.yudao.module.zsjos.service.registration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.*;
import cn.iocoder.yudao.module.zsjos.service.common.BusinessReadScopeService;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class StudentServiceReadServiceTest {
    @InjectMocks StudentServiceReadService service;
    @Mock StudentServicePageMapper pageMapper;
    @Mock ServiceRelationMapper relationMapper;
    @Mock MyStudentServiceImpl projection;
    @Mock PermissionApi permissionApi;
    @Mock AdminUserApi userApi;
    @Mock DeliveryClassScopeService classScope;
    @Mock BusinessReadScopeService readScope;
    @Mock AdvancedFilterService filters;
    @Mock StudentServiceSummaryReader summaryReader;
    @BeforeEach void before(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void after(){TenantContextHolder.clear();}
    @Test void twoCoursesOfSamePersonRemainTwoRowsAndDatabaseTotal(){
        var a=new ServiceRelationDO();a.setId(11L);a.setPersonId(9L);
        var b=new ServiceRelationDO();b.setId(12L);b.setPersonId(9L);
        when(pageMapper.count(any())).thenReturn(2L);when(pageMapper.page(any())).thenReturn(List.of(a,b));
        for(var r:List.of(a,b)){var p=new MyStudentRespVO();p.setPersonId(9L);
            var v=new MyStudentRespVO.ServiceVO();v.setServiceRelationId(r.getId());p.setServices(List.of(v));
            when(projection.convert(7L,9L,List.of(r))).thenReturn(p);}
        var result=service.page(7L,new StudentServicePageReqVO());
        assertEquals(2,result.getTotal());assertEquals(List.of(11L,12L),result.getList().stream().map(StudentServiceRespVO::getServiceRelationId).toList());
        assertTrue(result.getList().stream().allMatch(r->r.getPersonId()==9L));
    }
    @Test void currentServicePredicatesAreBoundBeforePagination(){
        var req=new StudentServicePageReqVO();req.setClassId(31L);req.setPersonId(9L);req.setPageNo(2);req.setPageSize(10);
        var q=service.query(7L,req);assertTrue(q.getWhereSql().contains("sr.class_id="));
        assertEquals(31L,q.getParameters().get("classId"));assertEquals(10L,q.getParameters().get("offset"));
        assertEquals(1L,q.getParameters().get("tenantId"));assertEquals(7L,q.getParameters().get("subject"));
        assertTrue(q.getWhereSql().contains("sr.acceptance_status"));
    }
    @Test void explicitAllRequiresExistingScopeResolver(){
        var req=new StudentServicePageReqVO();req.setReadScope("ALL");
        when(readScope.resolve("ALL",null,7L)).thenThrow(new SecurityException());
        assertThrows(SecurityException.class,()->service.page(7L,req));verifyNoInteractions(pageMapper);
    }
    @Test void invisibleSiblingCannotBeReadThroughPerson(){
        var r=new ServiceRelationDO();r.setId(12L);r.setPersonId(9L);when(relationMapper.selectById(12L)).thenReturn(r);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.detail(7L,12L));
        verifyNoInteractions(projection);
    }
    @Test void managerScopeUsesServiceOwnerAndBoundDepartmentMembers(){
        when(permissionApi.hasAnyPermissions(7L,"zsjos:delivery-class:query",DeliveryClassService.PERMISSION_QUERY_MANAGED)).thenReturn(true);
        when(classScope.resolveRead(7L)).thenReturn(new DeliveryClassScopeService.Scope(false,Set.of(31L)));
        when(userApi.getUserListByDeptIds(Set.of(31L))).thenReturn(List.of(new cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO().setId(8L)));
        var q=service.query(7L,new StudentServicePageReqVO());
        assertTrue(q.getWhereSql().contains("sr.owner_user_id IN"));
        assertTrue(q.getWhereSql().contains("sr.content_director_user_id"));
        assertEquals(8L,q.getParameters().get("owner1"));
    }
    @Test void pendingClassExtendsManagedReadButNotExplicitSelfOrUnownedFormalServices(){
        when(permissionApi.hasAnyPermissions(7L,"zsjos:delivery-class:query",DeliveryClassService.PERMISSION_QUERY_MANAGED)).thenReturn(true);
        when(classScope.resolveRead(7L)).thenReturn(new DeliveryClassScopeService.Scope(false,Set.of()));
        when(classScope.readablePendingClassId(7L)).thenReturn(90L);
        var req = new StudentServicePageReqVO(); req.setClassId(90L);
        var q = service.query(7L, req);
        assertTrue(q.getWhereSql().contains("OR (dc.system_class=1 AND dc.id=#{query.parameters.pendingClassId}"));
        assertFalse(q.getWhereSql().contains("owner_user_id IS NULL"));
        assertEquals(90L, q.getParameters().get("pendingClassId"));
        assertEquals(90L, q.getParameters().get("classId"));
        assertEquals(1L, q.getParameters().get("tenantId"));
        req.setReadScope("SELF"); when(readScope.resolve("SELF",null,7L)).thenReturn(7L);
        assertFalse(service.query(7L,req).getParameters().containsKey("pendingClassId"));
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="ZSJOS_CLASS_MYSQL_VERIFY", matches="true")
    void mysqlEvaluatesPendingScopeWithoutWritingBusinessData() throws Exception {
        when(permissionApi.hasAnyPermissions(7L,"zsjos:delivery-class:query",DeliveryClassService.PERMISSION_QUERY_MANAGED)).thenReturn(true);
        when(classScope.resolveRead(7L)).thenReturn(new DeliveryClassScopeService.Scope(false,Set.of()));
        when(classScope.readablePendingClassId(7L)).thenReturn(90L);
        // CTEs shadow table names only within this SELECT. No schema or persistent data changes.
        String fixtures = "WITH service_rows AS ("
                + "SELECT 1 id,1 tenant_id,0 deleted,9 person_id,90 class_id,NULL owner_user_id,'active' status,NULL order_id,NULL order_item_id,NOW() update_time "
                + "UNION ALL SELECT 2,1,0,9,90,99,'active',NULL,NULL,NOW() "
                + "UNION ALL SELECT 3,1,0,9,92,NULL,'active',NULL,NULL,NOW() "
                + "UNION ALL SELECT 4,1,0,9,92,7,'active',NULL,NULL,NOW() "
                + "UNION ALL SELECT 5,2,0,9,90,NULL,'active',NULL,NULL,NOW() "
                + "UNION ALL SELECT 6,1,1,9,90,NULL,'active',NULL,NULL,NOW() "
                + "UNION ALL SELECT 7,1,0,9,90,NULL,'terminated',NULL,NULL,NOW() "
                + "UNION ALL SELECT 8,1,0,9,92,99,'active',NULL,NULL,NOW() "
                + "UNION ALL SELECT 9,1,0,9,NULL,NULL,'active',NULL,NULL,NOW() "
                + "UNION ALL SELECT 10,1,0,9,91,NULL,'active',NULL,NULL,NOW() "
                + "UNION ALL SELECT 11,1,0,9,90,NULL,'paused',NULL,NULL,NOW()),"
                + "zsjos_service_relation AS (SELECT service_rows.*,NULL content_director_user_id,NULL career_planner_user_id,'accepted' acceptance_status FROM service_rows),"
                + "zsjos_person AS (SELECT 9 id,1 tenant_id,0 deleted UNION ALL SELECT 9,2,0),"
                + "zsjos_order AS (SELECT 0 id,1 tenant_id,0 deleted,0 lead_id),"
                + "zsjos_order_item AS (SELECT 0 id,0 order_id,1 tenant_id,0 deleted),"
                + "zsjos_lead AS (SELECT 0 id,1 tenant_id,0 deleted),"
                + "zsjos_delivery_class AS (SELECT 90 id,1 tenant_id,0 deleted,1 system_class "
                + "UNION ALL SELECT 90,2,0,1 UNION ALL SELECT 91,1,1,1 UNION ALL SELECT 92,1,0,0) ";
        try (var db = java.sql.DriverManager.getConnection(System.getenv("ZSJOS_CLASS_MYSQL_URL"),
                System.getenv("ZSJOS_CLASS_MYSQL_USER"),System.getenv("ZSJOS_CLASS_MYSQL_PASSWORD"))) {
            db.setReadOnly(true);
            var req = new StudentServicePageReqVO(); req.setClassId(90L);
            var pending = service.query(7L,req);
            assertEquals(Set.of(1L,2L,11L), mysqlIds(db, fixtures, pending));
            req.setClassId(null);
            assertEquals(Set.of(1L,2L,4L,11L), mysqlIds(db, fixtures, service.query(7L,req)));
            req.setServiceStatus("terminated");
            assertTrue(mysqlIds(db, fixtures, service.query(7L,req)).isEmpty());
        }
    }

    private Set<Long> mysqlIds(java.sql.Connection db, String fixtures, AdvancedFilterQuery q) throws Exception {
        String sql = fixtures + StudentServicePageSql.page(Map.of("query",q)).replace("${query.whereSql}",q.getWhereSql());
        var matcher = java.util.regex.Pattern.compile("#\\{query.parameters.([A-Za-z0-9]+)}").matcher(sql);
        List<Object> values = new ArrayList<>();
        StringBuffer bound = new StringBuffer();
        while(matcher.find()){values.add(q.getParameters().get(matcher.group(1)));matcher.appendReplacement(bound,"?");}
        matcher.appendTail(bound);
        try(var statement=db.prepareStatement(bound.toString())) {
            for(int i=0;i<values.size();i++)statement.setObject(i+1,values.get(i));
            Set<Long> ids=new LinkedHashSet<>();
            try(var result=statement.executeQuery()){while(result.next())ids.add(result.getLong("id"));}
            return ids;
        }
    }
}
