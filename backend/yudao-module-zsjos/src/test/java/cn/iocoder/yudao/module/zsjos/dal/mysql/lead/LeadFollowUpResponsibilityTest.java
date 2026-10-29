package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import static org.junit.jupiter.api.Assertions.*;

class LeadFollowUpResponsibilityTest {
    private Connection db;

    @BeforeEach void setup() throws Exception {
        db = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        exec("CREATE TABLE zsjos_lead(id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted INT,owner_user_id BIGINT,lead_category VARCHAR,lead_no VARCHAR,submitted_name VARCHAR,submitted_mobile VARCHAR,submitted_wechat_id VARCHAR)");
        exec("CREATE TABLE zsjos_lead_public_sea_pool_cycle(id BIGINT PRIMARY KEY,lead_id BIGINT,tenant_id BIGINT,deleted INT,status VARCHAR,collaborator_user_id BIGINT)");
        exec("CREATE TABLE zsjos_lead_public_sea_pool_compatibility_record(id BIGINT PRIMARY KEY,lead_id BIGINT,tenant_id BIGINT,deleted INT,owner_user_id BIGINT,collaborator_user_id BIGINT)");
        exec("CREATE TABLE zsjos_business_task(id BIGINT PRIMARY KEY,biz_id BIGINT,tenant_id BIGINT,deleted INT,biz_type VARCHAR,task_type VARCHAR,status VARCHAR,assignee_type VARCHAR,assignee_id BIGINT,due_at TIMESTAMP)");
        exec("INSERT INTO zsjos_lead VALUES(1,1,0,10,'A','LD-fixture','fixture','','')");
        exec("INSERT INTO zsjos_business_task VALUES(1,1,1,0,'lead','lead_follow_up_reminder','pending','user',10,'2026-10-13 12:00:00')");
        exec("ALTER TABLE zsjos_lead ADD status VARCHAR DEFAULT 'valid'");
        exec("ALTER TABLE zsjos_lead ADD assignment_status VARCHAR DEFAULT 'owned'");
        exec("ALTER TABLE zsjos_lead ADD provider_owner_type VARCHAR DEFAULT 'system_user'");
        exec("ALTER TABLE zsjos_lead ADD provider_owner_id BIGINT DEFAULT 10");
        exec("ALTER TABLE zsjos_lead ADD recycle_source_owner_user_id BIGINT");
        exec("ALTER TABLE zsjos_lead ADD last_activity_at TIMESTAMP");
        exec("ALTER TABLE zsjos_lead ADD ownership_started_at TIMESTAMP");
        exec("CREATE TABLE zsjos_opportunity(id BIGINT,lead_id BIGINT,tenant_id BIGINT,deleted INT,type VARCHAR,status VARCHAR)");
    }
    @AfterEach void close() throws Exception { db.close(); }
    private void exec(String sql) throws Exception { try (var s=db.createStatement()) { s.execute(sql); } }
    private Long scalar(String sql) throws Exception { try(var s=db.createStatement(); var r=s.executeQuery(sql)) { assertTrue(r.next()); return (Long) r.getObject(1, Long.class); } }
    private void pool(String status, String user) throws Exception { exec("INSERT INTO zsjos_lead_public_sea_pool_cycle VALUES(1,1,1,0,'"+status+"',"+user+")"); }
    private Long responsible() throws Exception { return scalar("SELECT " + LeadFollowUpResponsibilitySql.responsible("l") + " FROM zsjos_lead l WHERE id=1"); }
    private Long assignee() throws Exception { return scalar("SELECT " + LeadFollowUpResponsibilitySql.taskAssignee("t") + " FROM zsjos_business_task t WHERE id=1"); }
    private int calendar(long user) throws Exception {
        String sql=LeadCalendarSearchSql.due().replace("#{tenantId}","1").replace("#{userId}",Long.toString(user))
                .replace("#{start}","'2026-10-13 00:00:00'").replace("#{end}","'2026-10-14 00:00:00'");
        CCJSqlParserUtil.parse(sql);
        try(var s=db.createStatement();var r=s.executeQuery(sql)) { int count=0;while(r.next())count++;return count; }
    }
    @Test void calendarProjectsCurrentOwnershipTimeWithoutInventingMissingHistory() throws Exception {
        String sql = LeadCalendarSearchSql.due().replace("#{tenantId}", "1").replace("#{userId}", "10")
                .replace("#{start}", "'2026-10-13 00:00:00'").replace("#{end}", "'2026-10-14 00:00:00'");
        try (var s = db.createStatement(); var r = s.executeQuery(sql)) {
            assertTrue(r.next());
            assertNull(r.getTimestamp("ownershipStartedAt"));
        }
        exec("UPDATE zsjos_lead SET ownership_started_at='2026-10-12 08:00:00' WHERE id=1");
        try (var s = db.createStatement(); var r = s.executeQuery(sql)) {
            assertTrue(r.next());
            assertEquals(Timestamp.valueOf("2026-10-12 08:00:00"), r.getTimestamp("ownershipStartedAt"));
            assertEquals(Timestamp.valueOf("2026-10-13 12:00:00"), r.getTimestamp("deadline"));
            assertFalse(r.next());
        }
    }
    @Test void waitingPoolPausesOwnerTasksAndCalendar() throws Exception {
        pool("waiting_assignment","NULL"); assertNull(responsible()); assertNull(assignee());
        assertEquals(0,calendar(10)); assertEquals(0,calendar(20));
        assertEquals(10L,scalar("SELECT owner_user_id FROM zsjos_lead WHERE id=1"));
    }
    @Test void assignedPoolUsesBWithHistoricalTaskStillStoredUnderA() throws Exception {
        pool("assigned","20"); assertEquals(20L,responsible()); assertEquals(20L,assignee());
        assertEquals(0,calendar(10)); assertEquals(1,calendar(20));
        assertEquals(10L,scalar("SELECT assignee_id FROM zsjos_business_task WHERE id=1"));
    }
    @Test void reassignmentImmediatelyMovesResponsibilityAndExitRestoresOwner() throws Exception {
        pool("assigned","20"); exec("UPDATE zsjos_lead_public_sea_pool_cycle SET collaborator_user_id=30");
        assertEquals(0,calendar(20)); assertEquals(1,calendar(30));
        exec("UPDATE zsjos_lead_public_sea_pool_cycle SET status='exited'");
        assertEquals(10L,responsible()); assertEquals(1,calendar(10)); assertEquals(0,calendar(30));
    }
    @Test void completedAndCancelledTasksKeepTheirHistoricalAssignee() throws Exception {
        pool("assigned","20");
        for(String state:List.of("completed","cancelled")) {
            exec("UPDATE zsjos_business_task SET status='"+state+"'");
            assertEquals(10L,assignee()); assertEquals(0,calendar(20));
        }
    }
    @Test void legacyPoolAndOverlappingPoolAreHandledWithoutOwnerFallback() throws Exception {
        exec("INSERT INTO zsjos_lead_public_sea_pool_compatibility_record VALUES(1,1,1,0,10,20)");
        assertEquals(20L,responsible()); assertEquals(1,calendar(20));
        pool("assigned","30"); assertNull(responsible()); assertNull(assignee());
    }
    @Test void otherTenantAndDeletedCyclesCannotChangeResponsibility() throws Exception {
        pool("assigned","20"); exec("UPDATE zsjos_lead_public_sea_pool_cycle SET tenant_id=2");
        assertEquals(10L,responsible()); assertEquals(1,calendar(10));
        exec("UPDATE zsjos_lead_public_sea_pool_cycle SET tenant_id=1,deleted=1"); assertEquals(10L,responsible());
        exec("UPDATE zsjos_lead SET deleted=1"); assertNull(assignee()); assertEquals(0,calendar(10));
    }
    @Test void unrelatedAndHistoricalPendingDealSemanticsArePreserved() throws Exception {
        pool("deal_pending","20"); assertEquals(20L,responsible());
        exec("UPDATE zsjos_business_task SET task_type='lead_qualification'"); assertEquals(10L,assignee());
    }
    @Test void searchUsesSameResponsibilityAndTenantBoundary() throws Exception {
        pool("assigned","20");
        var req=new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.calendar.LeadCalendarSearchReqVO();
        req.setKeyword("fixture");
        String sql=LeadCalendarSearchSql.count(Map.of("req",req)).replace("#{tenantId}","1")
                .replace("#{userId}","20").replace("#{req.keyword}","'fixture'");
        assertEquals(1L,scalar(sql)); assertEquals(0L,scalar(sql.replace("=20","=10")));
    }

    @Test void filteredLeadListUsesBButUnfilteredOwnershipAndSubmissionStillUseA() throws Exception {
        pool("assigned","20");
        assertEquals(0L,leadCount(10,"following",null));
        assertEquals(1L,leadCount(20,"following",null));
        assertEquals(1L,leadCount(10,null,null));
        assertEquals(0L,leadCount(20,null,null));
        assertEquals(1L,leadCount(10,"following","submitted"));
        exec("UPDATE zsjos_lead_public_sea_pool_cycle SET status='waiting_assignment',collaborator_user_id=NULL");
        assertEquals(0L,leadCount(10,"following",null));
        assertEquals(0L,leadCount(20,"following",null));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private long leadCount(long user, String simpleStatus, String relationScope) throws Exception {
        return leadCount(user, simpleStatus, relationScope, null, null, List.of(user), List.of(user), false);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private long leadCount(long user, String simpleStatus, String relationScope, String audience,
            cn.iocoder.yudao.module.zsjos.service.lead.LeadInboxFilterQuery filter,
            List<Long> sources, List<Long> owners, boolean queryAll) throws Exception {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO.class);
        var mapper=org.mockito.Mockito.mock(LeadMapper.class,org.mockito.Mockito.CALLS_REAL_METHODS);
        var req=new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO();
        req.setSimpleStatus(simpleStatus); req.setRelationScope(relationScope); req.setAudience(audience);
        org.mockito.Mockito.doReturn(new cn.iocoder.yudao.framework.common.pojo.PageResult<>(List.of(),0L)).when(mapper)
                .selectPage(org.mockito.ArgumentMatchers.eq(req),org.mockito.ArgumentMatchers.any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        mapper.selectManagementPageByScope(req,sources,owners,queryAll,filter,null);
        var captor=org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        org.mockito.Mockito.verify(mapper).selectPage(org.mockito.ArgumentMatchers.eq(req),captor.capture());
        var query=(cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<?>)captor.getValue();
        String where=query.getExpression().getNormal().getSqlSegment();
        for(var e:query.getParamNameValuePairs().entrySet()) {
            Object v=e.getValue();
            where=where.replace("#{ew.paramNameValuePairs."+e.getKey()+"}", v instanceof Number ? v.toString() : "'"+v.toString().replace("'","''")+"'");
        }
        // H2 does not implement MySQL bit literals used by the existing opportunity predicate.
        return scalar("SELECT COUNT(*) FROM zsjos_lead WHERE tenant_id=1 AND deleted=0 AND "+where.replace("b'0'", "0"));
    }

    @Test void managementFiltersPreserveSourceAndOwnerVisibility() throws Exception {
        exec("UPDATE zsjos_lead SET provider_owner_id=30");
        pool("assigned", "20");
        for (var condition : List.of(Map.of("sales_progress", Set.of("following")),
                Map.of("follow_up_condition", Set.of("today")),
                Map.of("follow_up_condition", Set.of("overdue")))) {
            var due = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).atTime(12, 0);
            if (condition.getOrDefault("follow_up_condition", Set.of()).contains("overdue")) due = due.minusDays(1);
            exec("UPDATE zsjos_business_task SET due_at='" + due + "'");
            var filter = new cn.iocoder.yudao.module.zsjos.service.lead.LeadInboxFilterQuery(
                    Set.of(), Set.of(), false, condition);
            assertEquals(1L, leadCount(30, null, null, "management", filter, List.of(30L), List.of(), false));
            assertEquals(1L, leadCount(10, null, "all", "management", filter, List.of(), List.of(10L), false));
            assertEquals(0L, leadCount(20, null, null, "management", filter, List.of(20L), List.of(20L), false));
            assertEquals(0L, leadCount(30, null, null, "management", filter, List.of(), List.of(), false));
            assertEquals(1L, leadCount(30, null, null, "management", filter, List.of(), List.of(), true));
        }
    }

    @Test void providerBindsIdsAndTenantAndReturnsCurrentAssignee() throws Exception {
        pool("assigned","20");
        var config = new org.apache.ibatis.session.Configuration();
        var source = new org.apache.ibatis.scripting.xmltags.XMLLanguageDriver().createSqlSource(config,
                LeadFollowUpResponsibilitySql.taskAssignees(), Map.class);
        var sql = source.getBoundSql(Map.of("ids",List.of(1L),"tenantId",1L));
        CCJSqlParserUtil.parse(sql.getSql());
        try(var statement=db.prepareStatement(sql.getSql())) {
            statement.setLong(1,1); statement.setLong(2,1);
            try(var result=statement.executeQuery()) { assertTrue(result.next()); assertEquals(20L,result.getLong("assignee_id")); }
            statement.setLong(1,2);
            try(var result=statement.executeQuery()) { assertFalse(result.next()); }
        }
    }

    @Test void personalTaskCountsExcludeAAndWaitingCyclesButKeepDoneHistory() throws Exception {
        pool("assigned","20");
        assertEquals(0L, taskCount(10,"pending")); assertEquals(1L,taskCount(20,"pending"));
        exec("UPDATE zsjos_lead_public_sea_pool_cycle SET status='waiting_assignment',collaborator_user_id=NULL");
        assertEquals(0L,taskCount(10,"pending")); assertEquals(0L,taskCount(20,"pending"));
        exec("UPDATE zsjos_business_task SET status='completed'");
        assertEquals(1L,taskCount(10,"done")); assertEquals(0L,taskCount(20,"done"));
    }

    private long taskCount(long user, String status) throws Exception {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO.class);
        var method=cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper.class.getDeclaredMethod(
                "buildReadQuery",Long.class,String.class,String.class,java.time.LocalDateTime.class);
        method.setAccessible(true);
        var query=(cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<?>)method.invoke(null,user,status,null,java.time.LocalDateTime.now());
        String where=query.getSqlSegment();
        for(var e:query.getParamNameValuePairs().entrySet()) {
            Object v=e.getValue();
            where=where.replace("#{ew.paramNameValuePairs."+e.getKey()+"}",v instanceof Number?v.toString():"'"+v+"'");
        }
        String sql="SELECT COUNT(*) FROM zsjos_business_task WHERE tenant_id=1 AND deleted=0 AND "+where;
        CCJSqlParserUtil.parse(sql);
        return scalar(sql);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void pendingReassignmentVersionsOnlyLeadFollowUpTasks() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO.class);
        for (var types : List.of(List.of("lead_first_follow_up","lead_follow_up_reminder"),List.of("student_delivery_confirmation"))) {
            var mapper=org.mockito.Mockito.mock(cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper.class, org.mockito.Mockito.CALLS_REAL_METHODS);
            mapper.reassignPending(types,1L,20L);
            var captor=org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
            org.mockito.Mockito.verify(mapper).update(org.mockito.ArgumentMatchers.isNull(),captor.capture());
            var wrapper=(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?>)captor.getValue();
            boolean follow=types.contains("lead_follow_up_reminder");
            assertEquals(follow,wrapper.getSqlSet().contains("version = version + 1"));
            assertEquals(follow,wrapper.getSqlSegment().contains("biz_type ="));
            assertTrue(wrapper.getParamNameValuePairs().containsValue("pending"));
        }
    }
}

