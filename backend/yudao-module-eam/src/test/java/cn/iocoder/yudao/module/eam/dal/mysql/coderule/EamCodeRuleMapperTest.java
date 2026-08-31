package cn.iocoder.yudao.module.eam.dal.mysql.coderule;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.eam.dal.dataobject.coderule.EamCodeRuleDO;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EamCodeRuleMapperTest extends BaseDbUnitTest {

    @Resource
    private EamCodeRuleMapper codeRuleMapper;

    @Test
    void testSeparatorColumnIsEscaped() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(EamCodeRuleDO.class);
        assertNotNull(tableInfo);
        TableFieldInfo separatorField = tableInfo.getFieldList().stream()
                .filter(field -> "separator".equals(field.getProperty()))
                .findFirst()
                .orElseThrow();

        assertEquals("`separator`", separatorField.getColumn());
    }

    @Test
    void testSeparatorCrud() {
        EamCodeRuleDO rule = EamCodeRuleDO.builder()
                .prefix("AS")
                .useCategoryCode(false)
                .serialLength(4)
                .separator("-")
                .currentSerial(0L)
                .build();
        codeRuleMapper.insert(rule);

        List<EamCodeRuleDO> rules = codeRuleMapper.selectList();
        assertEquals(1, rules.size());
        assertEquals("-", rules.getFirst().getSeparator());

        EamCodeRuleDO update = new EamCodeRuleDO();
        update.setId(rule.getId());
        update.setSeparator("/");
        codeRuleMapper.updateById(update);

        assertEquals("/", codeRuleMapper.selectById(rule.getId()).getSeparator());
    }

}
