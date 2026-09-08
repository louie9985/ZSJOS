package cn.iocoder.yudao.module.zsjos.service.product;

import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ZsjosProductCategoryRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ZsjosProductCategorySaveReqVO;
import java.util.List;

public interface ZsjosProductCategoryService {
    Long create(ZsjosProductCategorySaveReqVO reqVO);
    void update(ZsjosProductCategorySaveReqVO reqVO);
    void delete(Long id);
    void updateStatus(Long id, Integer status);
    ZsjosProductCategoryRespVO get(Long id);
    List<ZsjosProductCategoryRespVO> getTree(Integer status);
    default List<ZsjosProductCategoryRespVO> getTree() { return getTree(null); }
}
