package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentServicePageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentServicePageSql;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterQuery;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.*;
import cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercise the real scope resolver: mocking its result alone missed the SELF-to-department leak. */
class StudentReadScopeRegressionTest {
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final AdminUserApi users = mock(AdminUserApi.class);
    private final ServiceRelationMapper relations = mock(ServiceRelationMapper.class);
    private final DeliveryClassMapper classes = mock(DeliveryClassMapper.class);
    private final DeliveryClassScopeService scope = new DeliveryClassScopeService();
    private final StudentServiceReadService reads = new StudentServiceReadService();
    private final StudentServiceObjectPermissionProvider objects = new StudentServiceObjectPermissionProvider();

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var self = new DeptDataPermissionRespDTO(); self.setSelf(true);
        when(permissions.getDeptDataPermission(7L)).thenReturn(self);
        when(permissions.hasAnyPermissions(7L, "zsjos:delivery-class:query", DeliveryClassService.PERMISSION_QUERY_MANAGED)).thenReturn(true);
        ReflectionTestUtils.setField(scope, "permissionApi", permissions);
        ReflectionTestUtils.setField(scope, "adminUserApi", users);
        ReflectionTestUtils.setField(scope, "classMapper", classes);
        ReflectionTestUtils.setField(reads, "permissionApi", permissions);
        ReflectionTestUtils.setField(reads, "userApi", users);
        ReflectionTestUtils.setField(reads, "classScope", scope);
        ReflectionTestUtils.setField(reads, "filters", mock(AdvancedFilterService.class));
        ReflectionTestUtils.setField(reads, "relationMapper", relations);
        ReflectionTestUtils.setField(objects, "permissionApi", permissions);
        ReflectionTestUtils.setField(objects, "adminUserApi", users);
        ReflectionTestUtils.setField(objects, "deliveryClassScopeService", scope);
        ReflectionTestUtils.setField(objects, "relationMapper", relations);
        ReflectionTestUtils.setField(objects, "deliveryClassMapper", classes);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void ordinaryQueryPlusSelfNeverLooksUpSameDepartmentMembers() {
        var query = reads.query(7L, new StudentServicePageReqVO());
        assertEquals(7L, query.getParameters().get("owner0"));
        assertFalse(query.getParameters().containsKey("owner1"));
        assertFalse(query.getParameters().containsKey("pendingClassId"));
        assertTrue(query.getWhereSql().contains("sr.career_planner_user_id"));
        verifyNoInteractions(users, classes);
    }

    @Test void forbiddenServiceAlsoDeniesDetailContactContextAndRecords() {
        var relation = relation(10L, 8L);
        when(relations.selectById(10L)).thenReturn(relation);
        assertFalse(objects.hasPermission(10L, "read", 7L));
        var contact = new StudentContactServiceImpl();
        ReflectionTestUtils.setField(contact, "relationMapper", relations);
        ReflectionTestUtils.setField(contact, "permissionApi", permissions);
        ReflectionTestUtils.setField(contact, "serviceReadPermission", objects);
        assertThrows(ServiceException.class, () -> contact.getContext(10L, 7L));
        assertThrows(ServiceException.class, () -> contact.getRecords(10L, new PageParam(), 7L));
        verifyNoInteractions(users);
    }

    @Test void ownAndPersonalCollaboratorReadsSurviveWithoutGrantingSiblingCommands() {
        var own = relation(10L, 7L); var sibling = relation(11L, 8L);
        sibling.setCareerPlannerUserId(7L);
        when(relations.selectById(10L)).thenReturn(own);
        when(relations.selectById(11L)).thenReturn(sibling);
        assertTrue(objects.hasPermission(10L, "read", 7L));
        assertTrue(objects.hasPermission(11L, "read", 7L));
        assertFalse(objects.hasPermission(11L, "contact", 7L));
        sibling.setCareerPlannerUserId(null);
        assertFalse(objects.hasPermission(11L, "read", 7L));
    }

    @Test void configuredDepartmentsAndSelfAreIndependentGrants() {
        var permission = new DeptDataPermissionRespDTO(); permission.setSelf(true); permission.setDeptIds(Set.of(20L));
        when(permissions.getDeptDataPermission(7L)).thenReturn(permission);
        when(users.getUserListByDeptIds(Set.of(20L))).thenReturn(List.of(new AdminUserRespDTO().setId(8L)));
        var query = reads.query(7L, new StudentServicePageReqVO());
        assertEquals(8L, query.getParameters().get("owner1"));
        var in = relation(10L, 8L); var out = relation(11L, 9L);
        when(relations.selectById(10L)).thenReturn(in); when(relations.selectById(11L)).thenReturn(out);
        assertTrue(objects.hasPermission(10L, "read", 7L));
        assertFalse(objects.hasPermission(11L, "read", 7L));
        assertFalse(objects.hasPermission(10L, "contact", 7L));
        verify(users, never()).getUser(7L);
    }

    @Test void pendingClassRequiresTheSeparateManagedGrantEvenWithSelfScope() {
        when(permissions.hasAnyPermissions(7L, DeliveryClassService.PERMISSION_QUERY_MANAGED)).thenReturn(true);
        when(classes.selectPending()).thenReturn(new DeliveryClassDO().setId(90L));
        assertEquals(90L, reads.query(7L, new StudentServicePageReqVO()).getParameters().get("pendingClassId"));
        var pending = relation(10L, null); pending.setClassId(90L);
        when(relations.selectById(10L)).thenReturn(pending);
        assertTrue(objects.hasPermission(10L, "read", 7L));
        assertFalse(objects.hasPermission(10L, "accept", 7L));
        pending.setClassId(91L); assertFalse(objects.hasPermission(10L, "read", 7L));
    }

    @Test void classDeepLinkCannotBypassSelfScopeButOwnClassRemainsReadable() {
        var provider = new DeliveryClassObjectPermissionProvider();
        ReflectionTestUtils.setField(provider, "mapper", classes);
        ReflectionTestUtils.setField(provider, "permissionApi", permissions);
        ReflectionTestUtils.setField(provider, "scopeService", scope);
        var row = new DeliveryClassDO().setId(10L).setSystemClass(false).setDeptId(20L).setHomeroomUserId(8L);
        when(classes.selectById(10L)).thenReturn(row);
        assertFalse(provider.hasPermission(10L, "read", 7L));
        row.setHomeroomUserId(7L);
        when(permissions.hasAnyPermissions(7L, "zsjos:delivery-class:query", DeliveryClassService.PERMISSION_QUERY_MANAGED,
                DeliveryClassService.PERMISSION_QUERY_MY)).thenReturn(true);
        assertTrue(provider.hasPermission(10L, "read", 7L));
        verifyNoInteractions(users);
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="ZSJOS_CLASS_MYSQL_VERIFY", matches="true")
    void mysqlEvaluatesRealScopeAndCountBeforePaginationWithoutPersistentWrites() throws Exception {
        String fixture = "WITH scope_rows AS ("
                + "SELECT 10 id,1 tenant_id,0 deleted,70 person_id,91 class_id,7 owner_user_id,NULL content_director_user_id,NULL career_planner_user_id,'accepted' acceptance_status,'active' status "
                + "UNION ALL SELECT 11,1,0,70,91,8,NULL,NULL,'accepted','active' "
                + "UNION ALL SELECT 12,1,0,70,91,9,NULL,7,'accepted','active' "
                + "UNION ALL SELECT 13,1,0,70,91,8,NULL,7,'pending','active' "
                + "UNION ALL SELECT 14,1,0,70,91,8,NULL,7,'accepted','paused' "
                + "UNION ALL SELECT 15,1,0,70,90,NULL,NULL,NULL,'accepted','active' "
                + "UNION ALL SELECT 16,2,0,70,91,7,NULL,NULL,'accepted','active' "
                + "UNION ALL SELECT 17,1,1,70,91,7,NULL,NULL,'accepted','active' "
                + "UNION ALL SELECT 18,1,0,70,91,7,NULL,NULL,'accepted','terminated'),"
                + "zsjos_service_relation AS (SELECT scope_rows.*,NULL order_id,NULL order_item_id,NOW() update_time FROM scope_rows),"
                + "zsjos_person AS (SELECT 70 id,1 tenant_id,0 deleted UNION ALL SELECT 70,2,0),"
                + "zsjos_order AS (SELECT 0 id,1 tenant_id,0 deleted,0 lead_id),"
                + "zsjos_order_item AS (SELECT 0 id,0 order_id,1 tenant_id,0 deleted),"
                + "zsjos_lead AS (SELECT 0 id,1 tenant_id,0 deleted),"
                + "zsjos_delivery_class AS (SELECT 90 id,1 tenant_id,0 deleted,1 system_class UNION ALL SELECT 91,1,0,0) ";
        try (var db = java.sql.DriverManager.getConnection(System.getenv("ZSJOS_CLASS_MYSQL_URL"),
                System.getenv("ZSJOS_CLASS_MYSQL_USER"), System.getenv("ZSJOS_CLASS_MYSQL_PASSWORD"))) {
            db.setReadOnly(true);
            var req = new StudentServicePageReqVO();
            var query = reads.query(7L, req);
            assertEquals(Set.of(10L, 12L), mysqlIds(db, fixture, query));
            assertEquals(2L, mysqlCount(db, fixture, query));
            req.setPageSize(1); req.setPageNo(2);
            assertEquals(Set.of(10L), mysqlIds(db, fixture, reads.query(7L, req)));
            assertEquals(2L, mysqlCount(db, fixture, reads.query(7L, req)));
            req.setPageNo(1); req.setPageSize(10);
            when(permissions.hasAnyPermissions(7L, DeliveryClassService.PERMISSION_QUERY_MANAGED)).thenReturn(true);
            when(classes.selectPending()).thenReturn(new DeliveryClassDO().setId(90L));
            assertEquals(Set.of(10L, 12L, 15L), mysqlIds(db, fixture, reads.query(7L, req)));
            var grant = new DeptDataPermissionRespDTO(); grant.setDeptIds(Set.of(20L));
            when(permissions.getDeptDataPermission(7L)).thenReturn(grant);
            when(users.getUserListByDeptIds(Set.of(20L))).thenReturn(List.of(new AdminUserRespDTO().setId(8L)));
            assertEquals(Set.of(10L, 11L, 12L, 13L, 14L, 15L), mysqlIds(db, fixture, reads.query(7L, req)));
        }
    }

    private Set<Long> mysqlIds(java.sql.Connection db, String fixture, AdvancedFilterQuery query) throws Exception {
        try (var statement = bind(db, fixture + StudentServicePageSql.page(Map.of("query", query)), query);
             var result = statement.executeQuery()) {
            Set<Long> ids = new LinkedHashSet<>(); while (result.next()) ids.add(result.getLong("id")); return ids;
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="ZSJOS_SCOPE_TARGET_USERNAME", matches=".+")
    void configuredAccountAndStudentAreEvaluatedByCurrentSqlWithoutChangingGrantsOrOwnership() throws Exception {
        try (var db = java.sql.DriverManager.getConnection(System.getenv("ZSJOS_CLASS_MYSQL_URL"),
                System.getenv("ZSJOS_CLASS_MYSQL_USER"), System.getenv("ZSJOS_CLASS_MYSQL_PASSWORD"))) {
            db.setReadOnly(true);
            long actor; long department;
            try (var statement = db.prepareStatement("SELECT id,dept_id FROM system_users WHERE tenant_id=1 AND deleted=0 AND status=0 AND username=?")) {
                statement.setString(1, System.getenv("ZSJOS_SCOPE_TARGET_USERNAME"));
                try (var result = statement.executeQuery()) { assertTrue(result.next(), "Target account must exist"); actor=result.getLong(1); department=result.getLong(2); }
            }
            try (var statement = db.prepareStatement("SELECT r.data_scope FROM system_user_role ur JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=1 AND r.deleted=0 AND r.status=0 WHERE ur.tenant_id=1 AND ur.deleted=0 AND ur.user_id=?")) {
                statement.setLong(1, actor);
                try (var result = statement.executeQuery()) {
                    int count=0; while(result.next()) { assertEquals(5, result.getInt(1), "Read-only target regression requires unchanged SELF grants"); count++; }
                    assertTrue(count>0);
                }
            }
            long person;
            try (var statement = db.prepareStatement("SELECT id FROM zsjos_person WHERE tenant_id=1 AND deleted=0 AND person_no=?")) {
                statement.setString(1, System.getenv("ZSJOS_SCOPE_TARGET_STUDENT"));
                try (var result=statement.executeQuery()) { assertTrue(result.next()); person=result.getLong(1); }
            }
            var self=new DeptDataPermissionRespDTO(); self.setSelf(true);
            when(permissions.getDeptDataPermission(actor)).thenReturn(self);
            when(permissions.hasAnyPermissions(actor,"zsjos:delivery-class:query",DeliveryClassService.PERMISSION_QUERY_MANAGED)).thenReturn(true);
            var req=new StudentServicePageReqVO(); req.setPersonId(person);
            assertEquals(0L, mysqlCount(db, "", reads.query(actor,req)));
            assertEquals(Set.of(), mysqlIds(db, "", reads.query(actor,req)));
            List<AdminUserRespDTO> members=new ArrayList<>();
            try(var statement=db.prepareStatement("SELECT id FROM system_users WHERE tenant_id=1 AND deleted=0 AND dept_id=?")) {
                statement.setLong(1, department);
                try(var result=statement.executeQuery()) { while(result.next()) members.add(new AdminUserRespDTO().setId(result.getLong(1))); }
            }
            var expanded=new DeptDataPermissionRespDTO(); expanded.setDeptIds(Set.of(department));
            when(permissions.getDeptDataPermission(actor)).thenReturn(expanded);
            when(users.getUserListByDeptIds(Set.of(department))).thenReturn(members);
            assertTrue(mysqlCount(db, "", reads.query(actor,req))>0, "Control must reproduce the old department expansion");
            Long owner;
            try(var statement=db.prepareStatement("SELECT owner_user_id FROM zsjos_service_relation WHERE tenant_id=1 AND deleted=0 AND person_id=? AND owner_user_id IS NOT NULL AND status='active' LIMIT 1")) {
                statement.setLong(1,person);
                try(var result=statement.executeQuery()) { assertTrue(result.next()); owner=result.getLong(1); }
            }
            when(permissions.getDeptDataPermission(owner)).thenReturn(self);
            when(permissions.hasAnyPermissions(owner,"zsjos:delivery-class:query",DeliveryClassService.PERMISSION_QUERY_MANAGED)).thenReturn(true);
            assertTrue(mysqlCount(db, "", reads.query(owner,req))>0, "Actual owner retains its course");
        }
    }

    private long mysqlCount(java.sql.Connection db, String fixture, AdvancedFilterQuery query) throws Exception {
        try (var statement = bind(db, fixture + StudentServicePageSql.count(Map.of("query", query)), query);
             var result = statement.executeQuery()) { assertTrue(result.next()); return result.getLong(1); }
    }

    private java.sql.PreparedStatement bind(java.sql.Connection db, String sql, AdvancedFilterQuery query) throws Exception {
        sql = sql.replace("${query.whereSql}", query.getWhereSql());
        var matcher = java.util.regex.Pattern.compile("#\\{query.parameters.([A-Za-z0-9]+)}").matcher(sql);
        List<Object> values = new ArrayList<>(); StringBuffer bound = new StringBuffer();
        while (matcher.find()) { values.add(query.getParameters().get(matcher.group(1))); matcher.appendReplacement(bound, "?"); }
        matcher.appendTail(bound); var statement = db.prepareStatement(bound.toString());
        for (int i = 0; i < values.size(); i++) statement.setObject(i + 1, values.get(i));
        return statement;
    }

    private ServiceRelationDO relation(Long id, Long owner) {
        var row = new ServiceRelationDO(); row.setId(id); row.setPersonId(70L); row.setOwnerUserId(owner);
        row.setStatus("active"); row.setAcceptanceStatus("accepted"); return row;
    }
}
