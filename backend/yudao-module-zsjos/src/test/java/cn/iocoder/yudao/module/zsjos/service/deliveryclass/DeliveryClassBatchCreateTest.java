package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassBatchCreateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliveryClassBatchCreateTest {
    private DeliveryClassService service;
    private DeliveryClassMapper mapper;
    private AdminUserApi users;
    private PermissionApi permissions;
    private JdbcTemplate jdbc;

    @BeforeEach void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:class_batch_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE classes(id BIGINT PRIMARY KEY, class_no VARCHAR(80) UNIQUE, name VARCHAR(100), owner_id BIGINT, exam_date VARCHAR(40))");
        mapper = mock(DeliveryClassMapper.class);
        users = mock(AdminUserApi.class);
        permissions = mock(PermissionApi.class);
        var roles = mock(RoleApi.class);
        var role = new RoleRespDTO(); role.setId(300L); role.setStatus(0);
        when(roles.getRoleByCode("study_planner")).thenReturn(role);
        when(users.getUser(9L)).thenReturn(user(9L, 80L, 0));
        for (long id : List.of(20L, 21L)) {
            when(users.getUser(id)).thenReturn(user(id, 80L, 0));
            when(permissions.getEnabledRoleIdsByUserId(id)).thenReturn(Set.of(300L));
        }
        var numbers = mock(DeliveryClassNumberService.class);
        var counter = new AtomicLong();
        when(numbers.next()).thenAnswer(call -> "BJ" + counter.incrementAndGet());
        doAnswer(call -> {
            DeliveryClassDO row = call.getArgument(0);
            row.setId(counter.get());
            jdbc.update("INSERT INTO classes VALUES(?,?,?,?,?)", row.getId(), row.getClassNo(), row.getClassName(), row.getHomeroomUserId(), row.getExamScheduleSnapshot());
            return 1;
        }).when(mapper).insert(any(DeliveryClassDO.class));
        var target = new DeliveryClassServiceImpl();
        ReflectionTestUtils.setField(target, "mapper", mapper);
        ReflectionTestUtils.setField(target, "adminUserApi", users);
        ReflectionTestUtils.setField(target, "permissionApi", permissions);
        ReflectionTestUtils.setField(target, "roleApi", roles);
        ReflectionTestUtils.setField(target, "deptApi", mock(DeptApi.class));
        ReflectionTestUtils.setField(target, "numberService", numbers);
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source), new AnnotationTransactionAttributeSource()));
        service = (DeliveryClassService) proxy.getProxy();
    }

    @Test void createsOneIndependentClassPerDistinctPlanner() {
        var ids = service.createBatch(request(20L, 21L, 20L), 9L);
        assertEquals(List.of(1L, 2L), ids);
        assertEquals(List.of(20L, 21L), jdbc.queryForList("SELECT owner_id FROM classes ORDER BY id", Long.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(DISTINCT class_no) FROM classes", Integer.class));
        assertEquals(List.of("同名班级", "同名班级"), jdbc.queryForList("SELECT name FROM classes ORDER BY id", String.class));
        assertEquals(List.of("2026-12-01", "2026-12-01"), jdbc.queryForList("SELECT exam_date FROM classes ORDER BY id", String.class));
    }

    @Test void supportsOnePlanner() {
        assertEquals(1, service.createBatch(request(20L), 9L).size());
        assertEquals(1, count());
    }

    @Test void rejectsMixedDepartmentBeforeAnyInsert() {
        when(users.getUser(21L)).thenReturn(user(21L, 81L, 0));
        var error = assertThrows(ServiceException.class, () -> service.createBatch(request(20L, 21L), 9L));
        assertEquals(DELIVERY_CLASS_PERMISSION_DENIED.getCode(), error.getCode());
        verify(mapper, never()).insert(any(DeliveryClassDO.class));
    }

    @Test void rejectsDisabledPlannerBeforeAnyInsert() {
        when(users.getUser(21L)).thenReturn(user(21L, 80L, 1));
        assertInvalidPlanner();
    }

    @Test void rejectsMissingPlannerRoleBeforeAnyInsert() {
        when(permissions.getEnabledRoleIdsByUserId(21L)).thenReturn(Set.of());
        assertInvalidPlanner();
    }

    @Test void rejectsMissingPlannerBeforeAnyInsert() {
        when(users.getUser(21L)).thenReturn(null);
        assertInvalidPlanner();
    }

    @Test void rollsBackEarlierInsertWhenLaterSaveFails() {
        doAnswer(call -> { throw new IllegalStateException("synthetic persistence failure"); })
                .when(mapper).insert(argThat((DeliveryClassDO row) -> row.getHomeroomUserId().equals(21L)));
        assertThrows(IllegalStateException.class, () -> service.createBatch(request(20L, 21L), 9L));
        verify(mapper, times(2)).insert(any(DeliveryClassDO.class));
        assertEquals(0, count());
    }

    @Test void validatesNameDateAndNonemptyNonNullSelection() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(request(20L)).isEmpty());
            assertFalse(validator.validate(request()).isEmpty());
            var req = request(20L); req.setHomeroomUserIds(null);
            assertFalse(validator.validate(req).isEmpty());
            req = request(20L); req.setHomeroomUserIds(Arrays.asList(20L, null));
            assertFalse(validator.validate(req).isEmpty());
            req = request(20L); req.setClassName(" "); req.setStartDate(null);
            assertEquals(Set.of("className", "startDate"), validator.validate(req).stream()
                    .map(v -> v.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet()));
        }
    }

    private void assertInvalidPlanner() {
        var error = assertThrows(ServiceException.class, () -> service.createBatch(request(20L, 21L), 9L));
        assertEquals(DELIVERY_CLASS_USER_INVALID.getCode(), error.getCode());
        verify(mapper, never()).insert(any(DeliveryClassDO.class));
    }
    private int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM classes", Integer.class); }
    private static AdminUserRespDTO user(long id, long dept, int status) {
        return new AdminUserRespDTO().setId(id).setDeptId(dept).setStatus(status).setNickname("测试规划师" + id);
    }
    private static DeliveryClassBatchCreateReqVO request(Long... ids) {
        var req = new DeliveryClassBatchCreateReqVO();
        req.setClassName("  同名班级  "); req.setStartDate(LocalDate.of(2026, 12, 1)); req.setHomeroomUserIds(List.of(ids));
        return req;
    }
}
