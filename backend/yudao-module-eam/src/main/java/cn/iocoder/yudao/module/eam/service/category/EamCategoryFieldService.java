package cn.iocoder.yudao.module.eam.service.category;

import cn.iocoder.yudao.module.eam.controller.admin.category.vo.EamCategoryFieldSaveReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryFieldDO;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

/**
 * EAM 分类自定义字段 Service 接口
 */
public interface EamCategoryFieldService {

    Long createField(@Valid EamCategoryFieldSaveReqVO reqVO);

    void updateField(@Valid EamCategoryFieldSaveReqVO reqVO);

    void deleteField(Long id);

    /**
     * 获得某分类【直接定义】的字段列表（不含继承）
     */
    List<EamCategoryFieldDO> getFieldListByCategoryId(Long categoryId);

    /**
     * 获得某分类【生效】的字段列表（含从父分类继承，子分类同 fieldKey 覆盖父分类）
     *
     * 用于资产表单渲染和 extFields 校验。
     */
    List<EamCategoryFieldDO> getEffectiveFieldList(Long categoryId);

    /**
     * 按分类的生效字段定义，校验并规整资产扩展字段值
     *
     * @param categoryId 分类编号
     * @param extFields  待校验的扩展字段值
     * @return 规整后的扩展字段值（仅保留已定义字段，类型已转换）
     */
    Map<String, Object> validateAndNormalizeExtFields(Long categoryId, Map<String, Object> extFields);

    /**
     * 校验扩展字段，并同时生成下拉字段的标签和字典类型快照。
     */
    NormalizedExtFields validateAndNormalizeExtFieldsWithSnapshots(Long categoryId, Map<String, Object> extFields);

    NormalizedExtFields validateAndNormalizeExtFieldsWithSnapshots(Long categoryId, Map<String, Object> extFields,
                                                                    Map<String, Object> previousValues,
                                                                    Map<String, String> previousLabels,
                                                                    Map<String, String> previousDictTypes);

    record NormalizedExtFields(Map<String, Object> values, Map<String, String> labels,
                               Map<String, String> dictTypes) {
    }

}
