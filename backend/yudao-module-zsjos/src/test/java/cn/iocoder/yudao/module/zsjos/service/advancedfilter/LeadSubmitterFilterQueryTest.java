package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.advancedfilter.AdvancedFilterMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="ZSJOS_STAGE_QUERY_JDBC_URL", matches=".+")
class LeadSubmitterFilterQueryTest {
    private AdvancedFilterGroupReqVO group(String key, String operator, String value) {
        var condition = new AdvancedFilterConditionReqVO();
        condition.setFieldKey(key); condition.setOperator(operator);
        condition.setValue(value == null ? null : List.of(value));
        var group = new AdvancedFilterGroupReqVO(); group.getConditions().add(condition);
        group.setLogic("AND"); return group;
    }

    @Test void employeeAndPartnerIdsNeverCrossMatchIncludingHistoricalSources() throws Exception {
        var ds = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", System.getenv("ZSJOS_STAGE_QUERY_JDBC_URL"),
                System.getenv("ZSJOS_STAGE_QUERY_USER"), System.getenv("ZSJOS_STAGE_QUERY_PASSWORD"));
        var cfg = new MybatisConfiguration();
        cfg.setEnvironment(new Environment("submitter", new JdbcTransactionFactory(), ds));
        cfg.addMapper(AdvancedFilterMapper.class);
        try (var session = new MybatisSqlSessionFactoryBuilder().build(cfg).openSession()) {
            try (var sql = session.getConnection().createStatement()) {
                sql.execute("CREATE TEMPORARY TABLE zsjos_lead(id BIGINT,tenant_id BIGINT,deleted BOOLEAN,source_type VARCHAR(32),source_user_id BIGINT,partner_id BIGINT,provider_owner_type VARCHAR(32),source_provider_recorded BIT,source_provider_user_id BIGINT)");
                sql.execute("INSERT INTO zsjos_lead VALUES "
                        + "(1,7,0,'internal_new_media',50,NULL,'system_user',0,NULL),"
                        + "(2,7,0,'partner',50,48,'partner',0,NULL),"
                        + "(3,7,0,'partner',50,NULL,'system_user',0,NULL),"
                        + "(4,7,0,'sales_self_sourced',60,NULL,'system_user',1,50),"
                        + "(5,7,0,'internal_new_media',50,NULL,NULL,0,NULL),"
                        + "(6,8,0,'partner',50,48,'partner',0,NULL),"
                        + "(7,7,1,'partner',50,48,'partner',0,NULL),"
                        + "(8,7,0,'partner',NULL,48,'partner',0,NULL),"
                        + "(9,7,0,'internal_new_media',50,NULL,'unknown',0,NULL),"
                        + "(10,7,0,'partner',50,NULL,NULL,0,NULL)");
            }
            var service = new AdvancedFilterService();
            ReflectionTestUtils.setField(service, "mapper", session.getMapper(AdvancedFilterMapper.class));
            TenantContextHolder.setTenantId(7L);
            assertEquals(List.of(1L,3L,4L,5L), service.matchLeadIds(group("lead.sourceUserId","in","50")));
            assertEquals(List.of(2L,8L), service.matchLeadIds(group("lead.partnerSubmitterId","in","48")));
            assertEquals(List.of(), service.matchLeadIds(group("lead.partnerSubmitterId","in","50")));
            assertEquals(List.of(), service.matchLeadIds(group("lead.sourceUserId","in","48")));
            assertEquals(List.of(), service.matchLeadIds(group("lead.partnerSubmitterId","not_in","48")));
            assertEquals(List.of(), service.matchLeadIds(group("lead.sourceUserId","not_in","50")));
            assertEquals(List.of(2L,8L), service.matchLeadIds(group("lead.partnerSubmitterId","is_not_empty",null)));
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), LeadDO.class);
            var basic = new LeadManagementPageReqVO(); basic.setSourceUserId(50L);
            assertEquals(List.of(1L,3L,5L), basicIds(session.getConnection(), basic));
            basic.setSourceUserId(null); basic.setPartnerSubmitterId(48L);
            assertEquals(List.of(2L,8L), basicIds(session.getConnection(), basic));
            basic.setSourceUserId(50L);
            assertEquals(List.of(), basicIds(session.getConnection(), basic));
            TenantContextHolder.setTenantId(8L);
            assertEquals(List.of(6L), service.matchLeadIds(group("lead.partnerSubmitterId","in","48")));
        } finally { TenantContextHolder.clear(); }
    }

    private List<Long> basicIds(java.sql.Connection connection, LeadManagementPageReqVO req) throws Exception {
        var query = new LambdaQueryWrapperX<LeadDO>();
        var method = LeadMapper.class.getDeclaredMethod("applySubmitterFilters", LambdaQueryWrapperX.class,
                LeadManagementPageReqVO.class);
        method.setAccessible(true); method.invoke(null, query, req);
        var values = new java.util.ArrayList<Object>();
        var matcher = java.util.regex.Pattern.compile("#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}").matcher(query.getSqlSegment());
        var sql = new StringBuilder();
        while (matcher.find()) {
            values.add(query.getParamNameValuePairs().get(matcher.group(1)));
            matcher.appendReplacement(sql, "?");
        }
        matcher.appendTail(sql);
        try (var statement = connection.prepareStatement("SELECT id FROM zsjos_lead WHERE tenant_id=7 AND deleted=0 AND " + sql + " ORDER BY id")) {
            for (int i=0; i<values.size(); i++) statement.setObject(i+1, values.get(i));
            var result = new java.util.ArrayList<Long>();
            try (var rows = statement.executeQuery()) { while (rows.next()) result.add(rows.getLong(1)); }
            return result;
        }
    }
}
