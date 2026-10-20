package cn.iocoder.yudao.module.zsjos.service.material;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialBrowseVisibilityTest {
    @Mock MaterialMapper materialMapper;
    @Mock MaterialVersionMapper versionMapper;
    @Mock MaterialTypeMapper typeMapper;
    @Mock MaterialFileMapper materialFileMapper;
    @Mock MaterialLikeMapper likeMapper;
    @Mock MaterialFavoriteMapper favoriteMapper;
    @Mock PermissionApi permissionApi;
    @Mock AdminUserApi adminUserApi;
    @InjectMocks MaterialService service;

    private MaterialDO published() {
        return new MaterialDO().setId(1L).setMaterialTypeId(2L).setOwnerUserId(7L)
                .setStatus("EFFECTIVE").setCurrentEffectiveVersionId(10L).setCurrentDraftVersionId(11L)
                .setTitle("Unpublished title").setSummary("Unpublished summary");
    }

    private MaterialVersionDO version(Long id, String status) {
        return new MaterialVersionDO().setId(id).setMaterialId(1L).setStatus(status)
                .setTitle(status).setSummary(status).setValuesJson("{}")
                .setFieldSnapshotJson("[]").setDictSnapshotJson("{}");
    }

    private void projection(MaterialVersionDO selected) {
        when(typeMapper.selectBatchIds(anyCollection())).thenReturn(List.of(new MaterialTypeDO().setId(2L).setStatus(0)));
        when(adminUserApi.getUserMap(Set.of(7L))).thenReturn(Map.of());
        when(versionMapper.selectBatchIds(Set.of(selected.getId()))).thenReturn(List.of(selected));
    }

    @Test void browseAlwaysSelectsPublishedVersionForOrdinaryUserOwnerAndAdministrator() {
        for (Long userId : List.of(6L, 7L, 9L)) {
            var request = new MaterialPageReqVO();
            when(materialMapper.selectPage(request, userId, false, true))
                    .thenReturn(new PageResult<>(List.of(published()), 1L));
            projection(version(10L, "EFFECTIVE"));
            var response = service.getBrowsePage(request, userId).getList().getFirst();
            assertEquals(10L, response.getCurrentVersion().getId());
            assertEquals("EFFECTIVE", response.getTitle());
            assertEquals("EFFECTIVE", response.getSummary());
            assertNull(response.getCurrentDraftVersionId());
            assertFalse(response.getAvailableActions().contains("UPDATE"));
        }
        verify(permissionApi, never()).hasTenantReadAllAccess(anyLong());
        verify(versionMapper, never()).selectById(11L);
    }

    @Test void managementStillSelectsDraftForAdministrator() {
        var request = new MaterialPageReqVO();
        when(permissionApi.hasTenantReadAllAccess(9L)).thenReturn(true);
        when(materialMapper.selectPage(request, 9L, true)).thenReturn(new PageResult<>(List.of(published()), 1L));
        projection(version(11L, "DRAFT"));
        assertEquals(11L, service.getPage(request, 9L).getList().getFirst().getCurrentVersion().getId());
    }

    @Test void unpublishedDisabledBrokenAndDisabledTypeCannotBeOpenedFromBrowse() {
        for (String status : List.of("DRAFT", "IN_APPROVAL", "REJECTED", "DISABLED")) {
            when(materialMapper.selectById(1L)).thenReturn(published().setStatus(status));
            when(versionMapper.selectById(10L)).thenReturn(version(10L, "EFFECTIVE"));
            when(typeMapper.selectById(2L)).thenReturn(new MaterialTypeDO().setId(2L).setStatus(0));
            assertThrows(RuntimeException.class, () -> service.getBrowse(1L, 9L));
            assertThrows(RuntimeException.class, () -> service.getBrowseVersions(1L, 9L));
        }
        when(materialMapper.selectById(1L)).thenReturn(published());
        when(versionMapper.selectById(10L)).thenReturn(version(10L, "DRAFT"));
        assertThrows(RuntimeException.class, () -> service.getBrowse(1L, 7L));
        when(versionMapper.selectById(10L)).thenReturn(version(10L, "EFFECTIVE"));
        when(typeMapper.selectById(2L)).thenReturn(new MaterialTypeDO().setId(2L).setStatus(1));
        assertThrows(RuntimeException.class, () -> service.getBrowse(1L, 9L));
    }

    @Test void browseDetailAndHistoryNeverSelectDraftEvenForOwner() {
        when(materialMapper.selectById(1L)).thenReturn(published());
        when(versionMapper.selectById(10L)).thenReturn(version(10L, "EFFECTIVE"));
        when(typeMapper.selectById(2L)).thenReturn(new MaterialTypeDO().setId(2L).setStatus(0));
        projection(version(10L, "EFFECTIVE"));
        assertEquals(10L, service.getBrowse(1L, 7L).getCurrentVersion().getId());
        when(versionMapper.selectByMaterialId(1L)).thenReturn(List.of(
                version(10L, "EFFECTIVE"), version(11L, "DRAFT"), version(12L, "IN_APPROVAL"), version(13L, "REJECTED")));
        assertEquals(List.of(10L), service.getBrowseVersions(1L, 7L).stream().map(v -> v.getId()).toList());
    }

    @Test void browseSqlEnforcesPublicationBeforePaginationAndPreservesPersonalAndFavoriteScope() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MaterialDO.class);
        MaterialMapper mapper = mock(MaterialMapper.class, CALLS_REAL_METHODS);
        doReturn(PageResult.empty()).when(mapper).selectPage(any(MaterialPageReqVO.class), any(LambdaQueryWrapperX.class));
        var request = new MaterialPageReqVO();
        request.setMine(true); request.setFavorite(true); request.setStatus("DRAFT");
        request.setVersionStatus("DRAFT"); request.setKeyword("secret");
        mapper.selectPage(request, 7L, true, true);
        ArgumentCaptor<LambdaQueryWrapperX> wrapper = ArgumentCaptor.forClass(LambdaQueryWrapperX.class);
        verify(mapper).selectPage(eq(request), wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment();
        assertTrue(sql.contains("mv.status='EFFECTIVE'"));
        assertTrue(sql.contains("mv.material_id=zsjos_material.id"));
        assertTrue(sql.contains("mv.tenant_id=zsjos_material.tenant_id"));
        assertTrue(sql.contains("mv.deleted=b'0'"));
        assertTrue(sql.contains("owner_user_id"));
        assertTrue(sql.contains("zsjos_material_favorite"));
        assertTrue(sql.contains("mv.title LIKE"));
        assertFalse(sql.contains("current_draft_version_id"));
        assertFalse(wrapper.getValue().getParamNameValuePairs().containsValue("DRAFT"));
    }
}
