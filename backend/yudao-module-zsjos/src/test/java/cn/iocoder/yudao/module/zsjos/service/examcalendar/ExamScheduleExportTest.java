package cn.iocoder.yudao.module.zsjos.service.examcalendar;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.ExamScheduleExportReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.ExamScheduleDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Expression;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExamScheduleExportTest {
    static JdbcTemplate jdbc;
    static ExamScheduleMapper mapper;
    static final LocalDateTime NOW = LocalDateTime.of(2026,10,14,12,0);

    @BeforeAll static void setup() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:exam_export;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.addMapper(ExamScheduleMapper.class);
        var plugin = new MybatisPlusInterceptor();
        plugin.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            public Expression getTenantId() { return new LongValue(TenantContextHolder.getRequiredTenantId()); }
        }));
        plugin.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config); factory.setPlugins(plugin);
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(ExamScheduleMapper.class);
        var columns = TableInfoHelper.getTableInfo(ExamScheduleDO.class).getFieldList().stream().map(field -> {
            Class<?> type = field.getPropertyType();
            String sqlType = type == LocalDate.class ? "DATE" : type == LocalDateTime.class ? "TIMESTAMP"
                    : type == Long.class ? "BIGINT" : type == Integer.class ? "INT" : type == Boolean.class ? "BOOLEAN" : "VARCHAR(4000)";
            return field.getColumn() + " " + sqlType;
        }).toList();
        jdbc.execute("CREATE TABLE zsjos_exam_schedule(id BIGINT PRIMARY KEY," + String.join(",",columns) + ")");
        // Ends on start, starts on end, spans whole interval, single-day boundaries, invisible/other-tenant rows.
        insert(1,"MULTI_DAY",null,"2026-09-01","2026-10-01","PUBLISHED",1,false,null,null);
        insert(2,"EXACT","2026-10-01",null,null,"PUBLISHED",1,false,null,null);
        insert(3,"MULTI_DAY",null,"2026-10-31","2026-11-02","PUBLISHED",1,false,null,null);
        insert(4,"MULTI_DAY",null,"2026-09-01","2026-11-02","PUBLISHED",1,false,null,null);
        insert(5,"EXACT","2026-10-31",null,null,"PUBLISHED",1,false,null,null);
        insert(6,"EXACT","2026-11-01",null,null,"PUBLISHED",1,false,null,null);
        insert(7,"MULTI_DAY",null,"2026-09-01","2026-09-30","PUBLISHED",1,false,null,null);
        insert(8,"EXACT","2026-10-10",null,null,"DRAFT",1,false,null,null);
        insert(9,"EXACT","2026-10-10",null,null,"PUBLISHED",2,false,null,null);
        insert(10,"EXACT","2026-10-10",null,null,"PUBLISHED",1,true,null,null);
        insert(11,"EXACT","2026-10-10",null,null,"REVOKED",1,false,NOW.minusMinutes(4),null);
        insert(12,"EXACT","2026-10-10",null,null,"REVOKED",1,false,NOW.minusMinutes(5),null);
        insert(13,"EXACT","2026-10-10",null,null,"REVOKED",1,false,NOW.minusMinutes(1),NOW);
    }
    static void insert(long id,String type,String exact,String start,String end,String status,long tenant,boolean deleted,LocalDateTime revoked,LocalDateTime claimed) {
        jdbc.update("INSERT INTO zsjos_exam_schedule(id,schedule_type,exact_date,start_date,end_date,record_status,tenant_id,deleted,revoked_at,reedit_claimed_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                id,type,exact,start,end,status,tenant,deleted,revoked,claimed);
    }
    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    static ExamScheduleExportReqVO request() {
        var req = new ExamScheduleExportReqVO(); req.setRangeStart(LocalDate.of(2026,10,1)); req.setRangeEnd(LocalDate.of(2026,10,31)); req.setPageSize(200); return req;
    }
    @Test void includesBothBoundariesAndOverlapWithTenantAndDeletionFilters() {
        var result = mapper.selectExportPage(request(),false,NOW);
        assertEquals(5,result.getTotal());
        assertEquals(List.of(1L,4L,2L,3L,5L),result.getList().stream().map(ExamScheduleDO::getId).toList());
        TenantContextHolder.setTenantId(2L);
        assertEquals(List.of(9L),mapper.selectExportPage(request(),false,NOW).getList().stream().map(ExamScheduleDO::getId).toList());
    }
    @Test void managerIncludesOnlyLiveDraftAndRevokeWindowAndHonorsStatus() {
        assertEquals(7,mapper.selectExportPage(request(),true,NOW).getTotal());
        var req=request(); req.setRecordStatus("REVOKED");
        assertEquals(List.of(11L),mapper.selectExportPage(req,true,NOW).getList().stream().map(ExamScheduleDO::getId).toList());
        assertEquals(0,mapper.selectExportPage(req,false,NOW).getTotal());
    }
    @Test void paginationCountsMatchAndEndBeforeStartDoesNotReachMapper() {
        var req=request(); req.setPageSize(2); req.setPageNo(3);
        var page=mapper.selectExportPage(req,false,NOW);
        assertEquals(5,page.getTotal()); assertEquals(List.of(5L),page.getList().stream().map(ExamScheduleDO::getId).toList());
        var service=new ExamScheduleService(); var permissions=mock(PermissionApi.class); var mockMapper=mock(ExamScheduleMapper.class);
        ReflectionTestUtils.setField(service,"permissionApi",permissions); ReflectionTestUtils.setField(service,"mapper",mockMapper);
        assertThrows(RuntimeException.class,()->service.exportPage(req,7L));
        when(permissions.hasAnyPermissions(7L,"zsjos:exam-calendar:query")).thenReturn(true);
        assertThrows(RuntimeException.class,()->service.exportPage(req,7L));
        when(permissions.hasAnyPermissions(7L,"zsjos:exam-calendar:export")).thenReturn(true);
        req.setRangeEnd(req.getRangeStart().minusDays(1));
        assertThrows(RuntimeException.class,()->service.exportPage(req,7L));
        verifyNoInteractions(mockMapper);
    }
}
