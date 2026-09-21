package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.agingpool.LeadAgingPoolPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAgingPoolCycleDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LeadAgingPoolCycleMapperTest {
    @Test
    void personalFiltersApplyEvenWithManageAllScope() {
        var owned = query("owned", null, null, null);
        assertTrue(owned.getSqlSegment().contains("original_owner_user_id ="));
        assertFalse(owned.getSqlSegment().contains("collaborator_user_id ="));
        assertTrue(owned.getParamNameValuePairs().containsValue(42L));
        var following = query("following", null, null, null);
        assertTrue(following.getSqlSegment().contains("collaborator_user_id ="));
        assertFalse(following.getSqlSegment().contains("original_owner_user_id ="));
        assertTrue(following.getParamNameValuePairs().containsValue(42L));
    }

    @Test
    void personalFilterIntersectsVisibilityAndAdvancedResults() {
        var query = query("following", List.of(10L, 20L), 42L, List.of(99L));
        String sql = query.getSqlSegment();
        assertTrue(sql.contains("original_owner_user_id IN"));
        assertTrue(sql.contains(" OR "));
        assertTrue(sql.contains(") AND collaborator_user_id ="), sql);
        assertTrue(sql.contains("lead_id IN"));
        assertTrue(query.getParamNameValuePairs().containsValue(99L));
    }

    @Test
    void absentRelationPreservesAdminRequestAndEmptyAdvancedResultsRemainEmpty() {
        var all = query(null, null, null, null);
        assertFalse(all.getSqlSegment().contains("original_owner_user_id"));
        assertFalse(all.getSqlSegment().contains("collaborator_user_id"));
        var empty = query("owned", null, null, List.of());
        assertTrue(empty.getSqlSegment().contains("lead_id ="));
        assertTrue(empty.getParamNameValuePairs().containsValue(-1L));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private LambdaQueryWrapperX<LeadAgingPoolCycleDO> query(String relation, List<Long> owners,
                                                         Long participant, List<Long> matched) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), LeadAgingPoolCycleDO.class);
        LeadAgingPoolCycleMapper mapper = mock(LeadAgingPoolCycleMapper.class, CALLS_REAL_METHODS);
        doReturn(new PageResult<>(List.of(), 0L)).when(mapper).selectPage(any(PageParam.class), any(Wrapper.class));
        var req = new LeadAgingPoolPageReqVO();
        req.setRelationScope(relation);
        mapper.selectPage(req, owners, participant, List.of(), false, matched, 42L);
        ArgumentCaptor<LambdaQueryWrapperX> captor = ArgumentCaptor.forClass(LambdaQueryWrapperX.class);
        verify(mapper).selectPage(any(PageParam.class), captor.capture());
        return captor.getValue();
    }
}
