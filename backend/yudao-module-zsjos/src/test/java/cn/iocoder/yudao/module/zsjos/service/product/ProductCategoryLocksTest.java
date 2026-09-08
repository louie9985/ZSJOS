package cn.iocoder.yudao.module.zsjos.service.product;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductCategoryDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductCategoryMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCategoryLocksTest {
    @InjectMocks ProductCategoryLocks locks;
    @Mock ZsjosProductCategoryMapper mapper;
    @BeforeEach void setup() { TenantContextHolder.setTenantId(7L); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void locksIdsInAscendingOrderNotTreeOrder() {
        var root = category(90L, 0L); var leaf = category(2L, 90L);
        when(mapper.selectCurrentList()).thenReturn(List.of(root, leaf));
        when(mapper.selectByIdForUpdate(2L, 7L)).thenReturn(leaf);
        when(mapper.selectByIdForUpdate(90L, 7L)).thenReturn(root);
        assertEquals(Set.of(2L, 90L), locks.paths(List.of(2L)).keySet());
        var order = inOrder(mapper);
        order.verify(mapper).selectCurrentList();
        order.verify(mapper).selectByIdForUpdate(2L, 7L);
        order.verify(mapper).selectByIdForUpdate(90L, 7L);
        order.verify(mapper).selectCurrentList();
    }

    @Test void rejectsMovedPathAndNewSubtreeMember() {
        var root = category(1L, 0L); var leaf = category(2L, 1L);
        when(mapper.selectCurrentList()).thenReturn(List.of(root, leaf), List.of(root, leaf, category(3L, 2L)));
        when(mapper.selectByIdForUpdate(1L, 7L)).thenReturn(root);
        when(mapper.selectByIdForUpdate(2L, 7L)).thenReturn(leaf);
        assertEquals(1_900_018_011, assertThrows(ServiceException.class, () -> locks.mutation(2L, null)).getCode());
    }

    private ZsjosProductCategoryDO category(Long id, Long parent) {
        return new ZsjosProductCategoryDO().setId(id).setParentId(parent).setStatus(0);
    }
}
