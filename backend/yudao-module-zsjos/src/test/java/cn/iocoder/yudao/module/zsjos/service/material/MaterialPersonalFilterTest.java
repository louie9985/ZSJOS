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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MaterialPersonalFilterTest {
    @ParameterizedTest
    @CsvSource({"true,false,false,true", "true,false,true,true", "true,true,true,false", "false,true,false,false", "false,false,true,false"})
    void adaptationFiltersFollowPersonalRevisionOnly(boolean mine, boolean browse, boolean manager, boolean revision) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MaterialDO.class);
        MaterialMapper mapper = mock(MaterialMapper.class, CALLS_REAL_METHODS);
        doReturn(PageResult.empty()).when(mapper).selectPage(any(MaterialPageReqVO.class), any(LambdaQueryWrapperX.class));
        var request = new MaterialPageReqVO();
        request.setMine(mine);
        request.setFavorite(true);
        request.setAccountType("persona-a");
        request.setProfession("profession-a");
        request.setAccountStage("stage-a");
        mapper.selectPage(request, 7L, manager, browse);
        ArgumentCaptor<LambdaQueryWrapperX> wrapper = ArgumentCaptor.forClass(LambdaQueryWrapperX.class);
        verify(mapper).selectPage(eq(request), wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment();
        String version = revision
                ? "COALESCE(zsjos_material.current_draft_version_id,zsjos_material.current_effective_version_id)"
                : "zsjos_material.current_effective_version_id";
        assertEquals(3, sql.split("d.material_version_id=" + java.util.regex.Pattern.quote(version), -1).length - 1);
        assertEquals(3, sql.split("d.tenant_id=zsjos_material.tenant_id", -1).length - 1);
        assertEquals(3, sql.split("OR d.unlimited=b'1'", -1).length - 1);
        assertEquals(3, sql.split("d.deleted=b'0'", -1).length - 1);
        assertEquals(revision, sql.contains("current_draft_version_id"));
        assertTrue(sql.contains("zsjos_material_favorite"));
        if (mine) assertTrue(sql.contains("owner_user_id"));
        for (String value : java.util.List.of("account_type", "profession", "account_stage", "persona-a", "profession-a", "stage-a")) {
            assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue(value));
        }
    }

    @Test void absentFiltersPreserveAdminAndPersonalQueryContract() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MaterialDO.class);
        for (boolean mine : new boolean[]{false, true}) {
            MaterialMapper mapper = mock(MaterialMapper.class, CALLS_REAL_METHODS);
            doReturn(PageResult.empty()).when(mapper).selectPage(any(MaterialPageReqVO.class), any(LambdaQueryWrapperX.class));
            var request = new MaterialPageReqVO();
            request.setMine(mine);
            mapper.selectPage(request, 7L, true);
            ArgumentCaptor<LambdaQueryWrapperX> wrapper = ArgumentCaptor.forClass(LambdaQueryWrapperX.class);
            verify(mapper).selectPage(eq(request), wrapper.capture());
            assertFalse(wrapper.getValue().getSqlSegment().contains("zsjos_material_dimension"));
        }
    }

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
    @ParameterizedTest @CsvSource({"true,false", "false,false", "true,true"})
    void keywordUsesPersonalRevisionWithoutChangingAdminOrPublicSearch(boolean mine, boolean browse) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MaterialDO.class);
        MaterialMapper mapper=mock(MaterialMapper.class,CALLS_REAL_METHODS);
        doReturn(PageResult.empty()).when(mapper).selectPage(any(MaterialPageReqVO.class),any(LambdaQueryWrapperX.class));
        var req=new MaterialPageReqVO();req.setMine(mine);req.setKeyword("draft-only");mapper.selectPage(req,7L,true,browse);
        ArgumentCaptor<LambdaQueryWrapperX> wrapper=ArgumentCaptor.forClass(LambdaQueryWrapperX.class);
        verify(mapper).selectPage(eq(req),wrapper.capture());String sql=wrapper.getValue().getSqlSegment();
        assertEquals(mine&&!browse,sql.contains("COALESCE(zsjos_material.current_draft_version_id"));
        if(!mine&&!browse) assertTrue(sql.contains(" OR title LIKE "));
        if(mine&&!browse) assertTrue(sql.contains("mv.material_id=zsjos_material.id"));
    }

}
