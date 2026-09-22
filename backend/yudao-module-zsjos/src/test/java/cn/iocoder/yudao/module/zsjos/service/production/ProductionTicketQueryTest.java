package cn.iocoder.yudao.module.zsjos.service.production;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.production.vo.ProductionTicketPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.production.ProductionTicketDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.production.ProductionTicketMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProductionTicketQueryTest {
    @Test
    void statusAndDeadlineFiltersStayInsideTheExistingScopeAndPagination() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "ticket-test"), ProductionTicketDO.class);
        var mapper = mock(ProductionTicketMapper.class, CALLS_REAL_METHODS);
        var request = new ProductionTicketPageReqVO();
        request.setPageNo(2); request.setPageSize(20); request.setStatusGroup("todo");
        request.setDeadlineFrom(LocalDateTime.of(2026, 9, 22, 0, 0));
        request.setDeadlineTo(LocalDateTime.of(2026, 9, 22, 23, 59, 59));
        doAnswer(invocation -> {
            assertSame(request, invocation.getArgument(0));
            Wrapper<?> wrapper = invocation.getArgument(1);
            String sql = wrapper.getSqlSegment();
            assertTrue(sql.contains("owner_operator_user_id"));
            assertTrue(sql.contains("assignee_filming_editor_user_id"));
            assertTrue(sql.contains("reviewer_user_id"));
            assertTrue(sql.contains("wo.tenant_id = zsjos_production_ticket.tenant_id"));
            assertTrue(sql.contains("wo.deleted = 0"));
            var values = ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().values();
            assertTrue(values.containsAll(List.of("pending_accept", "accepted", "rejected", 20L, request.getDeadlineFrom(), request.getDeadlineTo())));
            return new PageResult<ProductionTicketDO>(List.of(), 47L);
        }).when(mapper).selectPage(any(PageParam.class), any(Wrapper.class));
        assertEquals(47L, mapper.selectPage(request, List.of(20L), false).getTotal());
        assertEquals(List.of("submitted", "checking"), group("review"));
        assertEquals(List.of("in_production"), group("producing"));
        assertEquals(List.of("completed"), group("completed"));
        assertTrue(group("all").isEmpty());
    }
    private List<String> group(String key) {
        var request = new ProductionTicketPageReqVO(); request.setStatusGroup(key); return request.groupedStatuses();
    }
}
