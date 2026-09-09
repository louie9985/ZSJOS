package cn.iocoder.yudao.module.bpm.api.definition;

import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmCategoryEnsureReqDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.category.BpmCategorySaveReqVO;
import cn.iocoder.yudao.module.bpm.service.definition.BpmCategoryService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BpmCategoryApiImpl implements BpmCategoryApi {

    @Resource
    private BpmCategoryService categoryService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ensureCategories(List<BpmCategoryEnsureReqDTO> categories) {
        if (categories == null || categories.isEmpty()) {
            return;
        }
        for (BpmCategoryEnsureReqDTO category : categories) {
            if (!categoryService.getCategoryListByCode(List.of(category.getCode())).isEmpty()) {
                continue;
            }
            BpmCategorySaveReqVO request = new BpmCategorySaveReqVO();
            request.setName(category.getName());
            request.setCode(category.getCode());
            request.setDescription(category.getDescription());
            request.setStatus(category.getStatus());
            request.setSort(category.getSort());
            try {
                categoryService.createCategory(request);
            } catch (DuplicateKeyException error) {
                if (categoryService.getCategoryListByCode(List.of(category.getCode())).isEmpty()) {
                    throw error;
                }
            }
        }
    }
}
