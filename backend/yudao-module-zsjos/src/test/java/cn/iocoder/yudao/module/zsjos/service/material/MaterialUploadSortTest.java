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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MaterialUploadSortTest {

    @Test
    void uploadOrderAppliesBeforePaginationForAllAndMineIncludingManagers() {
        for (boolean mine : new boolean[]{false, true}) {
            for (boolean canManage : new boolean[]{false, true}) {
                for (int page : new int[]{1, 2}) {
                    var request = new MaterialPageReqVO();
                    request.setMine(mine);
                    request.setSortBy("createTime");
                    request.setPageNo(page);
                    request.setPageSize(20);
                    var query = captureQuery(request, canManage);
                    String sql = query.getSqlSegment();
                    assertEquals("ORDER BY create_time DESC,id DESC", sql.substring(sql.indexOf("ORDER BY")));
                    if (mine) {
                        assertTrue(sql.contains("owner_user_id"));
                        assertTrue(query.getParamNameValuePairs().containsValue(7L));
                    } else if (!canManage) {
                        assertTrue(sql.contains("current_effective_version_id IS NOT NULL"));
                        assertTrue(query.getParamNameValuePairs().containsValue("EFFECTIVE"));
                    }
                }
            }
        }
    }

    @Test
    void omittedSortPreservesAdminFavoritesAndRecommendationOrdering() {
        for (String consumer : new String[]{"admin", "favorite", "recommendation"}) {
            var request = new MaterialPageReqVO();
            request.setFavorite("favorite".equals(consumer));
            request.setRecommendation("recommendation".equals(consumer));
            String sql = captureQuery(request, "admin".equals(consumer)).getSqlSegment();
            assertEquals("ORDER BY pinned DESC,priority DESC,update_time DESC,id DESC",
                    sql.substring(sql.indexOf("ORDER BY")));
        }
    }

    @Test
    void unsupportedSortValuesAreRejectedByRequestValidation() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var request = new MaterialPageReqVO();
            request.setSortBy("create_time DESC");
            assertTrue(factory.getValidator().validate(request).stream()
                    .anyMatch(error -> error.getPropertyPath().toString().equals("sortBy")));
            request.setSortBy("createTime");
            assertTrue(factory.getValidator().validate(request).isEmpty());
            request.setSortBy(null);
            assertTrue(factory.getValidator().validate(request).isEmpty());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private LambdaQueryWrapperX<MaterialDO> captureQuery(MaterialPageReqVO request, boolean canManage) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MaterialDO.class);
        MaterialMapper mapper = mock(MaterialMapper.class, CALLS_REAL_METHODS);
        doReturn(PageResult.empty()).when(mapper).selectPage(any(MaterialPageReqVO.class), any(LambdaQueryWrapperX.class));
        mapper.selectPage(request, 7L, canManage);
        ArgumentCaptor<LambdaQueryWrapperX> wrapper = ArgumentCaptor.forClass(LambdaQueryWrapperX.class);
        verify(mapper).selectPage(eq(request), wrapper.capture());
        return wrapper.getValue();
    }
}
