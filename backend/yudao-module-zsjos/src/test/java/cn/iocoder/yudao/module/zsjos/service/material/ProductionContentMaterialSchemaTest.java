package cn.iocoder.yudao.module.zsjos.service.material;

import cn.iocoder.yudao.framework.xss.core.clean.XssCleaner;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.FIELD_SECTIONS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ProductionContentMaterialSchemaTest {

    @InjectMocks private MaterialSchemaService schemaService;
    @Mock private DictDataApi dictDataApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private DeptApi deptApi;
    @Mock private FileApi fileApi;
    @Mock private XssCleaner xssCleaner;

    /** 内置模板必须通过模板校验，否则启动时的自动发布会静默失败。 */
    @Test
    void bundledSchemaPassesValidation() {
        schemaService.validateSchema(ProductionContentMaterialSchema.fields(), false);
    }

    /**
     * 生产内容只在终审收录时由服务端写入，没有人工补填入口；任何必填字段都会让缺少来源的
     * 那条内容收录失败，因此模板必须全部非必填。
     */
    @Test
    void bundledSchemaHasNoRequiredField() {
        assertTrue(ProductionContentMaterialSchema.fields().stream()
                .noneMatch(field -> Boolean.TRUE.equals(field.getRequired())));
    }

    /** 字段编码需保持稳定，固定映射按编码寻址。 */
    @Test
    void bundledSchemaKeysMatchFixedMappingContract() {
        List<String> keys = ProductionContentMaterialSchema.fields().stream()
                .map(MaterialFieldDefinition::getKey).toList();
        assertEquals(List.of("content_title", "content_topic", "script_text", "deliverable_url",
                "lead_resource_url", "planned_publish_at", "deliverable_files",
                "account_type", "profession", "account_stage"), keys);
    }

    /** 三个推荐维度各有且只有一个承载字段，收录后的生产内容才能参与推荐。 */
    @Test
    void bundledSchemaCarriesEveryRecommendationDimensionOnce() {
        List<MaterialFieldDefinition> dictFields = ProductionContentMaterialSchema.fields().stream()
                .filter(field -> field.getDictType() != null).toList();
        assertEquals(3, dictFields.size());
        assertEquals(Set.of("zsjos_persona_type", "zsjos_material_profession", "zsjos_media_account_stage"),
                dictFields.stream().map(MaterialFieldDefinition::getDictType)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    /** 分区取值必须在受支持集合内，否则前端表单无法归位。 */
    @Test
    void bundledSchemaUsesSupportedSections() {
        assertFalse(ProductionContentMaterialSchema.fields().isEmpty());
        assertTrue(ProductionContentMaterialSchema.fields().stream()
                .allMatch(field -> field.getSection() == null || FIELD_SECTIONS.contains(field.getSection())));
    }
}
