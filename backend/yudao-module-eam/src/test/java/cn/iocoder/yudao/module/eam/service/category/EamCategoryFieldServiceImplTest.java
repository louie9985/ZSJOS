package cn.iocoder.yudao.module.eam.service.category;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.eam.controller.admin.category.vo.EamCategoryFieldSaveReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryFieldDO;
import cn.iocoder.yudao.module.eam.dal.mysql.category.EamCategoryFieldMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.category.EamCategoryMapper;
import cn.iocoder.yudao.module.eam.enums.category.EamFieldTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.FIELD_KEY_DUPLICATE;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.FIELD_VALUE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EamCategoryFieldServiceImpl} 的单元测试类
 *
 * 覆盖字段继承合并与 extFields 校验两条核心规则。
 */
@Import({EamCategoryFieldServiceImpl.class, EamCategoryServiceImpl.class})
public class EamCategoryFieldServiceImplTest extends BaseDbUnitTest {

    @Resource
    private EamCategoryFieldServiceImpl fieldService;
    @Resource
    private EamCategoryMapper categoryMapper;
    @Resource
    private EamCategoryFieldMapper fieldMapper;
    @MockitoBean
    private DictDataApi dictDataApi;

    private Long createCategory(String name, String code, Long parentId) {
        EamCategoryDO category = EamCategoryDO.builder()
                .parentId(parentId).name(name).code(code).sort(0).status(0).build();
        categoryMapper.insert(category);
        return category.getId();
    }

    private void createField(Long categoryId, String key, String name, Integer type,
                             Boolean required, List<String> options) {
        EamCategoryFieldSaveReqVO reqVO = new EamCategoryFieldSaveReqVO();
        reqVO.setCategoryId(categoryId);
        reqVO.setFieldKey(key);
        reqVO.setFieldName(name);
        reqVO.setFieldType(type);
        reqVO.setRequired(required);
        reqVO.setOptions(options);
        reqVO.setSort(0);
        fieldService.createField(reqVO);
    }

    @Test
    public void testCreateField_keyDuplicate() {
        Long categoryId = createCategory("设备", "IT", 0L);
        createField(categoryId, "sn", "序列号", EamFieldTypeEnum.TEXT.getType(), false, null);

        // 同分类下重复标识必须被拒绝，否则 extFields 会出现二义性
        assertServiceException(() ->
                        createField(categoryId, "sn", "序列号2", EamFieldTypeEnum.TEXT.getType(), false, null),
                FIELD_KEY_DUPLICATE, "sn");
    }

    @Test
    public void testCreateField_optionsClearedForNonSelectType() {
        Long categoryId = createCategory("设备", "IT", 0L);
        createField(categoryId, "sn", "序列号", EamFieldTypeEnum.TEXT.getType(), false,
                List.of("A", "B"));

        // 非下拉类型不应残留选项数据
        EamCategoryFieldDO saved = fieldMapper.selectByCategoryIdAndFieldKey(categoryId, "sn");
        assertTrue(saved.getOptions() == null || saved.getOptions().isEmpty());
    }

    @Test
    public void testGetEffectiveFieldList_inheritsFromParent() {
        Long parentId = createCategory("设备资产", "DEV", 0L);
        Long childId = createCategory("笔记本", "NB", parentId);
        createField(parentId, "sn", "序列号", EamFieldTypeEnum.TEXT.getType(), false, null);
        createField(childId, "cpu", "处理器", EamFieldTypeEnum.TEXT.getType(), false, null);

        List<EamCategoryFieldDO> effective = fieldService.getEffectiveFieldList(childId);

        // 子分类同时拿到自身字段和父分类字段
        assertEquals(2, effective.size());
        assertTrue(effective.stream().anyMatch(f -> "sn".equals(f.getFieldKey())));
        assertTrue(effective.stream().anyMatch(f -> "cpu".equals(f.getFieldKey())));
    }

    @Test
    public void testGetEffectiveFieldList_childOverridesParent() {
        Long parentId = createCategory("设备资产", "DEV", 0L);
        Long childId = createCategory("笔记本", "NB", parentId);
        createField(parentId, "sn", "父级序列号", EamFieldTypeEnum.TEXT.getType(), false, null);
        createField(childId, "sn", "子级序列号", EamFieldTypeEnum.TEXT.getType(), true, null);

        List<EamCategoryFieldDO> effective = fieldService.getEffectiveFieldList(childId);

        // 同 fieldKey 时子分类定义胜出，父分类定义不重复出现
        assertEquals(1, effective.size());
        assertEquals("子级序列号", effective.get(0).getFieldName());
        assertEquals(childId, effective.get(0).getCategoryId());
    }

    @Test
    public void testValidateExtFields_adminIgnoresLegacyRequiredWhenMissing() {
        Long categoryId = createCategory("数字资产", "DIGI", 0L);
        createField(categoryId, "account", "账号", EamFieldTypeEnum.TEXT.getType(), true, null);

        Map<String, Object> result = fieldService.validateAndNormalizeExtFields(categoryId, new HashMap<>());

        assertTrue(result.isEmpty());
    }

    @Test
    public void testValidateExtFields_adminIgnoresLegacyRequiredWhenBlank() {
        Long categoryId = createCategory("数字资产", "DIGI", 0L);
        createField(categoryId, "account", "账号", EamFieldTypeEnum.TEXT.getType(), true, null);

        Map<String, Object> input = new HashMap<>();
        input.put("account", "   ");

        Map<String, Object> result = fieldService.validateAndNormalizeExtFields(categoryId, input);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testValidateExtFields_numberConversion() {
        Long categoryId = createCategory("设备", "IT", 0L);
        createField(categoryId, "memory", "内存", EamFieldTypeEnum.NUMBER.getType(), false, null);

        Map<String, Object> input = new HashMap<>();
        input.put("memory", "18");

        Map<String, Object> result = fieldService.validateAndNormalizeExtFields(categoryId, input);
        assertEquals(new BigDecimal("18"), result.get("memory"));
    }

    @Test
    public void testValidateExtFields_numberInvalid() {
        Long categoryId = createCategory("设备", "IT", 0L);
        createField(categoryId, "memory", "内存", EamFieldTypeEnum.NUMBER.getType(), false, null);

        Map<String, Object> input = new HashMap<>();
        input.put("memory", "十八");

        assertServiceException(
                () -> fieldService.validateAndNormalizeExtFields(categoryId, input),
                FIELD_VALUE_INVALID, "内存");
    }

    @Test
    public void testValidateExtFields_selectOptionNotAllowed() {
        Long categoryId = createCategory("数字资产", "DIGI", 0L);
        createField(categoryId, "carrier", "运营商", EamFieldTypeEnum.SELECT.getType(), false,
                List.of("移动", "联通"));

        Map<String, Object> input = new HashMap<>();
        input.put("carrier", "电信");

        // 下拉值必须落在配置的选项内，否则统计口径会被脏值污染
        assertServiceException(
                () -> fieldService.validateAndNormalizeExtFields(categoryId, input),
                FIELD_VALUE_INVALID, "运营商");
    }

    @Test
    public void testValidateExtFields_dropsUndefinedKeys() {
        Long categoryId = createCategory("设备", "IT", 0L);
        createField(categoryId, "sn", "序列号", EamFieldTypeEnum.TEXT.getType(), false, null);

        Map<String, Object> input = new HashMap<>();
        input.put("sn", "C02XY");
        input.put("legacyField", "旧数据");

        Map<String, Object> result = fieldService.validateAndNormalizeExtFields(categoryId, input);

        // 未定义的键被丢弃，避免分类调整后残留脏字段
        assertEquals(1, result.size());
        assertEquals("C02XY", result.get("sn"));
        assertFalse(result.containsKey("legacyField"));
    }

    @Test
    public void testValidateExtFields_optionalBlankNotPersisted() {
        Long categoryId = createCategory("设备", "IT", 0L);
        createField(categoryId, "sn", "序列号", EamFieldTypeEnum.TEXT.getType(), false, null);

        Map<String, Object> input = new HashMap<>();
        input.put("sn", "");

        Map<String, Object> result = fieldService.validateAndNormalizeExtFields(categoryId, input);
        assertTrue(result.isEmpty());
    }

}
