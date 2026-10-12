package cn.iocoder.yudao.module.zsjos.service.examcalendar;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import cn.iocoder.yudao.module.zsjos.service.calendar.CalendarNotificationSnapshotService;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Expression;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="EXAM_CREATE_TEST_DB", matches="exam_create_[a-zA-Z0-9_]+")
class ExamScheduleCreateMySqlTest {
    static JdbcTemplate jdbc;
    static ExamScheduleMapper mapper;
    static ExamScheduleService service;
    static ExamScheduleService target;
    static CalendarNotificationSnapshotService snapshots;
    static PermissionApi permissions;
    static final java.util.concurrent.atomic.AtomicLong TENANTS = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());
    long tenant;
    @BeforeAll static void setup() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/" + System.getenv("EXAM_CREATE_TEST_DB")
            + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai",
            "root", System.getenv("EXAM_CREATE_TEST_PASSWORD"));
        jdbc = new JdbcTemplate(ds);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        var global = new com.baomidou.mybatisplus.core.config.GlobalConfig()
            .setDbConfig(new com.baomidou.mybatisplus.core.config.GlobalConfig.DbConfig())
            .setMetaObjectHandler(new cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler());
        com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.setGlobalConfig(config, global);
        config.addMapper(ExamScheduleMapper.class);
        config.addMapper(cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarNotifySnapshotMapper.class);
        config.addMapper(cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarNotifyStateMapper.class);
        var plugin = new MybatisPlusInterceptor();
        plugin.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            public Expression getTenantId() { return new LongValue(TenantContextHolder.getRequiredTenantId()); }
        }));
        plugin.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config); factory.setPlugins(plugin);
        factory.setGlobalConfig(global);
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(ExamScheduleMapper.class);
        target = new ExamScheduleService();
        permissions = mock(PermissionApi.class);
        when(permissions.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        ReflectionTestUtils.setField(target, "mapper", mapper);
        ReflectionTestUtils.setField(target, "permissionApi", permissions);
        ReflectionTestUtils.setField(target, "configApi", mock(ConfigApi.class));
        var session = new SqlSessionTemplate(factory.getObject());
        snapshots = spy(new CalendarNotificationSnapshotService());
        ReflectionTestUtils.setField(snapshots, "snapshotMapper", session.getMapper(cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarNotifySnapshotMapper.class));
        ReflectionTestUtils.setField(snapshots, "stateMapper", session.getMapper(cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarNotifyStateMapper.class));
        ReflectionTestUtils.setField(target, "notificationSnapshots", snapshots);
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        service = (ExamScheduleService) proxy.getProxy();
    }
    @BeforeEach void tenant() {
        tenant = TENANTS.incrementAndGet(); TenantContextHolder.setTenantId(tenant);
        reset(snapshots);
        ReflectionTestUtils.setField(target, "clock", java.time.Clock.system(java.time.ZoneId.of("Asia/Shanghai")));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    ExamScheduleSaveReqVO request() {
        return new ExamScheduleSaveReqVO().setIdempotencyKey(java.util.UUID.randomUUID().toString())
            .setScheduleName("幂等考试").setScheduleType("EXACT").setExactDate(LocalDate.of(2099, 10, 10));
    }
    long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id=?", Long.class, tenant);
    }
    @Test void draftDeletionIsLogicalTenantScopedAndPreservesCreationReplay() {
        var req = request();
        long id = service.create(req, 20L);
        TenantContextHolder.setTenantId(tenant + 1);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.deleteDraft(id, 20L));
        TenantContextHolder.setTenantId(tenant);
        service.deleteDraft(id, 20L);
        assertNull(mapper.selectById(id));
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM zsjos_exam_schedule WHERE id=? AND tenant_id=?",
                Integer.class, id, tenant));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT record_status FROM zsjos_exam_schedule WHERE id=?",
                String.class, id));
        assertEquals(1, count("zsjos_calendar_notify_snapshot"));
        assertEquals(id, service.create(req, 20L));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.deleteDraft(id, 20L));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.publish(id, 20L));
    }

    @Test void deletionAndPublicationCannotBothSucceed() throws Exception {
        long id = service.create(request(), 20L);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (boolean deleting : new boolean[]{true, false}) futures.add(pool.submit(() -> {
                TenantContextHolder.setTenantId(tenant);
                try {
                    barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                    if (deleting) service.deleteDraft(id, 20L); else service.publish(id, 20L);
                    return true;
                } catch (cn.iocoder.yudao.framework.common.exception.ServiceException expected) {
                    assertTrue(expected.getCode() == 1_900_018_001 || expected.getCode() == 1_900_018_005);
                    return false;
                } finally { TenantContextHolder.clear(); }
            }));
            int successes = 0;
            for (var future : futures) if (future.get(30, java.util.concurrent.TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
            String state = jdbc.queryForObject("SELECT CONCAT(record_status, ':', deleted + 0) FROM zsjos_exam_schedule WHERE id=?",
                    String.class, id);
            assertTrue("DRAFT:1".equals(state) || "PUBLISHED:0".equals(state), state);
        } finally { pool.shutdownNow(); }
    }

    @Test void concurrentSameRequestCreatesOneRowAndOneSnapshot() throws Exception {
        var req = request();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        var barrier = new java.util.concurrent.CyclicBarrier(8);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Long>>();
            for (int i=0;i<8;i++) futures.add(pool.submit(() -> {
                TenantContextHolder.setTenantId(tenant);
                try { barrier.await(10, java.util.concurrent.TimeUnit.SECONDS); return service.create(req,20L); }
                finally { TenantContextHolder.clear(); }
            }));
            var ids = new java.util.HashSet<Long>();
            for (var future:futures) ids.add(future.get(30,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1,ids.size());
            assertEquals(1,count("zsjos_exam_schedule"));
            assertEquals(1,count("zsjos_calendar_notify_snapshot"));
            assertEquals(1,count("zsjos_calendar_notify_state"));
            verify(snapshots,times(1)).captureExam(any(),eq("CREATED"));
        } finally { pool.shutdownNow(); }
    }
    @Test void conflictNewKeysUserAndTenantIsolation() {
        var req=request(); long first=service.create(req,20L);
        req.setRemark("不同内容");
        var conflict=assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.create(req,20L));
        assertEquals(1_900_018_016,conflict.getCode());
        req.setRemark(null);
        when(permissions.hasAnyPermissions(21L,ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        assertNotEquals(first,service.create(req,21L));
        TenantContextHolder.setTenantId(tenant+1000000);
        assertNotEquals(first,service.create(req,20L));
        TenantContextHolder.setTenantId(tenant);
        req.setIdempotencyKey(java.util.UUID.randomUUID().toString());
        assertNotEquals(first,service.create(req,20L));
        assertEquals(3,count("zsjos_exam_schedule"));
    }
    @Test void replaySurvivesUpdatesDeletionAndDateExpiry() {
        var req=request(); long id=service.create(req,20L);
        jdbc.update("UPDATE zsjos_exam_schedule SET schedule_name='后续编辑',record_status='REVOKED',deleted=1 WHERE id=?",id);
        ReflectionTestUtils.setField(target,"clock",java.time.Clock.fixed(java.time.Instant.parse("2100-01-01T00:00:00Z"),java.time.ZoneId.of("Asia/Shanghai")));
        assertEquals(id,service.create(req,20L));
        assertEquals(1,count("zsjos_exam_schedule"));
        verify(snapshots,times(1)).captureExam(any(),eq("CREATED"));
    }
    @Test void snapshotFailureRollsBackCreationAndKeyThenAllowsRetry() {
        var req=request();
        doAnswer(invocation -> { invocation.callRealMethod(); throw new IllegalStateException("injected after snapshot"); })
            .when(snapshots).captureExam(any(),eq("CREATED"));
        assertThrows(IllegalStateException.class,()->service.create(req,20L));
        assertEquals(0,count("zsjos_exam_schedule"));
        assertEquals(0,count("zsjos_calendar_notify_snapshot"));
        assertEquals(0,count("zsjos_calendar_notify_state"));
        doCallRealMethod().when(snapshots).captureExam(any(),eq("CREATED"));
        long id=service.create(req,20L);
        assertEquals(id,service.create(req,20L));
        assertEquals(1,count("zsjos_exam_schedule"));
    }
    @Test void missingInvalidKeysAndDeniedUserNeverWrite() {
        for (String key:new String[]{null,"","short","bad-key-has-spaces in it","x".repeat(65)}) {
            var req=request().setIdempotencyKey(key);
            assertEquals(1_900_018_015,assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.create(req,20L)).getCode());
        }
        var req=request(); long id=service.create(req,20L);
        when(permissions.hasAnyPermissions(20L,ExamScheduleService.PERMISSION_MANAGE)).thenReturn(false);
        try { assertEquals(1_900_018_006,assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.create(req,20L)).getCode()); }
        finally { when(permissions.hasAnyPermissions(20L,ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true); }
        assertEquals(id,service.create(req,20L)); assertEquals(1,count("zsjos_exam_schedule"));
    }
}
