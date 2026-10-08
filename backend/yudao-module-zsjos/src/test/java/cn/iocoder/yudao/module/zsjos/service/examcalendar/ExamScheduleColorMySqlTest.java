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

@EnabledIfEnvironmentVariable(named="EXAM_COLOR_TEST_DB", matches="exam_color_[a-zA-Z0-9_]+")
class ExamScheduleColorMySqlTest {
    static JdbcTemplate jdbc;
    static ExamScheduleMapper mapper;
    static ExamScheduleService service;
    @BeforeAll static void setup() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/" + System.getenv("EXAM_COLOR_TEST_DB")
            + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai",
            "root", System.getenv("EXAM_COLOR_TEST_PASSWORD"));
        jdbc = new JdbcTemplate(ds);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        var global = new com.baomidou.mybatisplus.core.config.GlobalConfig()
            .setDbConfig(new com.baomidou.mybatisplus.core.config.GlobalConfig.DbConfig())
            .setMetaObjectHandler(new cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler());
        com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.setGlobalConfig(config, global);
        config.addMapper(ExamScheduleMapper.class);
        var plugin = new MybatisPlusInterceptor();
        plugin.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            public Expression getTenantId() { return new LongValue(TenantContextHolder.getRequiredTenantId()); }
        }));
        plugin.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config); factory.setPlugins(plugin);
        factory.setGlobalConfig(global);
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(ExamScheduleMapper.class);
        var target = new ExamScheduleService();
        var permissions = mock(PermissionApi.class);
        when(permissions.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        ReflectionTestUtils.setField(target, "mapper", mapper);
        ReflectionTestUtils.setField(target, "permissionApi", permissions);
        ReflectionTestUtils.setField(target, "configApi", mock(ConfigApi.class));
        ReflectionTestUtils.setField(target, "notificationSnapshots", mock(CalendarNotificationSnapshotService.class));
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        service = (ExamScheduleService) proxy.getProxy();
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void realPersistenceRoundtripClearLegacyClientReeditAndTenantIsolation() {
        long tenant = System.currentTimeMillis(); TenantContextHolder.setTenantId(tenant);
        for (String type : new String[]{"EXACT", "MULTI_DAY"}) {
            var req = new ExamScheduleSaveReqVO().setScheduleName("底色持久化考试").setScheduleType(type).setBackgroundColor("#aabbcc");
            if (type.equals("EXACT")) req.setExactDate(LocalDate.of(2099,10,10));
            else req.setStartDate(LocalDate.of(2099,10,10)).setEndDate(LocalDate.of(2099,10,20));
            long id = service.create(req, 20L);
            assertEquals("#AABBCC", mapper.selectById(id).getBackgroundColor());
            assertEquals("底色持久化考试".getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                jdbc.queryForObject("SELECT OCTET_LENGTH(schedule_name) FROM zsjos_exam_schedule WHERE id=?", Integer.class, id));
            service.update(id, req.setBackgroundColor(null), 20L);
            assertEquals("#AABBCC", mapper.selectById(id).getBackgroundColor());
            service.update(id, req.setBackgroundColor(""), 20L);
            assertNull(mapper.selectById(id).getBackgroundColor());
            service.update(id, req.setBackgroundColor("#001133"), 20L);
            assertEquals(1, mapper.selectById(id).getCalendarVersion());
            var page = new ExamSchedulePageReqVO(); page.setPageNo(1); page.setPageSize(10);
            var result = type.equals("EXACT") ? service.exactPage(page,20L) : service.multiDayPage(page,20L);
            assertEquals("#001133", result.getList().getFirst().getBackgroundColor());
            TenantContextHolder.setTenantId(tenant + 1); assertNull(mapper.selectById(id)); TenantContextHolder.setTenantId(tenant);
            service.publish(id,20L); service.revoke(id,20L);
            var recovered = service.reedit(id,"color-reedit-" + id,20L);
            assertEquals("#001133", recovered.getBackgroundColor());
            assertEquals("#001133", service.reedit(id,"color-reedit-" + id,20L).getBackgroundColor());
        }
    }
}
