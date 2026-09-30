package cn.iocoder.yudao.module.zsjos.service.examcalendar;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.ExamSchedulePageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Expression;
import org.apache.ibatis.session.SqlSessionFactory;
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
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicitly opt in with an isolated retained schema. Never points at the development database. */
@EnabledIfEnvironmentVariable(named="EXAM_REEDIT_TEST_DB", matches="exam_reedit_[a-zA-Z0-9_]+")
class ExamScheduleReeditMySqlTest {
    static JdbcTemplate jdbc;
    static ExamScheduleMapper mapper;
    static ExamScheduleService service;
    static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 30, 10, 0);
    static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis());
    long tenantId;
    @BeforeAll static void setup() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/" + System.getenv("EXAM_REEDIT_TEST_DB")
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai",
                "root", System.getenv("EXAM_REEDIT_TEST_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ExamScheduleMapper.class);
        var plugin = new MybatisPlusInterceptor();
        plugin.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            public Expression getTenantId() { return new LongValue(TenantContextHolder.getRequiredTenantId()); }
        }));
        plugin.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(dataSource);
        factory.setConfiguration(configuration); factory.setPlugins(plugin);
        SqlSessionFactory sessions = factory.getObject();
        mapper = new SqlSessionTemplate(sessions).getMapper(ExamScheduleMapper.class);
        var target = new ExamScheduleService();
        var permissions = mock(PermissionApi.class);
        when(permissions.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        ReflectionTestUtils.setField(target, "mapper", mapper);
        ReflectionTestUtils.setField(target, "permissionApi", permissions);
        ReflectionTestUtils.setField(target, "clock", Clock.fixed(NOW.atZone(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai")));
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
        service = (ExamScheduleService) proxy.getProxy();
    }
    @BeforeEach void tenant() {
        tenantId = IDS.addAndGet(100);
        TenantContextHolder.setTenantId(tenantId);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    long insert(String type, String status, LocalDateTime revoked, long tenant) {
        long id = IDS.incrementAndGet();
        jdbc.update("INSERT INTO zsjos_exam_schedule(id,tenant_id,schedule_type,schedule_name,record_status,exact_date,start_date,end_date,revoked_at,revoked_by) VALUES(?,?,?,?,?,?,?,?,?,?)",
            id, tenant, type, "原考试", status, type.equals("EXACT") ? "2090-10-01" : null,
            type.equals("MULTI_DAY") ? "2090-10-01" : null, type.equals("MULTI_DAY") ? "2090-10-05" : null, revoked, 20L);
        return id;
    }
    @Test void realMapperFiltersBeforePaginationAndCounts() {
        for (String type : new String[]{"EXACT", "MULTI_DAY"}) {
            long valid = insert(type, "REVOKED", NOW.minusSeconds(299), tenantId);
            insert(type, "REVOKED", NOW.minusMinutes(5), tenantId);
            insert(type, "REVOKED", null, tenantId);
            insert(type, "REVOKED", NOW, tenantId + 1);
            var req = new ExamSchedulePageReqVO(); req.setPageNo(1); req.setPageSize(1);
            req.setRangeStart(LocalDate.of(2090,10,1)); req.setRangeEnd(LocalDate.of(2090,10,5));
            if (type.equals("EXACT")) {
                assertEquals(valid, mapper.selectExactList(req, true, NOW).getFirst().getId());
                assertEquals(1, mapper.selectExactList(req, true, NOW).size());
                assertTrue(mapper.selectExactList(req, false, NOW).isEmpty());
            } else {
                var page = mapper.selectMultiDayPage(req, true, NOW);
                assertEquals(1, page.getTotal()); assertEquals(valid, page.getList().getFirst().getId());
                assertEquals(0, mapper.selectMultiDayPage(req, false, NOW).getTotal());
            }
        }
    }
    @Test void concurrentClaimIsAtomicAndReplaysAfterSoftDelete() throws Exception {
        long id = insert("EXACT", "REVOKED", NOW.minusSeconds(299), tenantId);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<String> claim = () -> {
                TenantContextHolder.setTenantId(tenantId); start.await();
                String key = "request-" + Thread.currentThread().threadId() + "-000000000000";
                try { service.reedit(id, key, 20L); return key; }
                catch (ServiceException e) { assertEquals(1_900_018_012, e.getCode()); return null; }
                finally { TenantContextHolder.clear(); }
            };
            var a = pool.submit(claim); var b = pool.submit(claim); start.countDown();
            String first = a.get(15, TimeUnit.SECONDS), second = b.get(15, TimeUnit.SECONDS);
            assertTrue((first == null) != (second == null));
            String key = first != null ? first : second;
            assertNull(mapper.selectById(id));
            assertEquals("原考试", service.reedit(id, key, 20L).getScheduleName());
            assertEquals("REVOKED", jdbc.queryForObject("SELECT record_status FROM zsjos_exam_schedule WHERE id=?", String.class, id));
            TenantContextHolder.setTenantId(tenantId + 1);
            assertNull(mapper.selectReeditRecord(id, tenantId));
        }
    }
    @Test void exactExpiryDoesNotMutateTheRecord() {
        long id = insert("EXACT", "REVOKED", NOW.minusMinutes(5), tenantId);
        assertEquals(1_900_018_011, assertThrows(ServiceException.class, () -> service.reedit(id, "request-expired-01", 20L)).getCode());
        assertNotNull(mapper.selectById(id));
        assertNull(mapper.selectById(id).getReeditClaimedAt());
    }

    @Test void contentProjectionFailureRollsBackTheClaim() {
        long id = insert("EXACT", "REVOKED", NOW.minusSeconds(299), tenantId);
        jdbc.update("UPDATE zsjos_exam_schedule SET schedule_name=NULL, selected_specs_json='{}' WHERE id=?", id);
        assertThrows(RuntimeException.class, () -> service.reedit(id, "request-rollback-001", 20L));
        assertNotNull(mapper.selectById(id));
        assertNull(mapper.selectById(id).getReeditClaimedAt());
        assertNull(mapper.selectById(id).getReeditOperationKey());
    }
}
