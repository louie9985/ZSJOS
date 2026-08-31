package cn.iocoder.yudao.module.zsjos.service.production;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.production.ProductionTicketDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.production.ProductionTicketMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionTicketObjectPermissionProviderTest {

    @InjectMocks private ProductionTicketObjectPermissionProvider provider;
    @Mock private ProductionTicketMapper mapper;
    @Mock private PermissionApi permissionApi;

    @Test
    void workflowActionsFollowExplicitResponsibility() {
        when(mapper.selectById(1L)).thenReturn(new ProductionTicketDO().setId(1L)
                .setOwnerOperatorUserId(230L).setReviewerUserId(230L).setAssigneeFilmingEditorUserId(251L));

        assertTrue(provider.hasPermission(1L, "read", 230L));
        assertFalse(provider.hasPermission(1L, "accept", 230L));
        assertFalse(provider.hasPermission(1L, "produce", 230L));
        assertFalse(provider.hasPermission(1L, "submit", 230L));
        assertTrue(provider.hasPermission(1L, "check", 230L));

        assertTrue(provider.hasPermission(1L, "read", 251L));
        assertTrue(provider.hasPermission(1L, "accept", 251L));
        assertTrue(provider.hasPermission(1L, "produce", 251L));
        assertTrue(provider.hasPermission(1L, "submit", 251L));
        assertFalse(provider.hasPermission(1L, "check", 251L));
    }

    @Test
    void queryAllDoesNotGrantWorkflowMutation() {
        when(mapper.selectById(1L)).thenReturn(new ProductionTicketDO().setId(1L)
                .setOwnerOperatorUserId(230L).setReviewerUserId(230L).setAssigneeFilmingEditorUserId(251L));
        when(permissionApi.hasAnyPermissions(1L, "zsjos:production-ticket:query-all")).thenReturn(true);

        assertTrue(provider.hasPermission(1L, "read", 1L));
        assertFalse(provider.hasPermission(1L, "accept", 1L));
        assertFalse(provider.hasPermission(1L, "check", 1L));
    }
}
