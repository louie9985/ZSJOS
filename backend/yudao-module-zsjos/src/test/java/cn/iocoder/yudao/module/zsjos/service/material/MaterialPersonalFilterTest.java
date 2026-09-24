package cn.iocoder.yudao.module.zsjos.service.material;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MaterialPersonalFilterTest {
    @Test void personalReviewFilterUsesRevisionAndRetainsOwnerTenantAndDeletionPredicates() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MaterialDO.class);
        MaterialMapper mapper = mock(MaterialMapper.class, CALLS_REAL_METHODS);
        doReturn(PageResult.empty()).when(mapper).selectPage(any(MaterialPageReqVO.class), any(LambdaQueryWrapperX.class));
        var request = new MaterialPageReqVO();
        request.setMine(true); request.setVersionStatus("IN_APPROVAL");
        mapper.selectPage(request, 7L, false);
        ArgumentCaptor<LambdaQueryWrapperX> wrapper = ArgumentCaptor.forClass(LambdaQueryWrapperX.class);
        verify(mapper).selectPage(eq(request), wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment();
        assertTrue(sql.contains("owner_user_id"));
        assertTrue(sql.contains("COALESCE(zsjos_material.current_draft_version_id,zsjos_material.current_effective_version_id)"));
        assertTrue(sql.contains("mv.tenant_id=zsjos_material.tenant_id"));
        assertTrue(sql.contains("mv.deleted=b'0'"));
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue("IN_APPROVAL"));
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue(7L));
    }
}
