package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewBatchPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchMapper;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL + actual tenant/pagination interceptors. Read-only CTE fixtures shadow table names; no DDL/DML. */
@EnabledIfEnvironmentVariable(named = "ZSJOS_REVIEW_QUERY_JDBC_URL", matches = ".+")
class ContentReviewQueryMysqlTest {
    private static final String FIXTURES = """
        WITH zsjos_content_review_batch AS (
          SELECT f.*, NULL AS account_ids_json, NULL AS relation_snapshot_json,
            'DIRECTOR' AS current_stage, NULL AS process_definition_id, 'review' AS process_definition_key,
            1 AS process_definition_version, NULL AS business_key, NULL AS last_event_key,
            NULL AS director_completed_at, NULL AS final_completed_at, NULL AS finalized_at,
            1 AS version, 'test' AS creator, 'test' AS updater, NULL AS create_time, NULL AS update_time
          FROM JSON_TABLE('[
            {"id":1,"tenant":1,"no":"CR-ONE","student":11,"account":101,"operator":10,"director":20,"status":"DIRECTOR_REVIEW","date":"2026-09-22 23:59:59","context":{"accountSnapshots":[{"id":101,"nickname":"晨间账号","platformValue":"dy","ownerOperatorUserId":10,"directorUserId":20},{"id":102,"nickname":"厨房账号","platformValue":"xhs","ownerOperatorUserId":30,"directorUserId":40}]}},
            {"id":2,"tenant":2,"no":"CR-OTHER","student":12,"account":201,"operator":10,"director":20,"status":"DIRECTOR_REVIEW","date":"2026-09-22 12:00:00","context":{"accountSnapshots":[{"id":201,"nickname":"跨租户账号","platformValue":"dy","ownerOperatorUserId":10,"directorUserId":20}]}},
            {"id":3,"tenant":1,"no":"CR-LEGACY","student":11,"account":103,"operator":77,"director":66,"status":"FINAL_REVIEW","date":"2026-09-21 12:00:00","process":"task-visible","context":{"account":{"id":103,"nickname":"历史账号","platformValue":"dy"}}},
            {"id":4,"tenant":1,"no":"CR-OLD","student":11,"account":101,"operator":10,"director":20,"status":"NEED_MODIFY","context":{}},
            {"id":5,"tenant":1,"no":"CR-NEW","student":11,"account":101,"operator":10,"director":20,"revision":4,"status":"DRAFT","context":{}}
          ]', '$[*]' COLUMNS(id BIGINT PATH '$.id', tenant_id BIGINT PATH '$.tenant',
            batch_no VARCHAR(64) PATH '$.no', student_person_id BIGINT PATH '$.student',
            account_id BIGINT PATH '$.account', operator_user_id BIGINT PATH '$.operator',
            director_user_id BIGINT PATH '$.director', status VARCHAR(32) PATH '$.status',
            submitted_at DATETIME PATH '$.date', process_instance_id VARCHAR(64) PATH '$.process',
            revision_of_batch_id BIGINT PATH '$.revision', deleted INT PATH '$.deleted' DEFAULT '0' ON EMPTY,
            context_snapshot_json JSON PATH '$.context')) f
        ), zsjos_person AS (
          SELECT 11 AS id, 1 AS tenant_id, 0 AS deleted, '示例学员' AS name
          UNION ALL SELECT 12,2,0,'跨租户学员'
        ), zsjos_content_review_batch_item AS (
          SELECT 1 AS batch_id, 1 AS tenant_id, 0 AS deleted,
            JSON_OBJECT('title','早餐标题','topic','晨间选题','scriptText','正文搜索特别词') AS content_snapshot_json,
            55 AS director_reviewed_by_user_id, NULL AS final_reviewed_by_user_id
        )
        """;

    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    static class ReadOnlyFixtures implements Interceptor {
        @Override public Object intercept(Invocation invocation) throws Throwable {
            var handler = (StatementHandler) invocation.getTarget();
            String sql = handler.getBoundSql().getSql();
            assertTrue(sql.stripLeading().toUpperCase().startsWith("SELECT"), "Fixture connection only allows SELECT");
            SystemMetaObject.forObject(handler.getBoundSql()).setValue("sql", FIXTURES + sql);
            return invocation.proceed();
        }
    }

    @Test void filtersSearchScopeAndPaginationExecuteOnMysql() throws Exception {
        var source = new DriverManagerDataSource(System.getenv("ZSJOS_REVIEW_QUERY_JDBC_URL"),
                System.getenv("ZSJOS_REVIEW_QUERY_USER"), System.getenv("ZSJOS_REVIEW_QUERY_PASSWORD"));
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("read-only-fixture", new JdbcTransactionFactory(), source));
        var plugins = new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override public Expression getTenantId() { return new LongValue(TenantContextHolder.getRequiredTenantId()); }
        }));
        plugins.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        config.addInterceptor(plugins); config.addInterceptor(new ReadOnlyFixtures());
        config.addMapper(ContentReviewBatchMapper.class);
        TenantContextHolder.setTenantId(1L);
        try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession()) {
            var mapper = session.getMapper(ContentReviewBatchMapper.class);
            var request = new ContentReviewBatchPageReqVO(); request.setPageNo(1); request.setPageSize(20);
            assertEquals(List.of(5L, 1L), ids(mapper, request, 10L, false, List.of()));
            assertEquals(List.of(5L, 3L, 1L), ids(mapper, request, 10L, true, List.of()));
            assertEquals(List.of(1L), ids(mapper, request, 55L, false, List.of()));
            assertEquals(List.of(3L), ids(mapper, request, 99L, false, List.of("task-visible")));
            assertEquals(List.of(), ids(mapper, request, 99L, false, List.of()));
            request.setStatuses(List.of("DIRECTOR_REVIEW", "FINAL_REVIEW"));
            for (String keyword : List.of("示例学员", "晨间账号", "早餐标题", "晨间选题", "正文搜索特别词", "cr-one")) {
                request.setKeyword(keyword); assertEquals(List.of(1L), ids(mapper, request, 10L, false, List.of()));
            }
            request.setKeyword("%_'"); assertEquals(List.of(), ids(mapper, request, 10L, true, List.of()));
            request.setKeyword(null); request.setOperatorUserId(10L); request.setPlatformValue("xhs");
            assertEquals(List.of(), ids(mapper, request, 10L, true, List.of()));
            request.setOperatorUserId(30L); request.setDirectorUserId(40L);
            assertEquals(List.of(1L), ids(mapper, request, 10L, true, List.of()));
            request.setOperatorUserId(77L); request.setDirectorUserId(66L); request.setPlatformValue("dy");
            assertEquals(List.of(3L), ids(mapper, request, 10L, true, List.of()));
            request.setOperatorUserId(null); request.setDirectorUserId(null); request.setPlatformValue(null);
            request.setSubmittedFrom(LocalDate.of(2026,9,22)); request.setSubmittedTo(LocalDate.of(2026,9,22));
            assertEquals(List.of(1L), ids(mapper, request, 10L, true, List.of()));
            request.setSubmittedFrom(null); request.setSubmittedTo(null); request.setPageSize(1);
            var first = mapper.selectPage(request, 10L, true, List.of());
            assertEquals(2, first.getTotal()); assertEquals(3L, first.getList().getFirst().getId());
            request.setPageNo(2); assertEquals(List.of(1L), ids(mapper, request, 10L, true, List.of()));
            request.setPageNo(1); request.setMine(true); assertEquals(List.of(1L), ids(mapper, request, 10L, true, List.of()));
        } finally { TenantContextHolder.clear(); }
    }
    private List<Long> ids(ContentReviewBatchMapper mapper, ContentReviewBatchPageReqVO request, Long user, boolean all, List<String> tasks) {
        return mapper.selectPage(request, user, all, tasks).getList().stream().map(ContentReviewBatchDO::getId).toList();
    }
}
