package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderMyPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SalesOrderManagementKeywordTest {
    @Test
    void managementPageCursorAndCountApplyFormalOwnerBoundary() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SalesOrderDO.class);
        var mapper = mock(SalesOrderMapper.class, CALLS_REAL_METHODS);
        var captured = new AtomicReference<LambdaQueryWrapper<SalesOrderDO>>();
        doAnswer(call -> { captured.set(call.getArgument(0)); return List.of(); }).when(mapper).selectList(any(Wrapper.class));
        doAnswer(call -> { captured.set(call.getArgument(0)); return 0L; }).when(mapper).selectCount(any(Wrapper.class));
        var scope = new SalesOrderManagementScope(true, true, Set.of(), Set.of(), Set.of(30L));
        var req = new SalesOrderMyPageReqVO(); req.setPageSize(PageParam.PAGE_SIZE_NONE);
        mapper.selectManagementPage(scope, req, null);
        assertTrue(captured.get().getSqlSegment().contains("formal_sales_user_id IN"));
        mapper.selectManagementCursor(scope, null, null, null, null, null, 10);
        assertTrue(captured.get().getSqlSegment().contains("formal_sales_user_id IN"));
        mapper.selectManagementCount(scope, null);
        assertTrue(captured.get().getSqlSegment().contains("formal_sales_user_id IN"));
    }

    @Test
    void salesBoundaryKeepsActorsOwnSubmissionsWithSamePredicateForListAndCount() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SalesOrderDO.class);
        var mapper = mock(SalesOrderMapper.class, CALLS_REAL_METHODS);
        var captured = new AtomicReference<LambdaQueryWrapper<SalesOrderDO>>();
        doAnswer(call -> { captured.set(call.getArgument(0)); return List.of(); }).when(mapper).selectList(any(Wrapper.class));
        doAnswer(call -> { captured.set(call.getArgument(0)); return 0L; }).when(mapper).selectCount(any(Wrapper.class));
        var scope = new SalesOrderManagementScope(true, true, Set.of(), Set.of(), new SalesOrderBoundaryUserIds(30L, Set.of(30L, 31L)));
        mapper.selectManagementCursor(scope, null, null, null, null, null, 10);
        String list = captured.get().getSqlSegment();
        assertTrue(captured.get().getParamNameValuePairs().containsValue(30L));
        mapper.selectManagementCount(scope, null);
        String count = captured.get().getSqlSegment();
        for (String sql : List.of(list, count)) {
            assertTrue(sql.contains("formal_sales_user_id IN"), sql);
            assertTrue(sql.contains("OR submitter_user_id ="), sql);
        }
        // Plain id collections (non-sales callers) keep the formal-owner-only predicate.
        mapper.selectManagementCount(new SalesOrderManagementScope(true, true, Set.of(), Set.of(), Set.of(31L)), null);
        assertFalse(captured.get().getSqlSegment().contains("submitter_user_id"));
    }

    @Test
    void pageAndCursorSearchWechatWithinExistingScopeAndFilters() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SalesOrderDO.class);
        var mapper = mock(SalesOrderMapper.class, CALLS_REAL_METHODS);
        var captured = new AtomicReference<LambdaQueryWrapper<SalesOrderDO>>();
        doAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return List.of();
        }).when(mapper).selectList(any(Wrapper.class));
        var scope = new SalesOrderManagementScope(false, true, Set.of(), Set.of(10L));
        var request = new SalesOrderMyPageReqVO();
        request.setPageSize(PageParam.PAGE_SIZE_NONE);
        request.setKeyword("  wx_sample  ");
        request.setStatus("effective");
        mapper.selectManagementPage(scope, request, List.of(100L));
        assertKeywordAndScope(captured.get());

        mapper.selectManagementCursor(scope, "effective", "  wx_sample  ", List.of(100L),
                LocalDateTime.of(2026, 11, 12, 10, 0), 101L, 21);
        assertKeywordAndScope(captured.get());
        assertTrue(captured.get().getSqlSegment().contains("LIMIT 21"));
        assertTrue(captured.get().getSqlSegment().contains("update_time <"));

        request.setKeyword("   ");
        mapper.selectManagementPage(scope, request, null);
        assertFalse(captured.get().getSqlSegment().contains("LIKE"));
        mapper.selectManagementCursor(scope, null, null, null, null, null, 21);
        assertFalse(captured.get().getSqlSegment().contains("LIKE"));

        mapper.selectManagementCursor(scope, null, "missing", List.of(), null, null, 21);
        assertTrue(captured.get().getSqlSegment().contains("id ="));
        assertTrue(captured.get().getParamNameValuePairs().containsValue(-1L));
        clearInvocations(mapper);
        assertTrue(mapper.selectManagementPage(SalesOrderManagementScope.empty(), request, null).getList().isEmpty());
        assertTrue(mapper.selectManagementCursor(SalesOrderManagementScope.empty(), null, "wx", null, null, null, 21).isEmpty());
        verify(mapper, never()).selectList(any(Wrapper.class));
    }

    private void assertKeywordAndScope(LambdaQueryWrapper<SalesOrderDO> query) {
        String sql = query.getSqlSegment();
        assertTrue(sql.contains("(order_no LIKE"));
        assertTrue(sql.contains("OR student_name LIKE"));
        assertTrue(sql.contains("OR student_mobile LIKE"));
        assertTrue(sql.contains("OR student_wechat_id LIKE"));
        assertTrue(sql.contains("submitter_user_id IN"));
        assertTrue(sql.contains("status ="));
        assertTrue(sql.contains("id IN"));
        assertEquals(4, query.getParamNameValuePairs().values().stream().filter("%wx_sample%"::equals).count());
    }
}
