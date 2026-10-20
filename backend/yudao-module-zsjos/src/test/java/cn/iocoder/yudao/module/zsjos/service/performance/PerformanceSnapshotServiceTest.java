package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.PerformanceAttributionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceAttributionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PerformanceSnapshotServiceTest {
    private final PerformanceAttributionMapper mapper = mock(PerformanceAttributionMapper.class);
    private final LeadMapper leads = mock(LeadMapper.class);
    private final LeadAssignmentHistoryMapper histories = mock(LeadAssignmentHistoryMapper.class);
    private final PerformanceSnapshotService service = new PerformanceSnapshotService();
    private final LocalDateTime submitted = LocalDateTime.parse("2026-09-30T11:42:08");
    private final LocalDateTime received = submitted.minusDays(13);
    private final SalesOrderDO order = new SalesOrderDO();
    private final LeadDO lead = new LeadDO();

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service,"mapper",mapper);
        ReflectionTestUtils.setField(service,"leadMapper",leads);
        ReflectionTestUtils.setField(service,"assignmentHistoryMapper",histories);
        ReflectionTestUtils.setField(service,"access",mock(PerformanceAccess.class));
        order.setId(10L);order.setLeadId(1L);order.setFormalSalesUserId(17L);
        order.setSubmittedAt(submitted);order.setOrderType("first_purchase");
        lead.setId(1L);lead.setCurrentAssignmentHistoryId(5951L);
        lead.setOwnershipStartedAt(received.minusHours(8));lead.setSourceType("internal_new_media");
        when(leads.selectById(1L)).thenReturn(lead);
        when(mapper.selectCount(any())).thenReturn(0L);
    }
    private LeadAssignmentHistoryDO history(String action, Long owner) {
        var h=new LeadAssignmentHistoryDO();h.setId(7651L);h.setLeadId(1L);
        h.setActionType(action);h.setToOwnerUserId(owner);h.setOccurredAt(received);
        when(histories.selectLatestOwnershipAt(1L,submitted)).thenReturn(h);return h;
    }
    private PerformanceAttributionDO snapshot() {
        service.order(order);var capture=ArgumentCaptor.forClass(PerformanceAttributionDO.class);
        verify(mapper).insert(capture.capture());return capture.getValue();
    }
    @Test void legacyLinkAndWrongLeadTimeAreReplacedByRealReceipt() {
        history("claim",17L);var row=snapshot();
        assertEquals(7651L,row.getAssignmentId());assertEquals(received,row.getReceivedAt());
        assertEquals(17L,row.getUserId());assertEquals("frozen",row.getOrgSource());
        verify(histories).selectLatestOwnershipAt(1L,submitted);
    }
    @Test void assignmentFactTimeMatchesReaderInsteadOfCurrentLeadTime() {
        history("accept",17L);var fact=new PerformanceAttributionDO();
        fact.setReceivedAt(received.plusMinutes(1));when(mapper.selectOne(any())).thenReturn(fact);
        assertEquals(fact.getReceivedAt(),snapshot().getReceivedAt());
    }
    @Test void latestUnownedTransitionCannotReviveOldReceipt() {
        history("recycle",null);var row=snapshot();
        assertNull(row.getAssignmentId());assertNull(row.getReceivedAt());
    }
    @Test void latestOtherOwnerDoesNotBecomeThisSellersReceipt() {
        history("transfer",22L);var row=snapshot();
        assertNull(row.getAssignmentId());assertNull(row.getReceivedAt());
    }
    @Test void missingHistoryDoesNotCopyLegacyLink() {
        var row=snapshot();assertNull(row.getAssignmentId());assertNull(row.getReceivedAt());
    }
    @Test void repurchaseKeepsItsSourceGroupWithVerifiedReceipt() {
        order.setOrderType("repurchase");history("transfer",17L);
        var row=snapshot();assertEquals("repurchase",row.getSourceGroup());
        assertEquals(7651L,row.getAssignmentId());
    }
    @Test void noLeadDoesNotReadHistoryOrInventReceipt() {
        order.setLeadId(null);var row=snapshot();
        assertNull(row.getAssignmentId());assertNull(row.getReceivedAt());verifyNoInteractions(histories);
    }
    @Test void ownershipQueryRespectsTenantDeletionCutoffAndUnownedTransitions() throws Exception {
        var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:receipt-writer-"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new org.apache.ibatis.mapping.Environment("receipt",
                new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),ds));
        var plugins=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(
                new com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler(){
                    public net.sf.jsqlparser.expression.Expression getTenantId(){
                        return new net.sf.jsqlparser.expression.LongValue(1);
                    }
                }));
        config.addInterceptor(plugins);config.addMapper(LeadAssignmentHistoryMapper.class);
        var factory=new com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder().build(config);
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
        var ddl=new StringBuilder("CREATE TABLE zsjos_lead_assignment_history(id BIGINT PRIMARY KEY");
        for(var field:com.baomidou.mybatisplus.core.metadata.TableInfoHelper.getTableInfo(LeadAssignmentHistoryDO.class).getFieldList()){
            var t=field.getPropertyType();
            ddl.append(", "+field.getColumn()+" "+(t==String.class?"VARCHAR(2000)":t==LocalDateTime.class?"TIMESTAMP":"BIGINT"));
        }
        jdbc.execute(ddl.append(")").toString());
        jdbc.execute("INSERT INTO zsjos_lead_assignment_history(id,tenant_id,deleted,lead_id,action_type,to_owner_user_id,occurred_at) VALUES"
                +"(1,1,0,1,'claim',17,'2026-09-17'),(2,1,0,1,'other',17,'2026-09-21'),"
                +"(3,2,0,1,'transfer',22,'2026-09-22'),(4,1,1,1,'recycle',NULL,'2026-09-23'),"
                +"(5,1,0,1,'transfer',22,'2026-10-01'),(8,1,0,2,'transfer',22,'2026-09-29')");
        try(var session=factory.openSession()){
            var query=session.getMapper(LeadAssignmentHistoryMapper.class);
            assertEquals(1L,query.selectLatestOwnershipAt(1L,submitted).getId());
            assertNull(query.selectLatestOwnershipAt(1L,received.minusYears(1)));
            jdbc.execute("INSERT INTO zsjos_lead_assignment_history(id,tenant_id,deleted,lead_id,action_type,to_owner_user_id,occurred_at) VALUES(6,1,0,1,'release_to_claim_pool',NULL,'2026-09-29')");
            session.clearCache();var unowned=query.selectLatestOwnershipAt(1L,submitted);
            assertEquals(6L,unowned.getId());assertNull(unowned.getToOwnerUserId());
            jdbc.execute("INSERT INTO zsjos_lead_assignment_history(id,tenant_id,deleted,lead_id,action_type,to_owner_user_id,occurred_at) VALUES(7,1,0,1,'transfer',17,'2026-09-29')");
            session.clearCache();assertEquals(7L,query.selectLatestOwnershipAt(1L,submitted).getId());
        }
    }
}
