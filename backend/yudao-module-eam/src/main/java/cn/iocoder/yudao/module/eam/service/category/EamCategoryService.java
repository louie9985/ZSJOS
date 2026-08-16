package cn.iocoder.yudao.module.eam.service.category;

import cn.iocoder.yudao.module.eam.controller.admin.category.vo.EamCategorySaveReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * EAM 资产分类 Service 接口
 */
public interface EamCategoryService {

    /**
     * 创建资产分类
     *
     * @param reqVO 创建信息
     * @return 分类编号
     */
    Long createCategory(@Valid EamCategorySaveReqVO reqVO);

    /**
     * 更新资产分类
     *
     * @param reqVO 更新信息
     */
    void updateCategory(@Valid EamCategorySaveReqVO reqVO);

    /**
     * 删除资产分类
     *
     * @param id 分类编号
     */
    void deleteCategory(Long id);

    /**
     * 获得全部分类列表（树形）
     *
     * @return 分类列表
     */
    List<EamCategoryDO> getCategoryList();

    /**
     * 获得单个分类
     *
     * @param id 分类编号
     * @return 分类
     */
    EamCategoryDO getCategory(Long id);

    /**
     * 校验分类存在
     *
     * @param id 分类编号
     * @return 分类
     */
    EamCategoryDO validateCategoryExists(Long id);

    /**
     * 获取从指定分类到根的祖先链（含自身，自底向上）
     *
     * @param categoryId 分类编号
     * @return 祖先分类列表
     */
    List<EamCategoryDO> getAncestorChain(Long categoryId);

}
