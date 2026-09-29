package cn.iocoder.yudao.module.zsjos.service.cashback;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback.CashbackDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.cashback.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadObjectPermissionService;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderObjectPermissionService;
import com.baomidou.mybatisplus.core.*;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.annotation.DbType;
import net.sf.jsqlparser.expression.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Uses connection-local TEMPORARY tables, never changes persistent development data. */
@EnabledIfEnvironmentVariable(named="CASHBACK_TEST_MYSQL_URL", matches=".+")
class CashbackSearchMySqlTest {
    @Test void studentRelationshipBatchRetainsTenantStatusAndAcceptanceChecks() throws Exception {
        try (var connection=DriverManager.getConnection(System.getenv("CASHBACK_TEST_MYSQL_URL"),
                System.getenv("CASHBACK_TEST_MYSQL_USER"),System.getenv("CASHBACK_TEST_MYSQL_PASSWORD"))) {
            var ds=new SingleConnectionDataSource(connection,true);var jdbc=new JdbcTemplate(ds);
            jdbc.execute("CREATE TEMPORARY TABLE zsjos_order(id BIGINT,tenant_id BIGINT,deleted INT,lead_id BIGINT)");
            jdbc.execute("CREATE TEMPORARY TABLE zsjos_service_relation(id BIGINT,order_id BIGINT,person_id BIGINT,tenant_id BIGINT,deleted INT,status VARCHAR(20),acceptance_status VARCHAR(20),owner_user_id BIGINT,content_director_user_id BIGINT,career_planner_user_id BIGINT)");
            jdbc.execute("CREATE TEMPORARY TABLE zsjos_media_account(student_person_id BIGINT,tenant_id BIGINT,deleted INT,director_user_id BIGINT,owner_operator_user_id BIGINT)");
            for(int i=1;i<=9;i++) {
                jdbc.update("INSERT INTO zsjos_order VALUES(?,1,0,?)",i,i);
                jdbc.update("INSERT INTO zsjos_service_relation VALUES(?,?,?,1,0,'active','accepted',NULL,NULL,NULL)",i,i,i);
            }
            jdbc.update("UPDATE zsjos_service_relation SET owner_user_id=7 WHERE id=1");
            jdbc.update("UPDATE zsjos_service_relation SET content_director_user_id=7 WHERE id=2");
            jdbc.update("UPDATE zsjos_service_relation SET career_planner_user_id=7 WHERE id=3");
            for(int i=4;i<=9;i++) jdbc.update("INSERT INTO zsjos_media_account VALUES(?,1,0,7,NULL)",i);
            jdbc.update("UPDATE zsjos_service_relation SET acceptance_status='pending' WHERE id=5");
            jdbc.update("UPDATE zsjos_service_relation SET status='paused' WHERE id=6");
            jdbc.update("UPDATE zsjos_media_account SET tenant_id=2 WHERE student_person_id=7");
            jdbc.update("UPDATE zsjos_order SET deleted=1 WHERE id=8");
            jdbc.update("UPDATE zsjos_service_relation SET deleted=1 WHERE id=9");
            var config=new MybatisConfiguration();config.setEnvironment(new Environment("relationships",new JdbcTransactionFactory(),ds));
            config.addMapper(LeadReadBatchMapper.class);var factory=new MybatisSqlSessionFactoryBuilder().build(config);
            try(var session=factory.openSession()) {
                var mapper=session.getMapper(LeadReadBatchMapper.class);
                var ids=java.util.stream.LongStream.rangeClosed(1,9).boxed().toList();
                assertEquals(Set.of(1L,2L,3L,4L),mapper.selectStudentReadableLeadIds(ids,7L,1L));
                assertTrue(mapper.selectStudentReadableLeadIds(ids,7L,2L).isEmpty());
                assertTrue(mapper.selectStudentReadableLeadIds(ids,8L,1L).isEmpty());
            }
        }
    }
    @Test void realMysqlSearchPreservesResultsAndBoundsCandidateReads() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("CASHBACK_TEST_MYSQL_URL"),
                System.getenv("CASHBACK_TEST_MYSQL_USER"), System.getenv("CASHBACK_TEST_MYSQL_PASSWORD"))) {
            var ds = new SingleConnectionDataSource(connection, true);
            var jdbc = new JdbcTemplate(ds);
            var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
            config.setEnvironment(new Environment("cashback", new JdbcTransactionFactory(), ds));
            var plugins = new MybatisPlusInterceptor();
            plugins.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
                public Expression getTenantId() { return new LongValue(1); }
            }));
            plugins.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL)); config.addInterceptor(plugins);
            for (var type : List.of(CashbackMapper.class, CashbackSearchMapper.class, LeadMapper.class, PartnerMapper.class, SalesOrderMapper.class)) config.addMapper(type);
            var factory = new MybatisSqlSessionFactoryBuilder().build(config);
            for (var type : List.of(CashbackDO.class, LeadDO.class, PartnerDO.class, SalesOrderDO.class)) {
                var info = TableInfoHelper.getTableInfo(type);
                var ddl = new StringBuilder("CREATE TEMPORARY TABLE ").append(info.getTableName()).append(" (id BIGINT PRIMARY KEY");
                for (var field : info.getFieldList()) {
                    var t = field.getPropertyType();
                    String sql = t == String.class ? "TEXT" : t == LocalDateTime.class ? "DATETIME" : t == LocalDate.class ? "DATE"
                            : t == java.math.BigDecimal.class ? "DECIMAL(18,4)" : "BIGINT";
                    ddl.append(", `").append(field.getColumn()).append("` ").append(sql);
                }
                jdbc.execute(ddl.append(") CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci").toString());
            }
            int size = 600;
            for (int i=1; i<=size; i++) {
                String name = i == 1 ? "唯一目标" : i == 2 ? "A%_!\\汉" : i == 3 ? "İstanbul" : "共同姓名";
                jdbc.update("INSERT INTO zsjos_partner(id,tenant_id,deleted,name) VALUES(?,1,0,?)",i,name);
                jdbc.update("INSERT INTO zsjos_lead(id,tenant_id,deleted,lead_no,submitted_name,owner_user_id) VALUES(?,1,0,?,?,7)",i,"LEAD-"+i,name);
                jdbc.update("INSERT INTO zsjos_order(id,tenant_id,deleted,lead_id,student_name,submitter_user_id) VALUES(?,1,0,?,?,7)",i,i,name);
                jdbc.update("INSERT INTO zsjos_cashback(id,tenant_id,deleted,cashback_no,type,status,lead_id,order_id,partner_id,beneficiary_user_id,generated_at,amount) VALUES(?,1,0,?,'valid','available',?,?,?,?,?,10)",i,"CB-"+i,i,i,i,7,Timestamp.valueOf("2026-09-01 00:00:00"));
            }
            jdbc.update("UPDATE zsjos_cashback SET partner_id=NULL WHERE id IN(4,5)");
            jdbc.update("UPDATE zsjos_cashback SET tenant_id=2 WHERE id=6");
            jdbc.update("UPDATE zsjos_cashback SET deleted=1 WHERE id=7");
            jdbc.update("UPDATE zsjos_cashback SET status='blocked' WHERE id=8");
            jdbc.update("UPDATE zsjos_cashback SET beneficiary_user_id=9 WHERE id=9");
            var users = mock(AdminUserApi.class); var user = new AdminUserRespDTO(); user.setId(7L); user.setNickname("旧受益人");
            when(users.getUserListByNickname(anyString())).thenReturn(List.of(user));
            when(users.getUserMap(anyCollection())).thenReturn(Map.of(7L,user));
            when(users.getUserList(anyCollection())).thenReturn(List.of(user));
            var leadAccess = mock(LeadObjectPermissionService.class); var orderAccess = mock(SalesOrderObjectPermissionService.class);
            when(leadAccess.filterUnmaskedIdentity(anyCollection(), eq(7L))).thenAnswer(a -> ((Collection<LeadDO>)a.getArgument(0)).stream().filter(l -> l.getId()!=5).map(LeadDO::getId).collect(Collectors.toSet()));
            when(leadAccess.filterReadableDetails(anyCollection(), eq(7L))).thenAnswer(a -> ((Collection<LeadDO>)a.getArgument(0)).stream().filter(l -> l.getId()!=5).map(LeadDO::getId).collect(Collectors.toSet()));
            when(orderAccess.filterFinanceReadable(anyCollection(), eq(7L))).thenAnswer(a -> ((Collection<SalesOrderDO>)a.getArgument(0)).stream().filter(o -> o.getId()!=5).map(SalesOrderDO::getId).collect(Collectors.toSet()));
            var trace = spy(new FinanceTraceService());
            doReturn(true).when(trace).canQuerySource(anyString());
            // Legacy matching remains the result oracle, including Java name normalization.
            doAnswer(a -> java.util.stream.LongStream.rangeClosed(1,size).filter(x -> x!=5).boxed().collect(Collectors.toSet())).when(trace).visibleSourceIds(anyString());
            try(var session=factory.openSession(); var security=mockStatic(SecurityFrameworkUtils.class)) {
                TenantContextHolder.setTenantId(1L); security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
                var search = new CashbackSearchService(); var mapper = spy(session.getMapper(CashbackSearchMapper.class));
                var legacy = session.getMapper(CashbackMapper.class);
                var filters = new AdvancedFilterService(); ReflectionTestUtils.setField(filters,"financeTrace",trace);
                for (var target : List.of(search,trace)) {
                    ReflectionTestUtils.setField(target,"leads",session.getMapper(LeadMapper.class));
                    ReflectionTestUtils.setField(target,"partners",session.getMapper(PartnerMapper.class));
                    ReflectionTestUtils.setField(target,"orders",session.getMapper(SalesOrderMapper.class));
                    ReflectionTestUtils.setField(target,"users",users);
                }
                ReflectionTestUtils.setField(trace,"cashbacks",legacy);
                ReflectionTestUtils.setField(search,"mapper",mapper); ReflectionTestUtils.setField(search,"filters",filters);
                ReflectionTestUtils.setField(search,"trace",trace); ReflectionTestUtils.setField(search,"leadAccess",leadAccess);
                ReflectionTestUtils.setField(search,"orderAccess",orderAccess);
                var request = new CashbackPageReqVO(); request.setStatus("available"); request.setType("valid"); request.setPageSize(10);
                for (String term : List.of("唯一目标","CB-1","LEAD-2","共同","旧受益人","%_!\\","İ","i","不存在","  ","姓名")) {
                    request.setKeyword(term);
                    for(int page : List.of(1,2,60)) {
                        request.setPageNo(page);
                        var expected=legacy.selectCashbackPage(request,7L,null,trace.matchCashbackNameIds(term.trim()));
                        var actual=search.search(request,7L);
                        assertEquals(expected.getTotal(),actual.getTotal(),"total keyword="+term);
                        assertEquals(expected.getList().stream().map(CashbackDO::getId).toList(),actual.getList().stream().map(CashbackDO::getId).toList(),"page keyword="+term);
                    }
                }
                request.setKeyword("唯一目标"); request.setPageNo(1); clearInvocations(mapper,leadAccess,orderAccess);
                var result=search.search(request,7L); assertEquals(1,result.getTotal());
                verify(mapper,times(1)).candidates(any(),eq(0L),eq(256));
                verify(leadAccess).filterUnmaskedIdentity(argThat(c -> c.size()==1),eq(7L));
                verify(orderAccess).filterFinanceReadable(argThat(c -> c.size()==1),eq(7L));
                request.setKeyword("共同"); assertTrue(search.search(request,7L).getTotal()>512);
                request.setKeyword("唯一目标");
                long[] before=new long[20], after=new long[20];
                for(int run=0;run<23;run++) {
                    session.clearCache();
                    long started=System.nanoTime();
                    legacy.selectCashbackPage(request,7L,null,trace.matchCashbackNameIds(request.getKeyword()));
                    long middle=System.nanoTime();session.clearCache(); search.search(request,7L);long ended=System.nanoTime();
                    if(run>=3) {before[run-3]=middle-started;after[run-3]=ended-middle;}
                }
                Arrays.sort(before);Arrays.sort(after);
                System.out.printf(Locale.ROOT,"Cashback fixture (600 rows, read-only queries, mocked permission APIs): old P50=%.2fms P95=%.2fms; new P50=%.2fms P95=%.2fms%n",before[9]/1e6,before[18]/1e6,after[9]/1e6,after[18]/1e6);
                // Advanced filters execute inside the final count/page query, including OR and negative relations.
                for(String op : List.of("contains","not_contains","eq","ne","is_empty","is_not_empty")) {
                    var condition = new AdvancedFilterConditionReqVO(); condition.setFieldKey("cashback.customerName"); condition.setOperator(op);
                    if(!op.startsWith("is_")) condition.setValue("共同姓名");
                    var group = new AdvancedFilterGroupReqVO(); group.setConditions(List.of(condition)); request.setAdvancedFilter(group); request.setKeyword(null);
                    var actual=search.search(request,7L);
                    long expectedTotal=switch(op) {case "contains","eq" -> 592L;case "not_contains","ne" -> 3L;case "is_empty" -> 0L;default -> 595L;};
                    assertEquals(expectedTotal,actual.getTotal(),op);
                    assertFalse(actual.getList().stream().anyMatch(row -> row.getId()==5));
                }
                var injected = new AdvancedFilterConditionReqVO(); injected.setFieldKey("cashback.cashbackNo"); injected.setOperator("eq"); injected.setValue("' OR 1=1 --");
                var group = new AdvancedFilterGroupReqVO(); group.setConditions(List.of(injected)); request.setAdvancedFilter(group);
                assertEquals(0,search.search(request,7L).getTotal());
                request.setAdvancedFilter(null);request.setKeyword(null);
                jdbc.update("UPDATE zsjos_lead SET submitted_name=NULL WHERE id=2");session.clearCache();
                var empty=new AdvancedFilterConditionReqVO();empty.setFieldKey("cashback.customerName");empty.setOperator("is_empty");
                group.setConditions(List.of(empty));request.setAdvancedFilter(group);
                assertEquals(List.of(2L),search.search(request,7L).getList().stream().map(CashbackDO::getId).toList());
                empty.setOperator("ne");empty.setValue("共同姓名");
                assertEquals(3,search.search(request,7L).getTotal(),"negative relation retains SQL NULL behavior");
                empty.setFieldKey("cashback.beneficiaryName");empty.setOperator("contains");empty.setValue("旧受益人");
                assertEquals(2,search.search(request,7L).getTotal(),"partner precedence and legacy beneficiary names");
                doReturn(false).when(trace).canQuerySource(anyString());
                request.setAdvancedFilter(null);request.setKeyword("共同姓名");
                assertEquals(591,search.search(request,7L).getTotal(),"hidden source names do not contribute matches");
                request.setAdvancedFilter(group);empty.setFieldKey("cashback.customerName");
                assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->search.search(request,7L));
            } finally { TenantContextHolder.clear(); }
        }
    }
}
