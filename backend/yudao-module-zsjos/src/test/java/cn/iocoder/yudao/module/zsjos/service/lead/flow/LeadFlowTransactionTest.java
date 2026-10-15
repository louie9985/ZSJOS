package cn.iocoder.yudao.module.zsjos.service.lead.flow;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LeadFlowTransactionTest {
    public static class Command {
        private final JdbcTemplate jdbc;
        Command(JdbcTemplate jdbc) { this.jdbc=jdbc; }
        @LeadFlowOperation(value="主管恢复客资")
        public void restore(Long id, Long actor, boolean fail) {
            jdbc.update("UPDATE test_lead SET state='submitted' WHERE id=?",id);
            if(fail) throw new IllegalStateException("business failure");
        }
    }
    static class Recorder extends LeadFlowRecorder {
        private final JdbcTemplate jdbc;
        boolean fail;
        Recorder(JdbcTemplate jdbc) { this.jdbc=jdbc; }
        @Override public State capture(Long id) {
            return new State(Map.of("status",LeadFlowSnapshot.Value.text(jdbc.queryForObject("SELECT state FROM test_lead WHERE id=?",String.class,id))),Set.of(),true);
        }
        @Override public void record(Long id, State before, String title,String actorType,Long actor,String relatedType,Long relatedId,String no,String result,String reason,boolean changed,String key) {
            if(fail) throw new IllegalStateException("history failure");
            if(!before.values().equals(capture(id).values())) jdbc.update("INSERT INTO test_history(lead_id) VALUES(?)",id);
        }
    }
    @Test void commandAndHistoryCommitOrRollbackTogetherAndReplayStaysSilent() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:flow"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE test_lead(id BIGINT PRIMARY KEY,state VARCHAR(32))");
        jdbc.execute("CREATE TABLE test_history(lead_id BIGINT)");
        jdbc.update("INSERT INTO test_lead VALUES(1,'suspended')");
        var recorder=new Recorder(jdbc);
        var aspect=new LeadFlowOperationAspect();
        ReflectionTestUtils.setField(aspect,"recorder",recorder);
        ReflectionTestUtils.setField(aspect,"transactionTemplate",new TransactionTemplate(new DataSourceTransactionManager(source)));
        var factory=new AspectJProxyFactory(new Command(jdbc)); factory.addAspect(aspect);
        Command proxy=factory.getProxy();
        TenantContextHolder.setTenantId(1L);
        try {
            assertThrows(IllegalStateException.class,()->proxy.restore(1L,20L,true));
            assertEquals("suspended",jdbc.queryForObject("SELECT state FROM test_lead",String.class));
            recorder.fail=true;
            assertThrows(IllegalStateException.class,()->proxy.restore(1L,20L,false));
            assertEquals("suspended",jdbc.queryForObject("SELECT state FROM test_lead",String.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_history",Integer.class));
            recorder.fail=false; proxy.restore(1L,20L,false); proxy.restore(1L,20L,false);
            assertEquals("submitted",jdbc.queryForObject("SELECT state FROM test_lead",String.class));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_history",Integer.class));
        } finally { TenantContextHolder.clear(); jdbc.execute("DROP ALL OBJECTS"); }
    }
}
