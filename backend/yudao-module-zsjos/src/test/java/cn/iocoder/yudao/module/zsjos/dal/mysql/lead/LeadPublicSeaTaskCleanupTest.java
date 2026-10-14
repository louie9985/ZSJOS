package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import org.junit.jupiter.api.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import static org.junit.jupiter.api.Assertions.*;

class LeadPublicSeaTaskCleanupTest {
    private Connection db;
    private final LocalDateTime now=LocalDateTime.parse("2026-10-14T12:00:00");

    @BeforeEach void setup() throws Exception {
        db=DriverManager.getConnection("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL");
        sql("CREATE TABLE zsjos_lead(id BIGINT,tenant_id BIGINT,deleted INT)");
        sql("CREATE TABLE zsjos_lead_aging_pool_cycle(lead_id BIGINT,tenant_id BIGINT,deleted INT,status VARCHAR,collaborator_user_id BIGINT,assigned_at TIMESTAMP,entered_at TIMESTAMP)");
        sql("CREATE TABLE zsjos_lead_public_sea_record(lead_id BIGINT,tenant_id BIGINT,deleted INT,collaborator_user_id BIGINT,released_at TIMESTAMP)");
        sql("CREATE TABLE zsjos_business_task(id BIGINT PRIMARY KEY,biz_id BIGINT,tenant_id BIGINT,deleted INT,biz_type VARCHAR,task_type VARCHAR,status VARCHAR,assignee_id BIGINT,create_time TIMESTAMP,cancelled_at TIMESTAMP,cancel_reason VARCHAR,version INT DEFAULT 0,update_time TIMESTAMP,updater VARCHAR)");
        sql("INSERT INTO zsjos_lead VALUES(1,1,0),(2,1,0),(3,1,0),(1,2,0)");
        sql("INSERT INTO zsjos_lead_aging_pool_cycle VALUES(1,1,0,'assigned',20,'2026-10-14 10:00:00','2026-10-14 09:00:00'),(2,1,0,'waiting_assignment',NULL,NULL,'2026-10-14 09:00:00')");
    }
    @AfterEach void close() throws Exception {db.close();}
    private void sql(String s) throws Exception {try(var statement=db.createStatement()){statement.execute(s);}}
    private void task(long id,long lead,long tenant,long assignee,String created,String status,String type) throws Exception {
        try(var s=db.prepareStatement("INSERT INTO zsjos_business_task(id,biz_id,tenant_id,deleted,biz_type,task_type,status,assignee_id,create_time) VALUES(?,?,?,0,'lead',?,?,?,?)")) {
            s.setLong(1,id);s.setLong(2,lead);s.setLong(3,tenant);s.setString(4,type);s.setString(5,status);s.setLong(6,assignee);s.setString(7,created);s.executeUpdate();
        }
    }
    private PreparedStatement bind(String script,Map<String,Object> args) throws Exception {
        var config=new Configuration();
        var bound=new XMLLanguageDriver().createSqlSource(config,script,Map.class).getBoundSql(args);
        net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(bound.getSql());
        var s=db.prepareStatement(bound.getSql());
        for(int i=0;i<bound.getParameterMappings().size();i++){
            String property=bound.getParameterMappings().get(i).getProperty();
            Object value=bound.hasAdditionalParameter(property)?bound.getAdditionalParameter(property):args.get(property);
            s.setObject(i+1,value);
        }
        return s;
    }
    private List<Long> candidates() throws Exception {
        Map<String,Object> args=new HashMap<>();args.put("tenantId",1L);args.put("leadId",null);
        try(var s=bind(LeadPublicSeaTaskCleanupSql.candidates(),args);var r=s.executeQuery()){
            List<Long> ids=new ArrayList<>();while(r.next())ids.add(r.getLong(1));return ids;
        }
    }
    private int cancel(List<Long> ids) throws Exception {
        if(ids.isEmpty())return 0;
        try(var s=bind(LeadPublicSeaTaskCleanupSql.cancel(),Map.of("tenantId",1L,"ids",ids,"now",now,"reason","公海跟进责任变更"))){return s.executeUpdate();}
    }
    @Test void cancelsOnlyObsoletePendingTasksAndIsRepeatable() throws Exception {
        String before="2026-10-14 08:00:00",after="2026-10-14 11:00:00",reminder="lead_follow_up_reminder";
        task(1,1,1,10,before,"pending",reminder); // original A
        task(2,1,1,20,before,"pending",reminder); // old A task already reassigned to B
        task(3,1,1,20,after,"pending",reminder); // current B's valid reminder
        task(4,1,1,20,after,"pending","lead_first_follow_up");
        task(5,2,1,10,after,"pending",reminder); // waiting
        task(6,3,1,10,before,"pending",reminder); // outside pool
        task(7,1,2,10,before,"pending",reminder); // other tenant
        task(8,1,1,10,before,"completed",reminder);
        task(9,1,1,10,before,"cancelled",reminder);
        task(10,1,1,10,before,"pending","lead_qualification");
        task(11,1,1,10,before,"pending",reminder);sql("UPDATE zsjos_business_task SET deleted=1 WHERE id=11");
        assertEquals(List.of(1L,2L,4L,5L),candidates());assertEquals(4,cancel(candidates()));
        assertTrue(candidates().isEmpty());assertEquals(0,cancel(List.of(1L,2L,4L,5L)));
        try(var s=db.createStatement();var r=s.executeQuery("SELECT status,version,cancel_reason,cancelled_at FROM zsjos_business_task WHERE id=1")){
            assertTrue(r.next());assertEquals("cancelled",r.getString(1));assertEquals(1,r.getInt(2));
            assertEquals("公海跟进责任变更",r.getString(3));assertEquals(now,r.getObject(4,LocalDateTime.class));
        }
    }
    @Test void updateRechecksPoolExitAndCompletionAfterScan() throws Exception {
        task(1,1,1,10,"2026-10-14 08:00:00","pending","lead_follow_up_reminder");
        var ids=candidates();assertEquals(List.of(1L),ids);
        sql("UPDATE zsjos_lead_aging_pool_cycle SET status='exited'");assertEquals(0,cancel(ids));
        sql("UPDATE zsjos_lead_aging_pool_cycle SET status='assigned'");
        sql("UPDATE zsjos_business_task SET status='completed'");assertEquals(0,cancel(ids));
    }
    @Test void legacyPublicSeaUsesCurrentBAndReleaseTime() throws Exception {
        sql("INSERT INTO zsjos_lead_public_sea_record VALUES(3,1,0,20,'2026-10-14 10:00:00')");
        task(1,3,1,10,"2026-10-14 08:00:00","pending","lead_follow_up_reminder");
        task(2,3,1,20,"2026-10-14 11:00:00","pending","lead_follow_up_reminder");
        assertEquals(List.of(1L),candidates());assertEquals(1,cancel(candidates()));
    }
}
