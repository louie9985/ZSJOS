package cn.iocoder.yudao.module.zsjos.service.deliveryperformance;

import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryperformance.vo.DeliveryOrderDetailVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryperformance.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class DeliveryOrderDetailTest {
    private SqlSession session;
    private DeliveryOrderDetailsService service;
    private DeliveryOrderDetailVO.Query query;
    private DeliveryPerformanceReportQuery scope;
    private static final LocalDateTime NOW = LocalDateTime.of(2026,10,10,12,0);
    public static String hex(String value) { return value == null ? null : HexFormat.of().withUpperCase().formatHex(value.getBytes(StandardCharsets.UTF_8)); }
    public static String jsonExtract(String json, String path) {
        if (json == null) return null;
        Object value = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(json, java.util.Map.class).get(path.substring(2));
        return value == null ? null : value.toString();
    }
    public static String jsonUnquote(String value) { return value; }
    private static String key(String value) { return "value:" + hex(value); }
    @BeforeEach void setup() throws Exception {
        var ds = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:order-details-" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        var config = new Configuration(); config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("order-details", new JdbcTransactionFactory(), ds)); config.addMapper(DeliveryOrderDetailMapper.class);
        session = new SqlSessionFactoryBuilder().build(config).openSession();
        try (var sql = session.getConnection().createStatement()) {
            sql.execute("CREATE ALIAS JSON_EXTRACT FOR 'cn.iocoder.yudao.module.zsjos.service.deliveryperformance.DeliveryOrderDetailTest.jsonExtract'");
            sql.execute("CREATE ALIAS JSON_UNQUOTE FOR 'cn.iocoder.yudao.module.zsjos.service.deliveryperformance.DeliveryOrderDetailTest.jsonUnquote'");
            sql.execute("CREATE ALIAS HEX FOR 'cn.iocoder.yudao.module.zsjos.service.deliveryperformance.DeliveryOrderDetailTest.hex'");
            sql.execute("CREATE TABLE zsjos_lead(id BIGINT,tenant_id BIGINT,deleted INT,lead_no VARCHAR,source_type VARCHAR,source_provider_user_id BIGINT,lead_category_label_snapshot VARCHAR)");
            sql.execute("CREATE TABLE zsjos_order(id BIGINT,tenant_id BIGINT,deleted INT,order_no VARCHAR,lead_id BIGINT,formal_sales_user_id BIGINT,submitted_at TIMESTAMP,total_amount DECIMAL(18,2),order_type VARCHAR,status VARCHAR)");
            sql.execute("CREATE TABLE zsjos_performance_attribution(fact_type VARCHAR,fact_id BIGINT,tenant_id BIGINT,deleted INT,user_name VARCHAR,received_at TIMESTAMP,assignment_id BIGINT,source_group VARCHAR,channel_label VARCHAR,channel_code VARCHAR,dept_id BIGINT,center_id BIGINT)");
            sql.execute("CREATE TABLE zsjos_order_item(id BIGINT,order_id BIGINT,tenant_id BIGINT,deleted INT,product_ref VARCHAR,product_snapshot VARCHAR,payable_amount DECIMAL(18,2))");
            sql.execute("CREATE TABLE zsjos_lead_assignment_history(id BIGINT,tenant_id BIGINT,deleted INT,lead_id BIGINT,to_owner_user_id BIGINT,action_type VARCHAR,occurred_at TIMESTAMP)");
            sql.execute("INSERT INTO zsjos_lead VALUES(1,1,0,'KZ-1','sales_self_sourced',NULL,'分类甲'),(2,1,0,'KZ-2','partner',NULL,'分类乙'),(3,1,1,'HIDDEN','partner',NULL,'隐藏分类')");
            sql.execute("INSERT INTO zsjos_order VALUES"
                + "(1,1,0,'DD-%_A',1,1,'2026-10-10 10:00:00',2000,'first_purchase','effective'),"
                + "(2,1,0,'DD-2',2,1,'2026-10-10 10:00:00',100,'repurchase','effective'),"
                + "(3,1,0,'DD-3',3,1,'2026-10-10 09:00:00',0,'first_purchase','effective'),"
                + "(4,1,0,'DD-4',NULL,1,'2026-10-10 09:00:00',0.01,'first_purchase','effective'),"
                + "(5,1,0,'PENDING',1,1,'2026-10-10 09:00:00',500,'first_purchase','pending_approval'),"
                + "(6,1,0,'OUTSIDE',1,1,'2026-10-10 09:00:00',300,'first_purchase','effective'),"
                + "(7,2,0,'FOREIGN',1,1,'2026-10-10 09:00:00',400,'first_purchase','effective'),"
                + "(8,1,1,'DELETED',1,1,'2026-10-10 09:00:00',700,'first_purchase','effective'),"
                + "(9,1,0,'PRIOR',1,1,'2026-09-30 23:59:59',800,'first_purchase','effective')");
            for (int id : List.of(1,2,3,5,6,8,9)) sql.execute("INSERT INTO zsjos_performance_attribution VALUES('ORDER',"+id+",1,0,'历史教务','2026-10-01 00:00:00',1,'"+(id==3?"self":"inbound")+"','订单渠道旧名','old',"+(id==6?13:12)+",20)");
            sql.execute("INSERT INTO zsjos_order_item VALUES(1,1,1,0,'a','{\"name\":\"商品甲\"}',400),(2,1,1,0,'a','{\"name\":\"商品甲\"}',600),(3,1,1,0,'b','{\"name\":\"商品乙\"}',1000),(4,2,1,0,'a','{\"name\":\"商品甲\"}',100),(5,1,2,0,'foreign','{}',999),(6,1,1,1,'deleted','{}',999)");
        }
        try (var sql = session.getConnection().createStatement()) {
            sql.execute("CREATE TABLE zsjos_performance_org(tenant_id BIGINT,deleted INT,dept_id BIGINT,business_domain VARCHAR,create_time TIMESTAMP,update_time TIMESTAMP)");
            sql.execute("ALTER TABLE zsjos_performance_attribution ADD business_domain VARCHAR DEFAULT 'delivery'");
            sql.execute("ALTER TABLE zsjos_performance_attribution ADD org_source VARCHAR DEFAULT 'frozen'");
            for (String column : List.of("submission_source_type VARCHAR","source_provider_recorded BOOLEAN","provider_owner_type VARCHAR","provider_owner_id BIGINT")) sql.execute("ALTER TABLE zsjos_lead ADD "+column);
        }
        service = new DeliveryOrderDetailsService(); ReflectionTestUtils.setField(service,"mapper",session.getMapper(DeliveryOrderDetailMapper.class));
        query = new DeliveryOrderDetailVO.Query(); query.setPageSize(20);
        scope = new DeliveryPerformanceReportQuery(); scope.setBusinessDomain("delivery").setTenant(1L).setType("USER").setId(1L).setDepartments(Set.of(12L));
    }
    @AfterEach void close() { session.close(); }
    private DeliveryOrderDetailVO.Response read() { return service.read(query,scope,DeliveryPerformancePeriods.window("month",NOW),NOW); }
    @Test void allOrdersPreserveAmountsSnapshotsAndStablePagination() {
        query.setPageSize(1);var result=read();assertEquals(3,result.total());assertEquals(new BigDecimal("2100.00"),result.summary().amount());
        assertEquals(2L,result.list().getFirst().id());assertEquals("非引流",result.list().getFirst().sourceLabel());
        query.setPageNo(2);result=read();assertEquals(1L,result.list().getFirst().id());assertEquals(2,result.list().getFirst().products().size());
        assertEquals("订单渠道旧名",result.list().getFirst().channelLabel());
        assertEquals(2,result.options().products().stream().filter(p->p.label().equals("商品甲")).findFirst().orElseThrow().count());
        query.setPageNo(3);result=read();assertNull(result.list().getFirst().leadId());assertNull(result.list().getFirst().leadNo());assertEquals("客资分类快照缺失",result.list().getFirst().categoryLabel());
        query.setPageNo(4);result=read();assertTrue(result.list().isEmpty());assertEquals(3,result.total());
    }
    @Test void combinedFiltersApplyBeforePaginationWithoutChangingFacetsOrSummary() {
        var original=read();query.setSourceKey("self");query.setOrderTypeKey(key("repurchase"));query.setCategoryKey(key("分类乙"));
        query.setChannelKey(key("订单渠道旧名"));query.setProductKey(key("a")+"."+key("商品甲"));query.setKeyword(" kz-2 ");
        var result=read();assertEquals(1,result.total());assertEquals(2L,result.list().getFirst().id());assertEquals(new BigDecimal("100.00"),result.filteredSummary().amount());
        assertEquals(original.options(),result.options());assertEquals(original.summary(),result.summary());
        query.setProductKey("unknown");assertEquals(0,read().total());
        query.setProductKey(key("a")+"."+key("商品甲"));query.setSourceKey("SELF");assertEquals(0,read().total());
    }
    @Test void keywordTreatsWildcardsAsLiteralTextAndIncludesOrderAndLeadNumbers() {
        query.setKeyword("%_");assertEquals(1,read().total());query.setKeyword("dd-%_a");assertEquals(1,read().total());
        query.setKeyword("KZ-2");assertEquals(2L,read().list().getFirst().id());query.setKeyword("HIDDEN");assertEquals(0,read().total());
    }
    @Test void productEntryRetainsProductAmountWhileProductFilterRetainsWholeOrderAmount() {
        query.setProductKey(key("a")+"."+key("商品甲"));assertEquals(new BigDecimal("2100.00"),read().filteredSummary().amount());
        query.setDimension("product");query.setGroupKey("a|商品甲");var result=read();assertEquals("product",result.amountKind());
        assertEquals(new BigDecimal("1100.00"),result.summary().amount());assertEquals(new BigDecimal("1000.00"),result.list().getLast().amount());
        assertEquals(new BigDecimal("2000.00"),result.list().getLast().totalAmount());
        query.setProductKey(key("b")+"."+key("商品乙"));result=read();assertEquals(1,result.total());assertEquals(new BigDecimal("1000.00"),result.filteredSummary().amount());
    }
    @Test void fixedEntryConstraintsCannotBeRemovedByOptionalFilters() {
        query.setDimension("source");query.setGroupKey("inbound|线上引流");assertEquals(1,read().total());
        query.setSourceKey("self");assertEquals(0,read().total());query.setSourceKey(null);assertEquals(1,read().total());
        query.setDimension("contributor");query.setGroupKey("2");assertEquals(0,read().total());query.setGroupKey("1");assertEquals(3,read().total());
        query.setDimension("category");assertThrows(RuntimeException.class,this::read);
    }
    @Test void salesFactsCannotEnterDeliveryOrderTotals() throws Exception {
        try (var sql = session.getConnection().createStatement()) {
            sql.execute("UPDATE zsjos_performance_attribution SET business_domain='sales' WHERE fact_type='ORDER' AND fact_id=1");
        }
        session.clearCache();
        assertEquals(2,read().total());
        assertEquals(new BigDecimal("100.00"),read().summary().amount());
    }
    @Test void historicalScopeTenantAndMissingAttributionArePreserved() {
        scope.setDepartments(Set.of());assertEquals(0,read().total());scope.setAllDepartments(true).setMissingDepartment(false);assertEquals(4,read().total());
        scope.setMissingDepartment(true);assertEquals(4,read().total());assertEquals(new BigDecimal("2400.00"),read().summary().amount());
        scope.setTenant(2L);assertEquals(0,read().total());scope.setTenant(3L);assertEquals(0,read().total());
        scope.setBusinessDomain("delivery").setTenant(1L).setType("DEPT").setId(12L);assertEquals(3,read().total());
        scope.setType("CENTER").setId(20L);assertEquals(4,read().total());
    }
    @Test void mysqlStatementsPassProductionTenantParserForAllProjections() {
        var config = new Configuration();config.addMapper(DeliveryOrderDetailMapper.class);
        var parser = new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler() {
            public net.sf.jsqlparser.expression.Expression getTenantId(){return new net.sf.jsqlparser.expression.LongValue(1);}
            public boolean ignoreTable(String name){return !name.startsWith("zsjos_");}
        });
        for(String dimension:List.of("source","product","contributor")) {
            var q=new DeliveryOrderDetailQuery();q.setFilters(query);q.setDimension(dimension).setGroupKey("a|商品甲").setType("DEPT");
            for(String method:List.of("summary","filteredSummary","options","page","pageProducts")) {
                String sql=config.getMappedStatement(DeliveryOrderDetailMapper.class.getName()+"."+method).getBoundSql(q).getSql();
                assertTrue(parser.parserSingle(sql,null).contains("tenant_id = 1"));
            }
        }
    }
}
