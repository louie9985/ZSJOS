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
    @org.junit.jupiter.api.BeforeEach
    void denyUnconfiguredFeaturePermissions() {
        org.mockito.Mockito.lenient().when(permissionApi.hasAnyPermissions(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(String[].class))).thenReturn(false);
    }

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
    @Mock cn.iocoder.yudao.module.zsjos.service.task.MenuTaskSummaryService taskReminders;
    @BeforeEach void before(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void after(){TenantContextHolder.clear();}
    @Test void annotationFiltersRequireReadPermission() {
        var req = new StudentServicePageReqVO(); req.setServiceNeeded("false");
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> service.query(7L, req));
    }
    @Test void annotationFiltersMatchSameServiceAndTenantBeforePagination() {
        when(permissionApi.hasAnyPermissions(7L, StudentServiceAnnotationConstants.READ)).thenReturn(true);
        var req = new StudentServicePageReqVO(); req.setProxyStudent("true"); req.setServiceNeeded("false");
        var q = service.query(7L, req);
        assertTrue(q.getWhereSql().contains("sa.service_relation_id=sr.id"));
        assertTrue(q.getWhereSql().contains("sa.tenant_id=sr.tenant_id AND sa.deleted=0"));
        assertEquals("true", q.getParameters().get("proxyStudent"));
        assertEquals("false", q.getParameters().get("serviceNeeded"));
        assertTrue(q.getParameters().containsKey("subject"));
    }
    @Test void annotationDefaultsIncludeMissingNullAndDeletedRowsInCombinedFilters() throws Exception {
        when(permissionApi.hasAnyPermissions(7L, StudentServiceAnnotationConstants.READ)).thenReturn(true);
        try (var connection = java.sql.DriverManager.getConnection("jdbc:h2:mem:annotationDefaults;MODE=MySQL");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sr(id BIGINT, tenant_id BIGINT)");
            statement.execute("CREATE TABLE zsjos_student_service_annotation(service_relation_id BIGINT, tenant_id BIGINT, deleted INT, proxy_student VARCHAR(8), service_needed VARCHAR(8))");
            statement.execute("INSERT INTO sr VALUES(1,1),(2,1),(3,1),(4,1),(5,1),(6,1)");
            // Missing, NULL, explicit defaults, inverse values, deleted, and another tenant's annotation.
            statement.execute("INSERT INTO zsjos_student_service_annotation VALUES(2,1,0,NULL,NULL),(3,1,0,'false','true'),(4,1,0,'true','false'),(5,1,1,'true','false'),(6,2,0,'true','false')");
            for (String proxy : List.of("false", "true")) {
                for (String needed : List.of("false", "true")) {
                    var req = new StudentServicePageReqVO(); req.setProxyStudent(proxy); req.setServiceNeeded(needed);
                    String where = service.query(7L, req).getWhereSql();
                    String predicate = where.substring(where.indexOf("COALESCE((SELECT sa.proxy_student"))
                            .replace("#{query.parameters.proxyStudent}", "'" + proxy + "'")
                            .replace("#{query.parameters.serviceNeeded}", "'" + needed + "'");
                    List<Long> ids = new ArrayList<>();
                    try (var rows = statement.executeQuery("SELECT id FROM sr WHERE " + predicate + " ORDER BY id")) {
                        while (rows.next()) ids.add(rows.getLong(1));
                    }
                    assertEquals(proxy.equals("false") && needed.equals("true") ? List.of(1L,2L,3L,5L,6L)
                            : proxy.equals("true") && needed.equals("false") ? List.of(4L) : List.of(), ids);
                }
            }
        }
    }
    @Test void queryAllRemovesOwnerScopeBeforePaginationButKeepsTeachingAndTenantBoundaries() {
        when(permissionApi.hasAnyPermissions(7L, StudentReadScope.QUERY_ALL)).thenReturn(true);
        var req = new StudentServicePageReqVO(); req.setPageNo(2); req.setPageSize(10);
        var query = service.query(7L, req);
        assertFalse(query.getParameters().containsKey("subject"));
        assertFalse(query.getWhereSql().contains("sr.owner_user_id"));
        assertTrue(query.getWhereSql().contains("sr.teaching_delivery=1"));
        assertTrue(query.getWhereSql().contains("sr.status IN ('active','paused','completed')"));
        assertEquals(1L, query.getParameters().get("tenantId"));
        assertEquals(10L, query.getParameters().get("offset"));
        verifyNoInteractions(classScope, userApi);
        req.setReadScope("SELF"); when(readScope.resolve("SELF", null, 7L)).thenReturn(7L);
        assertEquals(7L, service.query(7L, req).getParameters().get("subject"));
    }

    @Test void classQueryAllDoesNotGrantStudentQueryAll() {
        org.mockito.Mockito.lenient().when(permissionApi.hasAnyPermissions(7L, DeliveryClassService.PERMISSION_QUERY_ALL)).thenReturn(true);
        assertEquals(7L, service.query(7L, new StudentServicePageReqVO()).getParameters().get("subject"));
    }
    @Test void agentKeywordRequiresAnnotationReadAndStaysWithinServiceTenant() {
        var req = new StudentServicePageReqVO(); req.setKeyword("  代理姓名  ");
        assertFalse(service.query(7L, req).getWhereSql().contains("sa.agent_name"));
        when(permissionApi.hasAnyPermissions(7L, StudentServiceAnnotationConstants.READ)).thenReturn(true);
        var query = service.query(7L, req);
        assertEquals("%代理姓名%", query.getParameters().get("keyword"));
        assertTrue(query.getWhereSql().contains("OR EXISTS (SELECT 1 FROM zsjos_student_service_annotation sa WHERE sa.service_relation_id=sr.id AND sa.tenant_id=sr.tenant_id AND sa.deleted=0 AND sa.agent_name LIKE #{query.parameters.keyword})"));
        assertTrue(query.getWhereSql().contains("sr.owner_user_id"));
    }

    @Test void nameKeywordAndSortUseServiceSnapshot() {
        var req = new StudentServicePageReqVO(); req.setKeyword("订单姓名"); req.setSortField("name"); req.setSortOrder("ascend");
        var q = service.query(7L, req);
        assertTrue(q.getWhereSql().contains("sr.student_name_snapshot LIKE"));
        assertFalse(q.getWhereSql().contains("p.name LIKE"));
        assertEquals("%订单姓名%", q.getParameters().get("keyword"));
        assertTrue(StudentServicePageSql.page(Map.of("query", q)).contains("ORDER BY sr.student_name_snapshot ASC"));
    }
    @Test void twoCoursesOfSamePersonRemainTwoRowsAndDatabaseTotal(){
        var a=new ServiceRelationDO();a.setId(11L);a.setPersonId(9L);
        var b=new ServiceRelationDO();b.setId(12L);b.setPersonId(9L);
        var reminder = new cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.StudentTaskReminderRespVO(1L,"student_contact","学员跟进",null,false);
        when(taskReminders.getMyStudentReminders(7L,List.of(11L,12L))).thenReturn(Map.of(12L,List.of(reminder)));
        when(pageMapper.count(any())).thenReturn(2L);when(pageMapper.page(any())).thenReturn(List.of(a,b));
        for(var r:List.of(a,b)){var p=new MyStudentRespVO();p.setPersonId(9L);
            var v=new MyStudentRespVO.ServiceVO();v.setServiceRelationId(r.getId());p.setServices(List.of(v));
            when(projection.convert(7L,9L,List.of(r))).thenReturn(p);}
        var result=service.page(7L,new StudentServicePageReqVO());
        assertEquals(2,result.getTotal());assertEquals(List.of(11L,12L),result.getList().stream().map(StudentServiceRespVO::getServiceRelationId).toList());
        assertTrue(result.getList().stream().allMatch(r->r.getPersonId()==9L));
        assertTrue(result.getList().getFirst().getPendingTasks().isEmpty());
        assertEquals(List.of(reminder),result.getList().getLast().getPendingTasks());
    }
    @Test void personalPriorityIsOptInBoundAndDoesNotChangeVisibilityClause(){
        var req = new StudentServicePageReqVO();
        var before = service.query(7L,req);
        req.setPendingTasksFirst(true);
        when(taskReminders.getMyStudentReminderIds(7L)).thenReturn(List.of(12L));
        var q = service.query(7L,req);
        assertEquals(before.getWhereSql(),q.getWhereSql());
        assertEquals(12L,q.getParameters().get("priorityRelation0"));
        String sql = StudentServicePageSql.page(Map.of("query",q));
        assertTrue(sql.contains("ORDER BY CASE WHEN sr.id IN (#{query.parameters.priorityRelation0}) THEN 0 ELSE 1 END ASC,sr.update_time"));
        assertFalse(sql.contains("IN (12)"));
    }

    @Test void realPriorityQueryPinsAcrossPagesAndKeepsTenantDeletionOwnershipAndTeachingScope() throws Exception {
        try(var db=java.sql.DriverManager.getConnection("jdbc:h2:mem:student_priority"+System.nanoTime()+";MODE=MySQL","sa","")) {
            db.createStatement().execute("CREATE TABLE zsjos_service_relation(id BIGINT,student_name_snapshot VARCHAR,tenant_id BIGINT,deleted INT,teaching_delivery INT,owner_user_id BIGINT,person_id BIGINT,order_id BIGINT,order_item_id BIGINT,class_id BIGINT)");
            db.createStatement().execute("CREATE TABLE zsjos_person(id BIGINT,tenant_id BIGINT,deleted INT)");
            db.createStatement().execute("INSERT INTO zsjos_person VALUES(9,1,0),(9,2,0)");
            for(String table:List.of("zsjos_order","zsjos_order_item","zsjos_lead","zsjos_delivery_class"))
                db.createStatement().execute("CREATE TABLE "+table+"(id BIGINT,tenant_id BIGINT,deleted INT,lead_id BIGINT,order_id BIGINT)");
            for(int i=1;i<=24;i++) db.createStatement().execute("INSERT INTO zsjos_service_relation VALUES("+i+",'N"+String.format("%02d",i)+"',"+(i==21?2:1)+","+(i==22?1:0)+","+(i==24?0:1)+","+(i==23?8:7)+",9,NULL,NULL,NULL)");
            var params=new LinkedHashMap<String,Object>();params.put("tenantId",1L);params.put("actor",7L);params.put("sortField","name");params.put("sortOrder","ascend");params.put("limit",2);params.put("offset",0);
            params.put("priorityRelationCount",5);
            var ids=List.of(18L,19L,21L,23L,24L);for(int i=0;i<ids.size();i++)params.put("priorityRelation"+i,ids.get(i));
            var q=new AdvancedFilterQuery("sr.owner_user_id=#{query.parameters.actor}",params);
            assertEquals(List.of(18L,19L),new ArrayList<>(mysqlIds(db,"",q)));
            params.put("offset",2);assertEquals(List.of(1L,2L),new ArrayList<>(mysqlIds(db,"",q)));
            params.put("offset",0);params.put("priorityRelationCount",0);assertEquals(List.of(1L,2L),new ArrayList<>(mysqlIds(db,"",q)));
        }
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

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="ZSJOS_CLASS_MYSQL_VERIFY", matches="true")
    void mysqlNameSearchSortAndAdvancedFilterStayOnCurrentVisibleOrder() throws Exception {
        String fixtures = "WITH service_rows AS ("
                + "SELECT 1 id,1 tenant_id,0 deleted,9 person_id,81 order_id,7 owner_user_id "
                + "UNION ALL SELECT 2,1,0,9,82,7 UNION ALL SELECT 3,1,0,9,83,99 "
                + "UNION ALL SELECT 4,1,0,9,NULL,7 UNION ALL SELECT 5,1,0,9,84,7),"
                + "zsjos_service_relation AS (SELECT service_rows.*,NULL class_id,NULL order_item_id,"
                + "NULL content_director_user_id,NULL career_planner_user_id,'accepted' acceptance_status,'active' status,NOW() update_time FROM service_rows),"
                + "zsjos_person AS (SELECT 9 id,1 tenant_id,0 deleted,'PersonOnly' name,'fixture' person_no,NULL mobile,NULL wechat_id),"
                + "zsjos_order AS (SELECT 81 id,1 tenant_id,0 deleted,0 lead_id,'FirstOrder' student_name "
                + "UNION ALL SELECT 82,1,0,0,'SecondOrder' UNION ALL SELECT 83,1,0,0,'HiddenOrder' UNION ALL SELECT 84,1,1,0,'DeletedOrder'),"
                + "zsjos_order_item AS (SELECT 0 id,0 order_id,1 tenant_id,0 deleted),"
                + "zsjos_lead AS (SELECT 0 id,1 tenant_id,0 deleted,NULL lead_no),"
                + "zsjos_delivery_class AS (SELECT 0 id,1 tenant_id,0 deleted,0 system_class) ";
        try (var db = java.sql.DriverManager.getConnection(System.getenv("ZSJOS_CLASS_MYSQL_URL"),
                System.getenv("ZSJOS_CLASS_MYSQL_USER"), System.getenv("ZSJOS_CLASS_MYSQL_PASSWORD"))) {
            db.setReadOnly(true);
            var req = new StudentServicePageReqVO(); req.setKeyword("SecondOrder");
            assertEquals(Set.of(2L), mysqlIds(db, fixtures, service.query(7L, req)));
            for (String missing : List.of("PersonOnly", "HiddenOrder", "DeletedOrder")) {
                req.setKeyword(missing); assertTrue(mysqlIds(db, fixtures, service.query(7L, req)).isEmpty());
            }
            req.setKeyword(null); req.setSortField("name"); req.setSortOrder("descend");
            assertEquals(List.of(2L,1L,5L,4L), new ArrayList<>(mysqlIds(db, fixtures, service.query(7L, req))));
            var group = new cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO();
            var condition = new cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterConditionReqVO();
            condition.setFieldKey("person.name"); condition.setOperator("eq"); condition.setValue("FirstOrder");
            group.setLogic("AND"); group.setGroups(List.of()); group.setConditions(List.of(condition));
            req.setAdvancedFilter(group);
            when(filters.buildStudentServiceQuery(group)).thenReturn(new AdvancedFilterService().buildStudentServiceQuery(group));
            assertEquals(Set.of(1L), mysqlIds(db, fixtures, service.query(7L, req)));
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
