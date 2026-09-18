package cn.iocoder.yudao.module.zsjos.service.material;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialSchemaVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialTypeDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialSchemaVersionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialTypeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialTypeServiceImplTest {
    @InjectMocks private MaterialTypeServiceImpl service;
    @Mock private MaterialTypeMapper typeMapper;
    @Mock private MaterialSchemaVersionMapper schemaMapper;

    private MaterialTypeDO type(String code, Long pointer) {
        MaterialTypeDO type = new MaterialTypeDO();
        type.setId(10L);
        type.setCode(code);
        type.setVersion(3);
        type.setCurrentSchemaVersionId(pointer);
        return type;
    }

    private MaterialSchemaVersionDO schema(long id, String status) {
        MaterialSchemaVersionDO schema = new MaterialSchemaVersionDO();
        schema.setId(id);
        schema.setStatus(status);
        return schema;
    }

    private void initialize(MaterialTypeDO type) {
        ReflectionTestUtils.invokeMethod(service, "ensureDefaultSchema", type);
    }

    @Test
    void preservesExistingTemplate() {
        MaterialTypeDO type = type("viral_account", 20L);
        when(schemaMapper.selectById(20L)).thenReturn(schema(20L, "PUBLISHED"));
        initialize(type);
        verifyNoInteractions(typeMapper);
        verify(schemaMapper, never()).selectListByTypeId(any());
    }

    @Test
    void repairsMissingReferenceUsingPublishedVersionInsteadOfNewerDraft() {
        MaterialTypeDO type = type("viral_content", 99L);
        when(schemaMapper.selectListByTypeId(10L)).thenReturn(List.of(
                schema(22L, "DRAFT"), schema(21L, "PUBLISHED")));
        when(typeMapper.publishSchema(10L, 3, 21L)).thenReturn(1);
        initialize(type);
        assertEquals(21L, type.getCurrentSchemaVersionId());
        verify(schemaMapper, never()).insert(any(MaterialSchemaVersionDO.class));
    }

    @Test
    void doesNotPublishDraftOrArchivedVersion() {
        when(schemaMapper.selectListByTypeId(10L)).thenReturn(List.of(
                schema(22L, "DRAFT"), schema(21L, "ARCHIVED")));
        initialize(type("viral_account", 99L));
        verifyNoInteractions(typeMapper);
        verify(schemaMapper, never()).insert(any(MaterialSchemaVersionDO.class));
    }

    @Test
    void recreatesBothMissingDefaultTemplatesWithAllThreeSections() {
        for (String code : List.of("viral_account", "viral_content")) {
            MaterialTypeDO type = type(code, 99L);
            when(schemaMapper.selectListByTypeId(10L)).thenReturn(List.of());
            doAnswer(invocation -> {
                MaterialSchemaVersionDO created = invocation.getArgument(0);
                assertEquals("PUBLISHED", created.getStatus());
                assertEquals(10L, created.getMaterialTypeId());
                List<MaterialFieldDefinition> fields = new MaterialSchemaService().parseFields(created.getFieldsJson());
                for (String section : List.of("ACCOUNT_DETAIL", "DIRECTOR_ANALYSIS", "BUILD_SUGGESTION")) {
                    assertTrue(fields.stream().anyMatch(field -> section.equals(field.getSection())));
                }
                created.setId(30L);
                return 1;
            }).when(schemaMapper).insert(any(MaterialSchemaVersionDO.class));
            when(typeMapper.publishSchema(10L, 3, 30L)).thenReturn(1);
            initialize(type);
            assertEquals(30L, type.getCurrentSchemaVersionId());
        }
    }

    @Test
    void lostOptimisticUpdateDoesNotPretendReferenceWasRepaired() {
        MaterialTypeDO type = type("viral_account", 99L);
        when(schemaMapper.selectListByTypeId(10L)).thenReturn(List.of(schema(21L, "PUBLISHED")));
        initialize(type);
        assertEquals(99L, type.getCurrentSchemaVersionId());
    }

    @Test
    void customTypesRemainAdministratorOwned() {
        initialize(type("sop", 99L));
        verifyNoInteractions(typeMapper, schemaMapper);
    }
}
