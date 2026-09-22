package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.advancedfilter.AdvancedFilterMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="ZSJOS_STAGE_QUERY_JDBC_URL", matches=".+")
class LeadSalesStageFilterQueryTest {
    private AdvancedFilterConditionReqVO condition(String key, String operator, Object value) {
        var c=new AdvancedFilterConditionReqVO();c.setFieldKey(key);c.setOperator(operator);c.setValue(value);return c;
    }
    private AdvancedFilterGroupReqVO group(AdvancedFilterConditionReqVO... values) {
        var g=new AdvancedFilterGroupReqVO();g.setLogic("AND");g.setConditions(new ArrayList<>(List.of(values)));g.setGroups(new ArrayList<>());return g;
    }
    @Test void executesCombinedFiltersWithTenantDeletionAndActualWonTime() throws Exception {
        var ds=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",System.getenv("ZSJOS_STAGE_QUERY_JDBC_URL"),System.getenv("ZSJOS_STAGE_QUERY_USER"),System.getenv("ZSJOS_STAGE_QUERY_PASSWORD"));
        var cfg=new MybatisConfiguration();cfg.setEnvironment(new Environment("stage",new JdbcTransactionFactory(),ds));cfg.addMapper(AdvancedFilterMapper.class);
        try(var session=new MybatisSqlSessionFactoryBuilder().build(cfg).openSession()) {
            try(var sql=session.getConnection().createStatement()) {
                sql.execute("CREATE TEMPORARY TABLE zsjos_lead(id BIGINT,tenant_id BIGINT,deleted BOOLEAN,sales_stage VARCHAR(100),status VARCHAR(100),owner_user_id BIGINT,submitted_at TIMESTAMP,last_follow_up_at TIMESTAMP,qualified_at TIMESTAMP,converted_at TIMESTAMP)");
                sql.execute("CREATE TEMPORARY TABLE zsjos_opportunity(id BIGINT,lead_id BIGINT,tenant_id BIGINT,deleted BOOLEAN,won_at TIMESTAMP,type VARCHAR(100))");
                sql.execute("INSERT INTO zsjos_lead VALUES (1,7,false,'contacted','valid',20,'2026-09-01','2026-09-22','2026-09-02','2026-09-02'),(2,7,false,'intent_customer','won',20,'2026-09-01','2026-09-22','2026-09-02','2026-09-02'),(3,8,false,'contacted','valid',20,'2026-09-01','2026-09-22','2026-09-02','2026-09-02'),(4,7,true,'contacted','valid',20,'2026-09-01','2026-09-22','2026-09-02','2026-09-02'),(5,7,false,NULL,'won',21,'2026-09-01',NULL,'2026-09-02','2026-09-02')");
                sql.execute("INSERT INTO zsjos_opportunity VALUES (1,2,7,false,'2026-09-20','initial_conversion'),(2,5,8,false,'2026-09-20','initial_conversion'),(3,5,7,true,'2026-09-20','initial_conversion'),(4,1,7,false,'2026-09-20','repurchase')");
            }
            var service=new AdvancedFilterService();ReflectionTestUtils.setField(service,"mapper",session.getMapper(AdvancedFilterMapper.class));
            var org=mock(LeadFilterOrganizationService.class);ReflectionTestUtils.setField(service,"leadFilterOrganizations",org);
            when(org.ownerIds(List.of("10"))).thenReturn(List.of(20L));
            TenantContextHolder.setTenantId(7L);
            assertEquals(List.of(1L),service.matchLeadIds(group(condition("lead.salesStage","in",List.of("contacted")),condition("lead.ownerDeptId","in",List.of("10")),condition("lead.qualificationStatus","in",List.of("valid")),condition("lead.dealStatus","in",List.of("not_won")),condition("lead.submittedAt","gte",LocalDateTime.of(2026,9,1,0,0)),condition("lead.lastFollowUpAt","gte",LocalDateTime.of(2026,9,21,0,0)))));
            assertEquals(List.of(2L),service.matchLeadIds(group(condition("lead.convertedAt","gte",LocalDateTime.of(2026,9,19,0,0)))));
            assertEquals(List.of(5L),service.matchLeadIds(group(condition("lead.salesStage","is_empty",null))));
            assertEquals(List.of(2L),service.matchLeadIds(group(condition("lead.salesStage","not_in",List.of("contacted")))));
            when(org.ownerIds(List.of("10"))).thenReturn(List.of());
            assertEquals(List.of(),service.matchLeadIds(group(condition("lead.ownerDeptId","in",List.of("10")))));
        } finally {TenantContextHolder.clear();}
    }
}
