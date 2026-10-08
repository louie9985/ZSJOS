package cn.iocoder.yudao.module.zsjos.service.examcalendar;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.ExamCalendarNoteSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Expression;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="EXAM_NOTE_TEST_DB", matches="exam_note_[a-zA-Z0-9_]+")
class ExamCalendarNoteMySqlTest {
    static JdbcTemplate jdbc;
    static ExamCalendarNoteService service;
    static FileApi files;
    static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis());
    long tenant;
    @BeforeAll static void setup() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/" + System.getenv("EXAM_NOTE_TEST_DB")
            + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai", "root", System.getenv("EXAM_NOTE_TEST_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ExamCalendarNoteMapper.class); configuration.addMapper(ExamCalendarNoteImageMapper.class);
        var plugin = new MybatisPlusInterceptor();
        plugin.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            public Expression getTenantId() { return new LongValue(TenantContextHolder.getRequiredTenantId()); }
        }));
        configuration.addInterceptor(plugin);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(dataSource); factory.setConfiguration(configuration);
        var template = new SqlSessionTemplate(factory.getObject());
        var target = new ExamCalendarNoteService();
        ReflectionTestUtils.setField(target, "notes", template.getMapper(ExamCalendarNoteMapper.class));
        ReflectionTestUtils.setField(target, "images", template.getMapper(ExamCalendarNoteImageMapper.class));
        var permission = mock(PermissionApi.class); when(permission.hasAnyPermissions(eq(7L), any(String[].class))).thenReturn(true);
        var access = new ExamCalendarNotePermissionProvider(); ReflectionTestUtils.setField(access,"permissions",permission);
        ReflectionTestUtils.setField(target,"access",access);
        files = mock(FileApi.class); when(files.presignGetUrl(anyLong(),eq(3600))).thenReturn("https://example.com/image.png");
        ReflectionTestUtils.setField(target,"files",files);
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource),new AnnotationTransactionAttributeSource()));
        service = (ExamCalendarNoteService) proxy.getProxy();
    }
    @BeforeEach void tenant() { tenant=IDS.addAndGet(100); TenantContextHolder.setTenantId(tenant); }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    ExamCalendarNoteSaveReqVO req(String content,long version) { return new ExamCalendarNoteSaveReqVO().setContent(content).setVersion(version); }
    @Test void firstSaveRaceHasOneWinnerAndOneConflict() throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> save=()-> {
                TenantContextHolder.setTenantId(tenant); start.await();
                try { service.save(tenant,7L,req("并发说明",0)); return true; }
                catch(ServiceException e) { assertEquals(1900018021,e.getCode()); return false; }
                finally { TenantContextHolder.clear(); }
            };
            var a=pool.submit(save);var b=pool.submit(save);start.countDown();
            assertNotEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
        }
        assertEquals(1L,service.get(tenant,7L).getVersion());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM zsjos_exam_calendar_note WHERE tenant_id=?",Integer.class,tenant));
    }
    @Test void rejectedImageRollsBackInitialRowAndTenantReadsAreIsolated() {
        jdbc.update("INSERT INTO zsjos_exam_calendar_note_image(tenant_id,file_id,uploaded_by,bound) VALUES (?,?,7,1)",tenant+1,81);
        assertEquals(1900018023,assertThrows(ServiceException.class,()->service.save(tenant,7L,req("<img src='exam-note-image:81'>",0))).getCode());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM zsjos_exam_calendar_note WHERE tenant_id=?",Integer.class,tenant));
        assertEquals("",service.get(tenant,7L).getContent());
        assertThrows(ServiceException.class,()->service.get(tenant+1,7L));
        service.save(tenant,7L,req("本租户说明",0));
        TenantContextHolder.setTenantId(tenant+1);
        assertEquals("",service.get(tenant+1,7L).getContent());
    }
    @Test void bindsImagesAndClearsReferencesWithTheNote() {
        jdbc.update("INSERT INTO zsjos_exam_calendar_note_image(tenant_id,file_id,uploaded_by,bound) VALUES (?,?,7,0)",tenant,82);
        var result=service.save(tenant,7L,req("<p>共享图片</p><img src='exam-note-image:82'>",0));
        assertEquals(1L,result.getVersion());
        assertEquals(1,jdbc.queryForObject("SELECT bound+0 FROM zsjos_exam_calendar_note_image WHERE tenant_id=?",Integer.class,tenant));
        service.save(tenant,7L,req("",1));
        assertEquals(0,jdbc.queryForObject("SELECT bound+0 FROM zsjos_exam_calendar_note_image WHERE tenant_id=?",Integer.class,tenant));
        assertEquals(2L,service.get(tenant,7L).getVersion());
    }
    @Test void downstreamFailureRollsBackContentVersionAndBinding() {
        service.save(tenant,7L,req("原说明",0));
        jdbc.update("INSERT INTO zsjos_exam_calendar_note_image(tenant_id,file_id,uploaded_by,bound) VALUES (?,?,7,0)",tenant,83);
        when(files.presignGetUrl(83L,3600)).thenThrow(new IllegalStateException("fixture signing failure"));
        assertThrows(IllegalStateException.class,()->service.save(tenant,7L,req("<img src='exam-note-image:83'>",1)));
        assertEquals("原说明",service.get(tenant,7L).getContent());
        assertEquals(1L,service.get(tenant,7L).getVersion());
        assertEquals(0,jdbc.queryForObject("SELECT bound+0 FROM zsjos_exam_calendar_note_image WHERE tenant_id=?",Integer.class,tenant));
    }
}
