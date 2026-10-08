package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.personalcalendar.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.coursecalendar.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.calendar.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personalcalendar.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.coursecalendar.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personalcalendar.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.coursecalendar.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.*;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.*;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Expression;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CALENDAR_SEARCH_TEST_DB", matches = "calendar_search_[a-zA-Z0-9_]+")
class CalendarSearchMySqlTest {
    static org.apache.ibatis.session.SqlSessionFactory factory;
    static JdbcTemplate jdbc;
    @BeforeAll static void setup() {
        var source = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/" + System.getenv("CALENDAR_SEARCH_TEST_DB")
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai", "root", System.getenv("CALENDAR_SEARCH_TEST_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        var plugins = new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            public Expression getTenantId() { return new LongValue(TenantContextHolder.getRequiredTenantId()); }
        }));
        plugins.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        config.addInterceptor(plugins);
        config.addMapper(PersonalCalendarEventMapper.class); config.addMapper(CourseCalendarEventMapper.class);
        config.addMapper(ExamScheduleMapper.class); config.addMapper(MediaAccountMapper.class); config.addMapper(LeadCalendarMapper.class);
        factory = new MybatisSqlSessionFactoryBuilder().build(config);
        for (var type : List.of(PersonalCalendarEventDO.class, CourseCalendarEventDO.class, ExamScheduleDO.class, MediaAccountDO.class)) {
            var info = TableInfoHelper.getTableInfo(type);
            var ddl = new StringBuilder("CREATE TABLE ").append(info.getTableName()).append("(id BIGINT PRIMARY KEY");
            for (var field : info.getFieldList()) ddl.append(", ").append(field.getColumn()).append(' ')
                    .append(field.getPropertyType() == String.class ? "TEXT"
                        : field.getPropertyType() == LocalDateTime.class ? "DATETIME(6)"
                        : field.getPropertyType() == LocalDate.class ? "DATE"
                        : field.getPropertyType() == java.math.BigDecimal.class ? "DECIMAL(20,4)" : "BIGINT");
            jdbc.execute(ddl.append(") CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci").toString());
        }
        jdbc.execute("CREATE TABLE zsjos_lead(id BIGINT PRIMARY KEY, tenant_id BIGINT, deleted INT, owner_user_id BIGINT, lead_category VARCHAR(50), lead_no VARCHAR(100), submitted_name VARCHAR(100), submitted_mobile VARCHAR(100), submitted_wechat_id VARCHAR(100)) CHARACTER SET utf8mb4");
        jdbc.execute("CREATE TABLE zsjos_business_task(id BIGINT PRIMARY KEY, tenant_id BIGINT, deleted INT, biz_id BIGINT, assignee_type VARCHAR(50), assignee_id BIGINT, biz_type VARCHAR(50), status VARCHAR(50), task_type VARCHAR(100), due_at DATETIME) CHARACTER SET utf8mb4");
    }
    @BeforeEach void tenant() { TenantContextHolder.setTenantId(901L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void personalAndCourseFilterLiteralTextRangeOwnerTenantAndPageInDatabase() {
        var today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        for (int id = 1; id <= 6; id++) {
            var date = today.plusDays(id == 1 ? -3 : id == 2 ? 1 : 0);
            jdbc.update("INSERT INTO zsjos_personal_calendar_event(id,tenant_id,deleted,owner_user_id,title,description,start_time,end_time) VALUES(?,?,?,?,?,?,?,?)",
                    id, id == 4 ? 902 : 901, id == 5 ? 1 : 0, id == 3 ? 8 : 7, "标题", id == 6 ? "100X备注" : "100%_备注", date.atTime(9,0), date.atTime(10,0));
        }
        try (var session = factory.openSession()) {
            var mapper = session.getMapper(PersonalCalendarEventMapper.class);
            var req = new PersonalCalendarSearchReqVO(); req.setKeyword(" 100%_ "); req.setPageSize(1);
            var first = mapper.selectSearch(req, 7L);
            assertEquals(2, first.getTotal()); assertEquals(2L, first.getList().getFirst().getId());
            req.setPageNo(2); assertEquals(1L, mapper.selectSearch(req, 7L).getList().getFirst().getId());
            req.setPageNo(1); req.setRangeStart(today); req.setRangeEnd(today.plusDays(1));
            assertEquals(1, mapper.selectSearch(req, 7L).getTotal());
            assertEquals(2, mapper.selectSearch(req, null).getTotal());
            TenantContextHolder.setTenantId(902L); assertEquals(1, mapper.selectSearch(req, 7L).getTotal());
            TenantContextHolder.setTenantId(901L);
        }
        jdbc.update("INSERT INTO zsjos_course_calendar_event(id,tenant_id,deleted,course_name,remark,start_time,end_time) VALUES (1,901,0,'课程','跨年备注','2026-12-31 09:00:00','2027-01-02 00:00:00'),(2,901,0,'课程','零时点','2027-01-02 00:00:00','2027-01-02 00:00:00'),(3,902,0,'课程','跨年备注','2026-12-31 09:00:00','2027-01-02 00:00:00')");
        try (var session = factory.openSession()) {
            var mapper = session.getMapper(CourseCalendarEventMapper.class);
            var req = new CourseCalendarSearchReqVO(); req.setKeyword("课程"); req.setSort("asc");
            req.setRangeStart(LocalDate.of(2027,1,1)); req.setRangeEnd(LocalDate.of(2027,1,1));
            assertEquals(List.of(1L), mapper.selectSearch(req).getList().stream().map(CourseCalendarEventDO::getId).toList());
            req.setRangeStart(LocalDate.of(2027,1,2)); req.setRangeEnd(LocalDate.of(2027,1,2));
            assertEquals(List.of(2L), mapper.selectSearch(req).getList().stream().map(CourseCalendarEventDO::getId).toList());
            req.setRangeStart(null); req.setRangeEnd(null); req.setKeyword("跨年备注"); assertEquals(1,mapper.selectSearch(req).getTotal());
        }
        assertEquals("E6A087E9A298", jdbc.queryForObject("SELECT HEX(title) FROM zsjos_personal_calendar_event WHERE id=1", String.class));
    }

    @Test void examCombinesTypesBeforePagingAndHonorsRevocationAndStatusScope() {
        var now = LocalDateTime.of(2026,10,8,9,0);
        for (int id=1;id<=7;id++) jdbc.update("INSERT INTO zsjos_exam_schedule(id,tenant_id,deleted,schedule_name,remark,schedule_type,exact_date,start_date,end_date,record_status,revoked_at,reedit_claimed_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                id, id == 7 ? 902 : 901, 0, "考试"+id, "统一备注", id==2 ? "MULTI_DAY" : "EXACT", id==2 ? null : LocalDate.of(2026,10,8),
                id==2 ? LocalDate.of(2026,10,1) : null, id==2 ? LocalDate.of(2026,10,10) : null,
                id<=2 || id==7 ? "PUBLISHED" : id==3 ? "DRAFT" : "REVOKED",
                id==5 ? now.minusMinutes(6) : now.minusMinutes(1), id==6 ? now : null);
        try(var session=factory.openSession()) {
            var mapper=session.getMapper(ExamScheduleMapper.class);
            var req=new ExamCalendarSearchReqVO();req.setKeyword("统一备注");req.setPageSize(1);
            var first=mapper.selectSearch(req,false,now);assertEquals(2,first.getTotal());assertEquals(2L,first.getList().getFirst().getId());
            req.setPageNo(2);assertEquals(1L,mapper.selectSearch(req,false,now).getList().getFirst().getId());
            req.setPageNo(1);assertEquals(4,mapper.selectSearch(req,true,now).getTotal());
            req.setRecordStatus("DRAFT");assertEquals(0,mapper.selectSearch(req,false,now).getTotal());assertEquals(1,mapper.selectSearch(req,true,now).getTotal());
            req.setRecordStatus(null);req.setRangeStart(LocalDate.of(2026,10,9));req.setRangeEnd(LocalDate.of(2026,10,9));
            assertEquals(1,mapper.selectSearch(req,true,now).getTotal());
        }
    }

    @Test void examLegacyDisplayedNameUsesFrozenCategoryAndSpecLabels() {
        jdbc.update("INSERT INTO zsjos_exam_schedule(id,tenant_id,deleted,schedule_type,exact_date,record_status,category_name_snapshot,selected_specs_json) VALUES(81,901,0,'EXACT','2026-10-08','PUBLISHED',?,?)",
                "历史分类", "[{\"attrName\":\"规格\",\"label\":\"高级\",\"labelMissing\":true}]");
        try(var session=factory.openSession()) {
            var req=new ExamCalendarSearchReqVO();req.setKeyword("历史分类，规格：高级（历史标签缺失）");
            assertEquals(1,session.getMapper(ExamScheduleMapper.class).selectSearch(req,false,LocalDateTime.of(2026,10,8,9,0)).getTotal());
            TenantContextHolder.setTenantId(902L);
            assertEquals(0,session.getMapper(ExamScheduleMapper.class).selectSearch(req,false,LocalDateTime.of(2026,10,8,9,0)).getTotal());
        }
    }

    @Test void mediaScopeAndFiltersApplyToCountsAndPagedResults() {
        for(int id=1;id<=6;id++) jdbc.update("INSERT INTO zsjos_media_account(id,tenant_id,deleted,account_no,nickname,director_user_id,owner_operator_user_id,current_status_value,s_stage,maintenance_start_date,maintenance_end_date) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                id,id==4?902:901,id==5?1:0,"ACC-"+id,"昵称%_",id==2?8:7,id==2?7:8,"active","stage",
                id==6?null:LocalDate.of(2026,10,1),id==6?null:LocalDate.of(2026,12,1));
        try(var session=factory.openSession()) {
            var mapper=session.getMapper(MediaAccountMapper.class);var req=new MediaCalendarSearchReqVO();
            req.setKeyword("昵称%_");req.setSort("desc");req.setPageSize(1);
            assertEquals(3,mapper.selectCalendarSearch(req,List.of(7L),false).getTotal());
            assertEquals(0,mapper.selectCalendarSearch(req,List.of(),false).getTotal());
            assertEquals(3,mapper.selectCalendarSearch(req,List.of(),true).getTotal());
            req.setDirectorUserId(8L);assertEquals(1,mapper.selectCalendarSearch(req,List.of(7L),false).getTotal());
            req.setStageValue("other");assertEquals(0,mapper.selectCalendarSearch(req,List.of(7L),false).getTotal());
            req.setStageValue(null);req.setKeyword("ACC-2");assertEquals(1,mapper.selectCalendarSearch(req,List.of(7L),false).getTotal());
        }
    }

    @Test void leadAggregatesEarliestBeforeDateFilterAndExcludesTransferredCompletedForeignTasks() {
        for(int id=1;id<=7;id++) {
            jdbc.update("INSERT INTO zsjos_lead VALUES(?,?,?,?,?,?,?,?,?)",id,id==4?902:901,0,id==3?8:7,"A","L-"+id,"姓名"+id,"13000000000","微信%_");
            jdbc.update("INSERT INTO zsjos_business_task VALUES(?,?,?,?,?,?,?,?,?,?)",id,id==4?902:901,0,id,"user",id==5?8:7,"lead",id==6?"completed":"pending",id==7?"other":"lead_first_follow_up",LocalDateTime.of(2026,10,1,9,0));
        }
        jdbc.update("INSERT INTO zsjos_business_task VALUES(20,901,0,1,'user',7,'lead','pending','lead_follow_up_reminder','2026-11-01 09:00:00')");
        try(var session=factory.openSession()) {
            var mapper=session.getMapper(LeadCalendarMapper.class);var req=new LeadCalendarSearchReqVO();req.setKeyword("微信%_");req.setSort("asc");req.setPageSize(1);
            assertEquals(2,mapper.searchCount(req,901L,7L));assertEquals(2L,mapper.searchPage(req,901L,7L,1).getFirst().getId());
            req.setRangeStart(LocalDate.of(2026,11,1)); assertEquals(0,mapper.searchCount(req,901L,7L));
            req.setRangeStart(null);req.setKeyword("L-1");assertEquals(1,mapper.searchCount(req,901L,7L));
            req.setKeyword("姓名2");assertEquals(1,mapper.searchCount(req,901L,7L));
            req.setKeyword("13000000000");assertEquals(2,mapper.searchCount(req,901L,7L));
        }
    }
}
