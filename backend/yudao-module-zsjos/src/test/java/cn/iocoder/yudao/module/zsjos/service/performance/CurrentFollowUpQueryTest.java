package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CurrentFollowUpQueryTest {
    @Test void currentCountsAndPagesIgnoreHistoryAndRespectTaskBoundaries() throws Exception {
        var source=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:current-followup-"+UUID.randomUUID()+";MODE=MySQL","sa","");
        var config=new Configuration();config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("current-followup",new JdbcTransactionFactory(),source));
        config.addMapper(PerformanceReportMapper.class);config.addMapper(PerformanceDetailMapper.class);
        try(var session=new SqlSessionFactoryBuilder().build(config).openSession()){
            try(var sql=session.getConnection().createStatement()){
                sql.execute("CREATE ALIAS JSON_EXTRACT FOR 'cn.iocoder.yudao.module.zsjos.dal.mysql.lead.SubordinateSalesDailyQueryTest.jsonExtract'");
                sql.execute("CREATE ALIAS JSON_UNQUOTE FOR 'cn.iocoder.yudao.module.zsjos.dal.mysql.lead.SubordinateSalesDailyQueryTest.jsonUnquote'");
                sql.execute("CREATE TABLE zsjos_lead(id BIGINT,tenant_id BIGINT,deleted INT,lead_no VARCHAR,owner_user_id BIGINT,ownership_started_at TIMESTAMP,lead_category_label_snapshot VARCHAR,sales_stage_label_snapshot VARCHAR)");
                sql.execute("INSERT INTO zsjos_lead VALUES(1,1,0,'LD-CURRENT',99,'2026-10-01',NULL,NULL),(1,2,0,'OTHER-TENANT',99,NULL,NULL,NULL)");
                sql.execute("CREATE TABLE zsjos_business_task(id BIGINT,tenant_id BIGINT,deleted INT,biz_id BIGINT,assignee_id BIGINT,create_time TIMESTAMP,due_at TIMESTAMP,completed_at TIMESTAMP,status VARCHAR,task_type VARCHAR,payload VARCHAR)");
                // All tasks share a lead: current responsibility belongs to the assignee, not the lead owner.
                for(int id=1;id<=14;id++){
                    String due=switch(id){case 1->"'2026-10-14 23:59:59'";case 2,12,13,14->"'2026-10-15 00:00:00'";case 3->"'2026-10-15 12:00:00'";case 4->"'2026-10-16 00:00:00'";case 5->"NULL";default->"'2026-10-15 11:59:59'";};
                    String status=id==6?"completed":id==7?"cancelled":"pending";
                    String type=id==8?"lead_first_follow_up":id==14?"lead_qualification":"lead_follow_up_reminder";
                    String payload=id==8?"{\"generationSource\":\"sales_self_sourced_auto\"}":"{}";
                    sql.execute("INSERT INTO zsjos_business_task VALUES("+id+","+(id==9?2:1)+","+(id==10?1:0)+",1,"+(id==11?3:id==12?2:1)+",'2026-09-01',"+due+",NULL,'"+status+"','"+type+"','"+payload+"')");
                }
                // Historical duplicate/old department facts cannot multiply current rows.
                sql.execute("CREATE TABLE zsjos_performance_attribution(fact_id BIGINT,dept_id BIGINT)");
                sql.execute("INSERT INTO zsjos_performance_attribution VALUES(1,90),(1,91),(2,92)");
            }
            var reports=session.getMapper(PerformanceReportMapper.class);var details=session.getMapper(PerformanceDetailMapper.class);
            var q=new PerformanceReportQuery();q.setTenant(1L);q.setNow(LocalDateTime.of(2026,10,15,12,0));
            q.setToday(q.getNow().toLocalDate().atStartOfDay());q.setTomorrow(q.getToday().plusDays(1));q.setSize(2);
            Map<String,Long> sums=new HashMap<>();
            for(var ids:List.of(Set.of(1L),Set.of(2L),Set.of(1L,2L),Set.<Long>of())){
                q.setAssigneeIds(ids);var counts=reports.currentFollowUps(q);
                for(var aggregate:counts){
                    q.setMetric(aggregate.getKey());q.setOffset(0);
                    long total=details.count(q);assertEquals(aggregate.getCount(),total);
                    Set<Long> seen=new HashSet<>();
                    for(long offset=0;offset<total;offset+=2){
                        q.setOffset(offset);for(var row:details.page(q)){assertTrue(seen.add(row.getId()));assertEquals("LD-CURRENT",row.getNumber());assertEquals(99L,row.getOwnerUserId());}
                    }
                    assertEquals(total,seen.size());
                    if(ids.size()==1)sums.merge(q.getMetric(),total,Long::sum);
                    else if(ids.size()==2){assertEquals(sums.get(q.getMetric()),total);assertEquals(4,total);}
                    else assertEquals(0,total);
                }
            }
        }
    }
}
