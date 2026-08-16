package cn.iocoder.yudao.module.eam.service.coderule;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.eam.controller.admin.coderule.vo.EamCodeRuleSaveReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.coderule.EamCodeRuleDO;
import cn.iocoder.yudao.module.eam.dal.mysql.coderule.EamCodeRuleMapper;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.CODE_RULE_GENERATE_FAIL;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.CODE_RULE_NOT_EXISTS;

/**
 * EAM 资产编号规则 Service 实现类
 */
@Service
@Validated
public class EamCodeRuleServiceImpl implements EamCodeRuleService {

    private static final String DEFAULT_SEPARATOR = "-";

    @Resource
    private EamCodeRuleMapper codeRuleMapper;
    @Resource
    private EamCategoryService categoryService;

    @Override
    public Long createCodeRule(EamCodeRuleSaveReqVO reqVO) {
        if (reqVO.getCategoryId() != null) {
            categoryService.validateCategoryExists(reqVO.getCategoryId());
        }
        EamCodeRuleDO rule = BeanUtils.toBean(reqVO, EamCodeRuleDO.class);
        rule.setCurrentSerial(0L);
        codeRuleMapper.insert(rule);
        return rule.getId();
    }

    @Override
    public void updateCodeRule(EamCodeRuleSaveReqVO reqVO) {
        validateCodeRuleExists(reqVO.getId());
        if (reqVO.getCategoryId() != null) {
            categoryService.validateCategoryExists(reqVO.getCategoryId());
        }
        // currentSerial 不通过表单更新，避免管理员误改导致编号回退重复
        EamCodeRuleDO updateObj = BeanUtils.toBean(reqVO, EamCodeRuleDO.class);
        updateObj.setCurrentSerial(null);
        codeRuleMapper.updateById(updateObj);
    }

    @Override
    public void deleteCodeRule(Long id) {
        validateCodeRuleExists(id);
        codeRuleMapper.deleteById(id);
    }

    @Override
    public List<EamCodeRuleDO> getCodeRuleList() {
        return codeRuleMapper.selectList();
    }

    @Override
    public EamCodeRuleDO getCodeRule(Long id) {
        return codeRuleMapper.selectById(id);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String generateAssetCode(Long categoryId) {
        EamCodeRuleDO rule = codeRuleMapper.selectByCategoryId(categoryId);
        if (rule == null) {
            throw exception(CODE_RULE_NOT_EXISTS);
        }
        // 行锁读取当前流水号，同一规则的并发创建在此排队
        Long current = codeRuleMapper.selectCurrentSerialForUpdate(rule.getId());
        if (current == null) {
            throw exception(CODE_RULE_GENERATE_FAIL);
        }
        long next = current + 1;
        codeRuleMapper.updateCurrentSerial(rule.getId(), next);

        return buildCode(rule, categoryId, next);
    }

    private String buildCode(EamCodeRuleDO rule, Long categoryId, long serial) {
        String separator = StrUtil.blankToDefault(rule.getSeparator(), DEFAULT_SEPARATOR);
        List<String> segments = new ArrayList<>();

        if (StrUtil.isNotBlank(rule.getPrefix())) {
            segments.add(rule.getPrefix());
        }
        if (Boolean.TRUE.equals(rule.getUseCategoryCode()) && categoryId != null) {
            EamCategoryDO category = categoryService.getCategory(categoryId);
            if (category != null && StrUtil.isNotBlank(category.getCode())) {
                segments.add(category.getCode());
            }
        }
        if (StrUtil.isNotBlank(rule.getDateFormat())) {
            segments.add(LocalDate.now().format(DateTimeFormatter.ofPattern(rule.getDateFormat())));
        }
        int length = rule.getSerialLength() != null ? rule.getSerialLength() : 4;
        segments.add(StrUtil.padPre(String.valueOf(serial), length, '0'));

        return String.join(separator, segments);
    }

    private EamCodeRuleDO validateCodeRuleExists(Long id) {
        EamCodeRuleDO rule = codeRuleMapper.selectById(id);
        if (rule == null) {
            throw exception(CODE_RULE_NOT_EXISTS);
        }
        return rule;
    }

}
