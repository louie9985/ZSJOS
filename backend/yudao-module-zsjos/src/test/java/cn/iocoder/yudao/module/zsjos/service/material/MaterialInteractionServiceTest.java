package cn.iocoder.yudao.module.zsjos.service.material;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialReferenceFieldReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialReferenceReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialLikeDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialFavoriteDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialReferenceDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentVersionFileMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentVersionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialFavoriteMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialFileMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialLikeMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialReferenceMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialVersionMapper;
import cn.iocoder.yudao.module.zsjos.service.content.ContentObjectPermissionProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.MATERIAL_EFFECTIVE;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MATERIAL_REFERENCE_CONFLICT;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MATERIAL_FAVORITE_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialInteractionServiceTest {

    @InjectMocks private MaterialInteractionService service;
    @Mock private MaterialService materialService;
    @Mock private MaterialMapper materialMapper;
    @Mock private MaterialVersionMapper versionMapper;
    @Mock private MaterialLikeMapper likeMapper;
    @Mock private MaterialFavoriteMapper favoriteMapper;
    @Mock private MaterialReferenceMapper referenceMapper;
    @Mock private MaterialFileMapper fileMapper;
    @Mock private ContentMapper contentMapper;
    @Mock private ContentVersionMapper contentVersionMapper;
    @Mock private ContentVersionFileMapper contentVersionFileMapper;
    @Mock private ContentObjectPermissionProvider contentPermissionProvider;
    @Mock private MaterialSchemaService schemaService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void referenceReplaySucceedsBeforeTargetDraftIsRevalidated() {
        MaterialReferenceReqVO request = referenceRequest("reference-1");
        MaterialReferenceDO replay = new MaterialReferenceDO().setMaterialVersionId(20L)
                .setTargetContentVersionId(30L).setCopiedFieldsJson(JsonUtils.toJsonString(request.getFields()))
                .setIdempotencyKey("reference-1").setReferencedByUserId(7L);
        when(referenceMapper.selectByIdempotencyKey("reference-1")).thenReturn(replay);
        when(versionMapper.selectById(20L)).thenReturn(
                new cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialVersionDO()
                        .setId(20L).setMaterialId(10L));

        service.reference(10L, 20L, request, 7L);

        verifyNoInteractions(materialService, contentMapper, contentVersionMapper);
    }

    @Test
    void referenceReplayRejectsReuseOfIdempotencyKeyForAnotherTarget() {
        MaterialReferenceReqVO request = referenceRequest("reference-1");
        MaterialReferenceDO replay = new MaterialReferenceDO().setMaterialVersionId(20L)
                .setTargetContentVersionId(99L).setCopiedFieldsJson(JsonUtils.toJsonString(request.getFields()))
                .setIdempotencyKey("reference-1").setReferencedByUserId(7L);
        when(referenceMapper.selectByIdempotencyKey("reference-1")).thenReturn(replay);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.reference(10L, 20L, request, 7L));

        assertEquals(MATERIAL_REFERENCE_CONFLICT.getCode(), error.getCode());
        verifyNoInteractions(materialService);
    }

    @Test
    void referenceReplayRejectsDifferentCallerEvenWhenPayloadMatches() {
        MaterialReferenceReqVO request = referenceRequest("reference-1");
        MaterialReferenceDO replay = new MaterialReferenceDO().setMaterialVersionId(20L)
                .setTargetContentVersionId(30L).setCopiedFieldsJson(JsonUtils.toJsonString(request.getFields()))
                .setIdempotencyKey("reference-1").setReferencedByUserId(8L);
        when(referenceMapper.selectByIdempotencyKey("reference-1")).thenReturn(replay);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.reference(10L, 20L, request, 7L));

        assertEquals(MATERIAL_REFERENCE_CONFLICT.getCode(), error.getCode());
        verifyNoInteractions(materialService, versionMapper);
    }

    @Test
    void referenceReplayRejectsMaterialPathThatDoesNotOwnTheStoredVersion() {
        MaterialReferenceReqVO request = referenceRequest("reference-1");
        MaterialReferenceDO replay = new MaterialReferenceDO().setMaterialVersionId(20L)
                .setTargetContentVersionId(30L).setCopiedFieldsJson(JsonUtils.toJsonString(request.getFields()))
                .setIdempotencyKey("reference-1").setReferencedByUserId(7L);
        when(referenceMapper.selectByIdempotencyKey("reference-1")).thenReturn(replay);
        when(versionMapper.selectById(20L)).thenReturn(
                new cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialVersionDO()
                        .setId(20L).setMaterialId(99L));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.reference(10L, 20L, request, 7L));

        assertEquals(MATERIAL_REFERENCE_CONFLICT.getCode(), error.getCode());
        verifyNoInteractions(materialService);
    }

    @Test
    void referenceRejectsDifferentFieldMappingForExistingTarget() {
        MaterialReferenceReqVO request = referenceRequest("reference-2");
        MaterialReferenceDO existing = new MaterialReferenceDO().setMaterialVersionId(20L)
                .setTargetContentVersionId(30L).setCopiedFieldsJson("[]")
                .setIdempotencyKey("reference-1").setReferencedByUserId(7L);
        when(referenceMapper.selectByTarget(20L, 30L)).thenReturn(existing);
        when(materialService.lockMaterial(10L)).thenReturn(effectiveMaterial());
        when(versionMapper.selectByIdForUpdate(20L, 1L)).thenReturn(
                new cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialVersionDO()
                        .setId(20L).setMaterialId(10L).setStatus("EFFECTIVE"));
        when(contentVersionMapper.selectById(30L)).thenReturn(
                new cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentVersionDO()
                        .setId(30L).setContentId(40L));
        when(contentMapper.selectByIdForUpdate(40L, 1L)).thenReturn(
                new cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentDO()
                        .setId(40L).setCurrentVersionNo(1).setStatus("topic"));
        when(contentPermissionProvider.hasPermission(40L, "version-create", 7L)).thenReturn(true);
        when(contentVersionMapper.selectByIdForUpdate(30L, 1L)).thenReturn(
                new cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentVersionDO()
                        .setId(30L).setContentId(40L).setVersionNo(1));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.reference(10L, 20L, request, 7L));

        assertEquals(MATERIAL_REFERENCE_CONFLICT.getCode(), error.getCode());
    }

    @Test
    void toggleLikeCreatesUserDetailAndIncrementsAggregateCount() {
        MaterialDO material = effectiveMaterial().setLikeCount(4L);
        when(materialService.lockMaterial(10L)).thenReturn(material);
        when(materialMapper.incrementLike(10L, 1)).thenReturn(1);

        var result = service.toggleLike(10L, 7L);

        assertTrue(result.getActive());
        assertEquals(5L, result.getCount());
        verify(likeMapper).insert(any(MaterialLikeDO.class));
    }

    @Test
    void toggleLikeCanCancelWithoutMakingAggregateNegative() {
        MaterialDO material = effectiveMaterial().setLikeCount(0L);
        MaterialLikeDO existing = new MaterialLikeDO().setId(1L).setMaterialId(10L).setUserId(7L)
                .setActive(true).setVersion(0);
        when(materialService.lockMaterial(10L)).thenReturn(material);
        when(likeMapper.selectForUpdate(10L, 7L, 1L)).thenReturn(existing);
        when(likeMapper.updateById(existing)).thenReturn(1);
        when(materialMapper.incrementLike(10L, -1)).thenReturn(1);

        var result = service.toggleLike(10L, 7L);

        assertFalse(result.getActive());
        assertEquals(0L, result.getCount());
        verify(likeMapper).updateById(existing);
    }

    @Test
    void toggleFavoriteUsesFavoriteConflictWhenAggregateUpdateFails() {
        MaterialDO material = effectiveMaterial().setFavoriteCount(2L);
        MaterialFavoriteDO existing = new MaterialFavoriteDO().setId(1L).setMaterialId(10L)
                .setUserId(7L).setActive(true).setVersion(0);
        when(materialService.lockMaterial(10L)).thenReturn(material);
        when(favoriteMapper.selectForUpdate(10L, 7L, 1L)).thenReturn(existing);
        when(favoriteMapper.updateById(existing)).thenReturn(1);
        when(materialMapper.incrementFavorite(10L, -1)).thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.toggleFavorite(10L, 7L));

        assertEquals(MATERIAL_FAVORITE_CONFLICT.getCode(), error.getCode());
    }

    private MaterialReferenceReqVO referenceRequest(String idempotencyKey) {
        return new MaterialReferenceReqVO().setTargetContentVersionId(30L).setIdempotencyKey(idempotencyKey)
                .setFields(List.of(new MaterialReferenceFieldReqVO().setSourceField("title")
                        .setTargetField("titleSnapshot").setAction("REPLACE")));
    }

    private MaterialDO effectiveMaterial() {
        return new MaterialDO().setId(10L).setStatus(MATERIAL_EFFECTIVE).setCurrentEffectiveVersionId(20L);
    }
}
