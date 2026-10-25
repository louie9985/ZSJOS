package cn.iocoder.yudao.module.zsjos.service.account;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.DiagnosisMetricMapper;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import java.util.Map;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosisSqlContractTest {
    @Test void actualMapperStatementsBindAndParseForTheRuntimeInterceptors(){
        var base=Map.of("tenant",1L,"account",10L,"partner",30L,"metric",1L,"lead",1L,"node","node","days",14,
                "from",LocalDateTime.parse("2026-10-01T00:00:00"),"to",LocalDateTime.parse("2026-10-15T00:00:00"),"cutoff",LocalDateTime.parse("2026-10-15T00:00:00"));
        var values=new java.util.HashMap<String,Object>(base);values.put("ids",java.util.List.of(1L,2L));values.put("now",LocalDateTime.parse("2026-10-22T00:00:00"));
        values.put("from7",LocalDateTime.parse("2026-10-08T00:00:00"));values.put("from28",LocalDateTime.parse("2026-09-17T00:00:00"));
        for(var method:DiagnosisMetricMapper.class.getDeclaredMethods()){
            var select=method.getAnnotation(Select.class);var insert=method.getAnnotation(org.apache.ibatis.annotations.Insert.class);if(select==null && insert==null)continue;
            var bound=new XMLLanguageDriver().createSqlSource(new Configuration(),String.join("\n",select==null?insert.value():select.value()),Map.class).getBoundSql(values);
            assertDoesNotThrow(()->CCJSqlParserUtil.parse(bound.getSql()),method.getName());
        }
    }
    @Test void batchedEvidencePreservesCutoffTenantDistinctDealsAndMemberReplay() {
        var source=new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:diagnosis_batch_"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(source);
        jdbc.execute("CREATE TABLE zsjos_business_event(id bigint,tenant_id bigint,aggregate_type varchar(20),aggregate_id bigint,event_type varchar(40),to_status varchar(20),occurred_at timestamp,deleted int,related_object_refs varchar(500))");
        jdbc.execute("CREATE TABLE zsjos_order(tenant_id bigint,lead_id bigint,deleted int,status varchar(20),order_type varchar(20),effective_at timestamp)");
        jdbc.execute("CREATE TABLE zsjos_account_diagnosis_member(tenant_id bigint,metric_id bigint,lead_id bigint,UNIQUE(tenant_id,metric_id,lead_id))");
        jdbc.execute("INSERT INTO zsjos_business_event VALUES(1,1,'lead',1,'lead_flow_operation','valid','2026-10-01',0,'{}'),(2,1,'lead',1,'lead_flow_operation','invalid','2026-10-16',0,'{}'),(3,2,'lead',1,'lead_flow_operation','won','2026-10-14',0,'{}'),(4,1,'lead',2,'lead_flow_operation',NULL,'2026-10-02',0,'{legacy}'),(5,1,'lead',1,'lead_flow_operation','lost','2026-10-14',1,'{}')");
        jdbc.execute("INSERT INTO zsjos_order VALUES(1,1,0,'effective','new','2026-10-17'),(1,1,0,'effective','new','2026-10-18'),(1,2,0,'effective','repurchase','2026-10-18'),(2,2,0,'effective','new','2026-10-18'),(1,2,0,'effective','new','2026-10-25')");
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();
        config.setEnvironment(new org.apache.ibatis.mapping.Environment("test",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),source));
        config.addMapper(DiagnosisMetricMapper.class);
        try(var session=new com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder().build(config).openSession(true)) {
            var mapper=session.getMapper(DiagnosisMetricMapper.class);
            var cutoff=LocalDateTime.parse("2026-10-15T00:00:00");
            var statuses=mapper.statusesBefore(1L,java.util.List.of(1L,2L),cutoff);
            assertEquals(1,statuses.size());assertEquals("valid",statuses.getFirst().get("toStatus"));
            assertEquals("{legacy}",mapper.flowsBefore(1L,java.util.List.of(2L),cutoff).getFirst().get("refs"));
            assertEquals(java.util.List.of(1L),mapper.dealtLeadIds(1L,java.util.List.of(1L,2L),LocalDateTime.parse("2026-10-22T00:00:00")));
            mapper.members(1L,9L,java.util.List.of(1L,2L));mapper.members(1L,9L,java.util.List.of(1L,2L));
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM zsjos_account_diagnosis_member",Integer.class));
        }
    }

}
