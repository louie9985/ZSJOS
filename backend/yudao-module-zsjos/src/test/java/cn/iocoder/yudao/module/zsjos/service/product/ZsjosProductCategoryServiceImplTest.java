package cn.iocoder.yudao.module.zsjos.service.product;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ZsjosProductCategorySaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ZsjosProductCategoryRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductCategoryDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductCategoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZsjosProductCategoryServiceImplTest {
    @InjectMocks private ZsjosProductCategoryServiceImpl service;
    @Mock private ZsjosProductCategoryMapper categoryMapper;
    @Mock private ZsjosProductMapper productMapper;
    @Mock private ProductCategoryLocks categoryLocks;

    @Test
    void createsRootAtDepthOne() {
        when(categoryMapper.selectByParentIdAndName(0L, "根分类")).thenReturn(null);
        service.create(request(null, 0L, "根分类"));
        ArgumentCaptor<ZsjosProductCategoryDO> captor = ArgumentCaptor.forClass(ZsjosProductCategoryDO.class);
        verify(categoryMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getLevel());
        assertEquals(new BigDecimal("10.00"), captor.getValue().getDefaultValidCashbackAmount());
        assertEquals(new BigDecimal("0.1000"), captor.getValue().getDefaultDealCashbackRate());
    }

    @Test
    void rejectsChildUnderCategoryWithSpu() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, 0L, 1));
        when(productMapper.selectCountByCategoryId(1L)).thenReturn(1L);
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.create(request(null, 1L, "子分类")));
        assertEquals(PRODUCT_CATEGORY_LEVEL_INVALID.getCode(), error.getCode());
        verify(categoryMapper, never()).insert(any(ZsjosProductCategoryDO.class));
    }

    @Test
    void rejectsDepthEleven() {
        when(categoryMapper.selectById(10L)).thenReturn(category(10L, 9L, 10));
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.create(request(null, 10L, "第十一层")));
        assertEquals(PRODUCT_CATEGORY_LEVEL_INVALID.getCode(), error.getCode());
    }

    @Test
    void movingSubtreeRecalculatesDescendantDepth() {
        ZsjosProductCategoryDO root = category(1L, 0L, 1);
        ZsjosProductCategoryDO current = category(2L, 1L, 2);
        ZsjosProductCategoryDO child = category(3L, 2L, 3);
        ZsjosProductCategoryDO newParent = category(4L, 1L, 2);
        when(categoryMapper.selectById(2L)).thenReturn(current);
        when(categoryMapper.selectById(4L)).thenReturn(newParent);
        when(categoryMapper.selectList()).thenReturn(List.of(root, current, child, newParent));
        when(categoryMapper.selectByParentIdAndName(4L, "移动节点")).thenReturn(null);

        service.update(request(2L, 4L, "移动节点"));

        ArgumentCaptor<ZsjosProductCategoryDO> captor = ArgumentCaptor.forClass(ZsjosProductCategoryDO.class);
        verify(categoryMapper, times(2)).updateById(captor.capture());
        assertEquals(3, captor.getAllValues().get(0).getLevel());
        assertEquals(4, captor.getAllValues().get(1).getLevel());
    }

    @Test
    void disablingCategoryRejectsEnabledChild() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, 0L, 1));
        when(categoryMapper.selectListByParentId(1L)).thenReturn(List.of(category(2L, 1L, 2)));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateStatus(1L, CommonStatusEnum.DISABLE.getStatus()));

        assertEquals(PRODUCT_CATEGORY_HAS_ENABLED_CHILDREN.getCode(), error.getCode());
        verify(categoryMapper, never()).updateById(any(ZsjosProductCategoryDO.class));
    }

    @Test
    void disablingCategoryRejectsEnabledProduct() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, 0L, 1));
        when(categoryMapper.selectListByParentId(1L)).thenReturn(List.of());
        when(productMapper.selectCountByCategoryIdAndStatus(1L, CommonStatusEnum.ENABLE.getStatus())).thenReturn(1L);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateStatus(1L, CommonStatusEnum.DISABLE.getStatus()));

        assertEquals(PRODUCT_CATEGORY_HAS_PRODUCTS.getCode(), error.getCode());
    }

    @Test
    void enablingCategoryRejectsDisabledParent() {
        ZsjosProductCategoryDO child = category(2L, 1L, 2);
        child.setStatus(CommonStatusEnum.DISABLE.getStatus());
        ZsjosProductCategoryDO parent = category(1L, 0L, 1);
        parent.setStatus(CommonStatusEnum.DISABLE.getStatus());
        when(categoryMapper.selectById(2L)).thenReturn(child);
        when(categoryMapper.selectById(1L)).thenReturn(parent);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateStatus(2L, CommonStatusEnum.ENABLE.getStatus()));

        assertEquals(PRODUCT_CATEGORY_PARENT_DISABLED.getCode(), error.getCode());
    }

    @Test
    void enablingCategoryOnlyUpdatesCurrentNode() {
        ZsjosProductCategoryDO current = category(1L, 0L, 1);
        current.setStatus(CommonStatusEnum.DISABLE.getStatus());
        when(categoryMapper.selectById(1L)).thenReturn(current);

        service.updateStatus(1L, CommonStatusEnum.ENABLE.getStatus());

        ArgumentCaptor<ZsjosProductCategoryDO> captor = ArgumentCaptor.forClass(ZsjosProductCategoryDO.class);
        verify(categoryMapper).updateById(captor.capture());
        assertEquals(1L, captor.getValue().getId());
        assertEquals(CommonStatusEnum.ENABLE.getStatus(), captor.getValue().getStatus());
        verify(categoryMapper, never()).selectListByParentId(anyLong());
    }

    @Test
    void editingCategoryCannotBypassStatusRules() {
        ZsjosProductCategoryDO current = category(1L, 0L, 1);
        when(categoryMapper.selectById(1L)).thenReturn(current);
        when(categoryMapper.selectList()).thenReturn(List.of(current));
        when(categoryMapper.selectListByParentId(1L)).thenReturn(List.of());
        when(productMapper.selectCountByCategoryIdAndStatus(1L, CommonStatusEnum.ENABLE.getStatus())).thenReturn(1L);
        ZsjosProductCategorySaveReqVO request = request(1L, 0L, "根分类");
        request.setStatus(CommonStatusEnum.DISABLE.getStatus());

        ServiceException error = assertThrows(ServiceException.class, () -> service.update(request));

        assertEquals(PRODUCT_CATEGORY_HAS_PRODUCTS.getCode(), error.getCode());
    }

    @Test
    void statusTreesExcludeOtherStatusAndKeepFilteredHierarchy() {
        ZsjosProductCategoryDO enabledRoot = category(1L, 0L, 1);
        ZsjosProductCategoryDO disabledChild = category(2L, 1L, 2);
        disabledChild.setStatus(CommonStatusEnum.DISABLE.getStatus());
        ZsjosProductCategoryDO disabledGrandchild = category(3L, 2L, 3);
        disabledGrandchild.setStatus(CommonStatusEnum.DISABLE.getStatus());
        when(categoryMapper.selectList()).thenReturn(new ArrayList<>(List.of(enabledRoot, disabledChild, disabledGrandchild)));

        List<ZsjosProductCategoryRespVO> disabledTree = service.getTree(CommonStatusEnum.DISABLE.getStatus());

        assertEquals(1, disabledTree.size());
        assertEquals(2L, disabledTree.get(0).getId());
        assertEquals(3L, disabledTree.get(0).getChildren().get(0).getId());

        List<ZsjosProductCategoryRespVO> enabledTree = service.getTree(CommonStatusEnum.ENABLE.getStatus());
        assertEquals(1, enabledTree.size());
        assertEquals(1L, enabledTree.get(0).getId());
        assertEquals(null, enabledTree.get(0).getChildren());
    }

    private static ZsjosProductCategorySaveReqVO request(Long id, Long parentId, String name) {
        ZsjosProductCategorySaveReqVO request = new ZsjosProductCategorySaveReqVO();
        request.setId(id); request.setParentId(parentId); request.setName(name);
        request.setStatus(CommonStatusEnum.ENABLE.getStatus()); request.setSort(0);
        return request;
    }

    private static ZsjosProductCategoryDO category(Long id, Long parentId, int level) {
        ZsjosProductCategoryDO category = new ZsjosProductCategoryDO();
        category.setId(id); category.setParentId(parentId); category.setLevel(level);
        category.setName("分类" + id); category.setStatus(CommonStatusEnum.ENABLE.getStatus()); category.setSort(0);
        return category;
    }
}
