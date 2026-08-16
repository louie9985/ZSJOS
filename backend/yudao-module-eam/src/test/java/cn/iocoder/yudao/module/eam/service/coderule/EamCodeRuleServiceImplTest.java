package cn.iocoder.yudao.module.eam.service.coderule;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.coderule.EamCodeRuleDO;
import cn.iocoder.yudao.module.eam.dal.mysql.category.EamCategoryMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.coderule.EamCodeRuleMapper;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryServiceImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.CODE_RULE_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * {@link EamCodeRuleServiceImpl} 的单元测试类
 *
 * 覆盖编号拼装格式与流水号自增不重复。
 */
@Import({EamCodeRuleServiceImpl.class, EamCategoryServiceImpl.class})
public class EamCodeRuleServiceImplTest extends BaseDbUnitTest {

    @Resource
    private EamCodeRuleServiceImpl codeRuleService;
    @Resource
    private EamCodeRuleMapper codeRuleMapper;
    @Resource
    private EamCategoryMapper categoryMapper;

    private Long createCategory(String code) {
        EamCategoryDO category = EamCategoryDO.builder()
                .parentId(0L).name("设备").code(code).sort(0).status(0).build();
        categoryMapper.insert(category);
        return category.getId();
    }

    private void createRule(Long categoryId, String prefix, Boolean useCategoryCode,
                            String dateFormat, Integer serialLength, String separator) {
        codeRuleMapper.insert(EamCodeRuleDO.builder()
                .categoryId(categoryId)
                .prefix(prefix)
                .useCategoryCode(useCategoryCode)
                .dateFormat(dateFormat)
                .serialLength(serialLength)
                .separator(separator)
                .currentSerial(0L)
                .build());
    }

    @Test
    @Transactional
    public void testGenerateAssetCode_fullFormat() {
        Long categoryId = createCategory("IT");
        createRule(categoryId, "AS", true, "yyyy", 4, "-");

        String code = codeRuleService.generateAssetCode(categoryId);

        assertEquals("AS-IT-" + LocalDate.now().getYear() + "-0001", code);
    }

    @Test
    @Transactional
    public void testGenerateAssetCode_withoutDate() {
        Long categoryId = createCategory("IT");
        createRule(categoryId, "AS", true, null, 3, "-");

        assertEquals("AS-IT-001", codeRuleService.generateAssetCode(categoryId));
    }

    @Test
    @Transactional
    public void testGenerateAssetCode_serialIncrementsAndIsUnique() {
        Long categoryId = createCategory("IT");
        createRule(categoryId, "AS", false, null, 4, "-");

        String first = codeRuleService.generateAssetCode(categoryId);
        String second = codeRuleService.generateAssetCode(categoryId);
        String third = codeRuleService.generateAssetCode(categoryId);

        assertEquals("AS-0001", first);
        assertEquals("AS-0002", second);
        assertEquals("AS-0003", third);
        assertNotEquals(first, second);
    }

    @Test
    @Transactional
    public void testGenerateAssetCode_fallsBackToGlobalRule() {
        Long categoryId = createCategory("IT");
        // 该分类没有专属规则，只有全局规则（category_id 为 null）
        createRule(null, "GLOBAL", false, null, 4, "-");

        assertEquals("GLOBAL-0001", codeRuleService.generateAssetCode(categoryId));
    }

    @Test
    @Transactional
    public void testGenerateAssetCode_categoryRuleWinsOverGlobal() {
        Long categoryId = createCategory("IT");
        createRule(null, "GLOBAL", false, null, 4, "-");
        createRule(categoryId, "SPEC", false, null, 4, "-");

        assertEquals("SPEC-0001", codeRuleService.generateAssetCode(categoryId));
    }

    @Test
    @Transactional
    public void testGenerateAssetCode_noRuleThrows() {
        Long categoryId = createCategory("IT");

        assertServiceException(() -> codeRuleService.generateAssetCode(categoryId),
                CODE_RULE_NOT_EXISTS);
    }

    @Test
    @Transactional
    public void testGenerateAssetCode_serialPersisted() {
        Long categoryId = createCategory("IT");
        createRule(categoryId, "AS", false, null, 4, "-");

        codeRuleService.generateAssetCode(categoryId);
        codeRuleService.generateAssetCode(categoryId);

        // 流水号必须落库，重启后不会从头开始导致编号冲突
        EamCodeRuleDO rule = codeRuleMapper.selectByCategoryId(categoryId);
        assertEquals(2L, rule.getCurrentSerial());
    }

}
