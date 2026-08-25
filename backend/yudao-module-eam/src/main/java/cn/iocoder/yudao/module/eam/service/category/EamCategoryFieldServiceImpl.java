package cn.iocoder.yudao.module.eam.service.category;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.module.eam.controller.admin.category.vo.EamCategoryFieldSaveReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryFieldDO;
import cn.iocoder.yudao.module.eam.dal.mysql.category.EamCategoryFieldMapper;
import cn.iocoder.yudao.module.eam.enums.category.EamFieldTypeEnum;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.*;

/**
 * EAM 分类自定义字段 Service 实现类
 */
@Service
@Validated
public class EamCategoryFieldServiceImpl implements EamCategoryFieldService {

    @Resource
    private EamCategoryFieldMapper fieldMapper;
    @Resource
    private EamCategoryService categoryService;
    @Resource
    private DictDataApi dictDataApi;

    @Override
    public Long createField(EamCategoryFieldSaveReqVO reqVO) {
        categoryService.validateCategoryExists(reqVO.getCategoryId());
        validateFieldKeyUnique(reqVO.getCategoryId(), reqVO.getFieldKey(), null);

        EamCategoryFieldDO field = BeanUtils.toBean(reqVO, EamCategoryFieldDO.class);
        normalizeOptions(field);
        fieldMapper.insert(field);
        return field.getId();
    }

    @Override
    public void updateField(EamCategoryFieldSaveReqVO reqVO) {
        validateFieldExists(reqVO.getId());
        categoryService.validateCategoryExists(reqVO.getCategoryId());
        validateFieldKeyUnique(reqVO.getCategoryId(), reqVO.getFieldKey(), reqVO.getId());

        EamCategoryFieldDO updateObj = BeanUtils.toBean(reqVO, EamCategoryFieldDO.class);
        normalizeOptions(updateObj);
        fieldMapper.updateById(updateObj);
    }

    @Override
    public void deleteField(Long id) {
        validateFieldExists(id);
        fieldMapper.deleteById(id);
    }

    @Override
    public List<EamCategoryFieldDO> getFieldListByCategoryId(Long categoryId) {
        return fieldMapper.selectListByCategoryId(categoryId);
    }

    @Override
    public List<EamCategoryFieldDO> getEffectiveFieldList(Long categoryId) {
        // 祖先链自底向上：[当前分类, 父, 祖父, ...]
        List<EamCategoryDO> chain = categoryService.getAncestorChain(categoryId);
        // 用 LinkedHashMap 按 fieldKey 去重；先放子分类的字段，父分类同 key 不覆盖
        Map<String, EamCategoryFieldDO> merged = new LinkedHashMap<>();
        for (int i = 0; i < chain.size(); i++) {
            EamCategoryDO node = chain.get(i);
            boolean inherited = i > 0; // 第 0 个是自身，其余都是继承来的
            for (EamCategoryFieldDO field : fieldMapper.selectListByCategoryId(node.getId())) {
                if (merged.containsKey(field.getFieldKey())) {
                    continue; // 子分类已定义，父分类不覆盖
                }
                // 借用 DO 的瞬时标记：继承字段在管理界面上不允许就地编辑
                field.setSort(field.getSort() == null ? 0 : field.getSort());
                merged.put(field.getFieldKey(), inherited ? markInherited(field) : field);
            }
        }
        List<EamCategoryFieldDO> result = new ArrayList<>(merged.values());
        result.sort((a, b) -> {
            int s = Integer.compare(
                    a.getSort() == null ? 0 : a.getSort(),
                    b.getSort() == null ? 0 : b.getSort());
            return s != 0 ? s : Long.compare(a.getId(), b.getId());
        });
        return result;
    }

    @Override
    public Map<String, Object> validateAndNormalizeExtFields(Long categoryId, Map<String, Object> extFields) {
        return validateAndNormalizeExtFieldsWithSnapshots(categoryId, extFields).values();
    }

    @Override
    public NormalizedExtFields validateAndNormalizeExtFieldsWithSnapshots(
            Long categoryId, Map<String, Object> extFields) {
        List<EamCategoryFieldDO> definitions = getEffectiveFieldList(categoryId);
        Map<String, Object> normalized = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, String> dictTypes = new LinkedHashMap<>();
        Map<String, Object> input = extFields != null ? extFields : Map.of();

        for (EamCategoryFieldDO def : definitions) {
            Object raw = input.get(def.getFieldKey());
            boolean blank = raw == null || (raw instanceof CharSequence && StrUtil.isBlank((CharSequence) raw));
            if (blank) {
                continue; // 管理端字段全部选填；员工收集表规则由独立表单执行
            }
            Object value = convertValue(def, raw);
            normalized.put(def.getFieldKey(), value);
            if (Objects.equals(def.getFieldType(), EamFieldTypeEnum.SELECT.getType())) {
                labels.put(def.getFieldKey(), resolveSelectLabel(def, String.valueOf(value)));
                if ("SYSTEM_DICT".equals(def.getOptionSource())) {
                    dictTypes.put(def.getFieldKey(), def.getDictType());
                }
            }
        }
        // 未在定义中的键直接丢弃，避免脏数据随分类变更堆积
        return new NormalizedExtFields(normalized, labels, dictTypes);
    }

    /**
     * 按字段类型转换取值；转换失败即视为该字段值不合法
     */
    private Object convertValue(EamCategoryFieldDO def, Object raw) {
        String text = String.valueOf(raw).trim();
        Integer type = def.getFieldType();
        if (Objects.equals(type, EamFieldTypeEnum.NUMBER.getType())) {
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException e) {
                throw exception(FIELD_VALUE_INVALID, def.getFieldName());
            }
        }
        if (Objects.equals(type, EamFieldTypeEnum.DATE.getType())) {
            try {
                return LocalDate.parse(text).toString();
            } catch (DateTimeParseException e) {
                throw exception(FIELD_VALUE_INVALID, def.getFieldName());
            }
        }
        if (Objects.equals(type, EamFieldTypeEnum.SELECT.getType())) {
            if ("SYSTEM_DICT".equals(def.getOptionSource())) {
                if (StrUtil.isBlank(def.getDictType())) {
                    throw exception(FIELD_VALUE_INVALID, def.getFieldName());
                }
                dictDataApi.validateDictDataList(def.getDictType(), List.of(text));
                return text;
            }
            if (CollUtil.isEmpty(def.getOptions()) || !def.getOptions().contains(text)) {
                throw exception(FIELD_VALUE_INVALID, def.getFieldName());
            }
            return text;
        }
        if (Objects.equals(type, EamFieldTypeEnum.FILE.getType())) {
            return text;
        }
        // 单行/多行文本
        return text;
    }

    private String resolveSelectLabel(EamCategoryFieldDO def, String value) {
        if (!"SYSTEM_DICT".equals(def.getOptionSource())) {
            return value;
        }
        return dictDataApi.getDictDataList(def.getDictType()).stream()
                .filter(item -> Objects.equals(item.getValue(), value))
                .map(DictDataRespDTO::getLabel)
                .findFirst()
                .orElseThrow(() -> exception(FIELD_VALUE_INVALID, def.getFieldName()));
    }

    private EamCategoryFieldDO markInherited(EamCategoryFieldDO field) {
        return field; // 继承标记在 Controller 转 VO 时按 categoryId 判定，DO 不额外持久化状态
    }

    private void normalizeOptions(EamCategoryFieldDO field) {
        if (!Objects.equals(field.getFieldType(), EamFieldTypeEnum.SELECT.getType())) {
            field.setOptions(null);
            field.setOptionSource(null);
            field.setDictType(null);
        } else if (StrUtil.isBlank(field.getOptionSource())) {
            field.setOptionSource("STATIC");
        } else if ("SYSTEM_DICT".equals(field.getOptionSource())) {
            field.setOptions(null);
        }
        field.setRequired(false); // 兼容旧字段；管理端不执行必填校验
        if (field.getAdminVisible() == null) {
            field.setAdminVisible(true);
        }
        if (field.getCollectionVisible() == null) {
            field.setCollectionVisible(true);
        }
        if (field.getCollectionRequired() == null) {
            field.setCollectionRequired(false);
        }
    }

    private EamCategoryFieldDO validateFieldExists(Long id) {
        EamCategoryFieldDO field = fieldMapper.selectById(id);
        if (field == null) {
            throw exception(FIELD_NOT_EXISTS);
        }
        return field;
    }

    private void validateFieldKeyUnique(Long categoryId, String fieldKey, Long excludeId) {
        EamCategoryFieldDO existing = fieldMapper.selectByCategoryIdAndFieldKey(categoryId, fieldKey);
        if (existing != null && !Objects.equals(existing.getId(), excludeId)) {
            throw exception(FIELD_KEY_DUPLICATE, fieldKey);
        }
    }

}
