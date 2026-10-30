package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentServicePageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentMetricCountTest {
    @InjectMocks StudentServiceReadService service;
    @Mock StudentServicePageMapper mapper;
    @Mock PermissionApi permissions;
    @Mock AdvancedFilterService filters;
    @BeforeEach void before() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void after() { TenantContextHolder.clear(); }

    @Test void deniedFeatureDoesNotQueryDatabase() {
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.countMetricStudents(7L, new StudentServicePageReqVO()));
        verifyNoInteractions(mapper, filters);
    }

    @Test void metricUsesExistingServiceScopeAndAdvancedFilter() {
        when(permissions.hasAnyPermissions(7L, "zsjos:student:query-my")).thenReturn(true);
        when(filters.buildStudentServiceQuery(null)).thenReturn(new AdvancedFilterQuery("sr.class_id=#{query.parameters.selectedClass}", Map.of("selectedClass", 21L)));
        when(mapper.countPeople(any())).thenReturn(2L);
        assertEquals(2L, service.countMetricStudents(7L, new StudentServicePageReqVO()));
        var capture = ArgumentCaptor.forClass(AdvancedFilterQuery.class);
        verify(mapper).countPeople(capture.capture());
        var query = capture.getValue();
        assertTrue(query.getWhereSql().contains("sr.owner_user_id=#{query.parameters.subject}"));
        assertTrue(query.getWhereSql().contains("sr.class_id=#{query.parameters.selectedClass}"));
        assertEquals(7L, query.getParameters().get("subject"));
        assertEquals(1L, query.getParameters().get("tenantId"));
        verify(mapper, never()).page(any());
    }

    @Test void generatedSqlDeduplicatesVisiblePeopleAndExcludesOtherTenantsAndDeletedRows() throws Exception {
        try (var connection = java.sql.DriverManager.getConnection("jdbc:h2:mem:studentMetric;MODE=MySQL");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE zsjos_service_relation(id BIGINT,person_id BIGINT,tenant_id BIGINT,deleted INT,teaching_delivery INT,order_id BIGINT,order_item_id BIGINT,class_id BIGINT,owner_user_id BIGINT)");
            statement.execute("CREATE TABLE zsjos_person(id BIGINT,tenant_id BIGINT,deleted INT)");
            statement.execute("CREATE TABLE zsjos_order(id BIGINT,tenant_id BIGINT,deleted INT,lead_id BIGINT)");
            statement.execute("CREATE TABLE zsjos_order_item(id BIGINT,order_id BIGINT,tenant_id BIGINT,deleted INT)");
            statement.execute("CREATE TABLE zsjos_lead(id BIGINT,tenant_id BIGINT,deleted INT)");
            statement.execute("CREATE TABLE zsjos_delivery_class(id BIGINT,tenant_id BIGINT,deleted INT)");
            statement.execute("INSERT INTO zsjos_person VALUES(1,1,0),(2,1,0),(3,1,0),(4,2,0),(5,1,1),(6,1,0)");
            statement.execute("INSERT INTO zsjos_service_relation VALUES(1,1,1,0,1,NULL,NULL,NULL,7),(2,1,1,0,1,NULL,NULL,NULL,7),(3,2,1,0,1,NULL,NULL,NULL,7),(4,3,1,0,1,NULL,NULL,NULL,8),(5,4,2,0,1,NULL,NULL,NULL,7),(6,5,1,0,1,NULL,NULL,NULL,7),(7,6,1,1,1,NULL,NULL,NULL,7)");
            String sql = StudentServicePageSql.countPeople(Map.of())
                    .replace("#{query.parameters.tenantId}", "1")
                    .replace("${query.whereSql}", "sr.owner_user_id=7");
            try (var rows = statement.executeQuery(sql)) { assertTrue(rows.next()); assertEquals(2, rows.getLong(1)); }
        }
    }
}
