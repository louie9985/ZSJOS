package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentVersionMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContentReviewFrozenVersionSqlTest {
    @Test
    void currentListHidesAncestorsButKeepsTenantIsolationAndIgnoresDeletedSuccessors() throws Exception {
        var type = cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchDO.class;
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "review-batch"), type);
        var mapper = mock(cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchMapper.class, CALLS_REAL_METHODS);
        var request = new cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewBatchPageReqVO();
        java.util.concurrent.atomic.AtomicReference<Wrapper<?>> captured = new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(call -> { captured.set(call.getArgument(1)); return cn.iocoder.yudao.framework.common.pojo.PageResult.empty(); })
                .when(mapper).selectPage(eq(request), any(Wrapper.class));
        mapper.selectPage(request, 7L, true, java.util.List.of());
        try (var c = DriverManager.getConnection("jdbc:h2:mem:list" + UUID.randomUUID() + ";MODE=MySQL", "sa", "")) {
            try (var st = c.createStatement()) {
                st.execute("CREATE TABLE zsjos_content_review_batch(id bigint, tenant_id bigint, revision_of_batch_id bigint, deleted binary(1))");
                st.execute("INSERT INTO zsjos_content_review_batch VALUES(1,1,null,X'00'),(2,1,1,X'00'),(3,1,null,X'00'),(4,2,3,X'00'),(5,1,null,X'00'),(6,1,5,X'01')");
                // H2 uses hex binary literals; the application SQL is MySQL b'0'.
                String sql = ("SELECT id FROM zsjos_content_review_batch " + captured.get().getCustomSqlSegment())
                        .replace("b'0'", "X'00'").replace("WHERE ", "WHERE tenant_id=1 AND deleted=X'00' AND ");
                // Add tenant predicate only to the outer query; nested relation already correlates tenant_id.
                sql = sql.replace("SELECT 1 FROM zsjos_content_review_batch successor WHERE tenant_id=1 AND deleted=X'00' AND ", "SELECT 1 FROM zsjos_content_review_batch successor WHERE ");
                var ids = new ArrayList<Long>();
                try (var rows = st.executeQuery(sql)) { while (rows.next()) ids.add(rows.getLong(1)); }
                assertEquals(java.util.List.of(5L, 3L, 2L), ids);
            }
        }
    }

    @Test
    void rejectionPersistsOnceAndPreservesHistoryFreeze() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "review-version"), ContentVersionDO.class);
        ContentVersionMapper mapper = mock(ContentVersionMapper.class, CALLS_REAL_METHODS);
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:review" + UUID.randomUUID() + ";MODE=MySQL", "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE zsjos_content_version(id bigint, frozen_at timestamp, review_decision varchar, review_comment varchar, reviewed_by_user_id bigint, reviewed_at timestamp)");
                statement.execute("INSERT INTO zsjos_content_version(id,frozen_at) VALUES(1,CURRENT_TIMESTAMP),(2,CURRENT_TIMESTAMP)");
            }
            doAnswer(invocation -> {
                LambdaUpdateWrapper<ContentVersionDO> wrapper = invocation.getArgument(1);
                String raw = "UPDATE zsjos_content_version SET " + wrapper.getSqlSet() + " " + wrapper.getCustomSqlSegment();
                var matcher = Pattern.compile("#\\{ew.paramNameValuePairs.([^}]+)}").matcher(raw);
                var values = new ArrayList<Object>();
                while (matcher.find()) values.add(wrapper.getParamNameValuePairs().get(matcher.group(1)));
                try (var statement = connection.prepareStatement(matcher.replaceAll("?"))) {
                    for (int i = 0; i < values.size(); i++) statement.setObject(i + 1, values.get(i));
                    return statement.executeUpdate();
                }
            }).when(mapper).update(isNull(), any(Wrapper.class));
            assertEquals(1, mapper.finishReview(1L, "rejected", "退回运营修改", 9L, LocalDateTime.now()));
            assertEquals(0, mapper.finishReview(1L, "approved", "重复回调", 9L, LocalDateTime.now()));
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT frozen_at,review_decision,review_comment FROM zsjos_content_version WHERE id=1")) {
                assertTrue(rows.next()); assertNotNull(rows.getTimestamp(1));
                assertEquals("rejected", rows.getString(2)); assertEquals("退回运营修改", rows.getString(3));
            }
            // This reproduces why the removed unfreeze-before-finish sequence could never succeed.
            assertEquals(1, mapper.unfreeze(2L));
            assertEquals(0, mapper.finishReview(2L, "rejected", "退回", 9L, LocalDateTime.now()));
        }
    }
}
