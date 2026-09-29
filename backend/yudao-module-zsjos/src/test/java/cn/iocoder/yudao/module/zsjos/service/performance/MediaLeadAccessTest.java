package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.MediaLeadVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.MediaLeadOrgDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.MediaLeadOrgMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaLeadAccessTest {
    @InjectMocks private MediaLeadAccess access;
    @Mock private PermissionApi permissions;
    @Mock private DeptApi depts;
    @Mock private PostApi posts;
    @Mock private AdminUserApi users;
    @Mock private MediaLeadOrgMapper orgs;
    private MockedStatic<SecurityFrameworkUtils> security;

    @BeforeEach void login() {
        security = mockStatic(SecurityFrameworkUtils.class);
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1L);
    }
    @AfterEach void close() { security.close(); }
    private MediaLeadVO.Query q(String type, long id) { return new MediaLeadVO.Query(type, id, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 28)); }
    private MediaLeadOrgDO org(long dept, long center, String kind) {
        var row = new MediaLeadOrgDO(); row.setDeptId(dept); row.setCenterId(center); row.setKind(kind); return row;
    }
    private void mediaUser(long id, long dept) {
        var user = new AdminUserRespDTO(); user.setId(id); user.setDeptId(dept); user.setStatus(0);
        when(users.getUser(id)).thenReturn(user);
        var post = new PostRespDTO(); post.setId(7L);
        when(posts.getPostByCode("new_media_operator")).thenReturn(post);
        when(users.getUserListByPostIds(List.of(7L))).thenReturn(List.of(user));
    }
    @Test void centerButtonCannotReadUnconfiguredPerson() {
        mediaUser(2L, 10L);
        when(permissions.hasAnyPermissions(1L, MediaLeadAccess.QUERY)).thenReturn(true);
        when(permissions.hasAnyPermissions(1L, MediaLeadAccess.CENTER)).thenReturn(true);
        when(permissions.hasTenantReadAllAccess(1L)).thenReturn(true);
        assertThrows(RuntimeException.class, () -> access.authorize(q("USER", 2L), false));
    }
    @Test void centerCannotContainOutOfScopeDepartment() {
        when(permissions.hasAnyPermissions(1L, MediaLeadAccess.QUERY)).thenReturn(true);
        when(permissions.hasAnyPermissions(1L, MediaLeadAccess.CENTER)).thenReturn(true);
        var scope = new DeptDataPermissionRespDTO(); scope.setDeptIds(Set.of(20L));
        when(permissions.getDeptDataPermission(1L)).thenReturn(scope);
        when(orgs.selectList()).thenReturn(List.of(org(20, 20, "CENTER")));
        var child = new cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO();
        child.setId(10L); child.setParentId(20L);
        when(depts.getChildDeptList(20L)).thenReturn(List.of(child));
        assertThrows(RuntimeException.class, () -> access.authorize(q("CENTER", 20L), false));
    }
    @Test void menuPermissionNeverGrantsCrossDepartmentRead() {
        mediaUser(2L, 10L);
        when(permissions.hasAnyPermissions(1L, MediaLeadAccess.QUERY)).thenReturn(true);
        when(permissions.hasAnyPermissions(1L, MediaLeadAccess.DEPARTMENT)).thenReturn(true);
        when(permissions.getDeptDataPermission(1L)).thenReturn(new DeptDataPermissionRespDTO());
        assertTrue(access.has(MediaLeadAccess.DEPARTMENT));
        assertThrows(RuntimeException.class, () -> access.authorize(q("USER", 2L), false));
    }
    @Test void tenantReadAllDoesNotGrantMissingFeatureButton() {
        when(permissions.hasAnyPermissions(1L, MediaLeadAccess.QUERY)).thenReturn(true);
        when(permissions.hasTenantReadAllAccess(1L)).thenReturn(true);
        when(orgs.selectList()).thenReturn(List.of(org(20, 20, "CENTER")));
        assertThrows(RuntimeException.class, () -> access.authorize(q("CENTER", 20L), false));
    }

    @Test void systemDescendantsBecomeDepartmentsWithoutSeparateMappings() {
        when(orgs.selectList()).thenReturn(List.of(org(20, 20, "CENTER")));
        var child = new cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO();
        child.setId(10L); child.setParentId(20L); child.setName("一部");
        var grandchild = new cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO();
        grandchild.setId(11L); grandchild.setParentId(10L); grandchild.setName("小组");
        when(depts.getChildDeptList(20L)).thenReturn(List.of(child, grandchild));
        when(permissions.hasAnyPermissions(eq(1L), anyString()))
                .thenAnswer(call -> MediaLeadAccess.DEPARTMENT.equals(call.getArgument(1)));
        when(permissions.hasTenantReadAllAccess(1L)).thenReturn(true);
        when(depts.getDept(10L)).thenReturn(child);
        when(depts.getDept(11L)).thenReturn(grandchild);
        assertEquals(3, access.organizations().size());
        var tree = access.tree();
        assertEquals(2, tree.size());
        assertNull(tree.stream().filter(x -> "DEPT:10".equals(x.key())).findFirst().orElseThrow().parentKey());
        assertEquals("DEPT:10", tree.stream().filter(x -> "DEPT:11".equals(x.key())).findFirst().orElseThrow().parentKey());
    }

    @Test void departmentPermissionCanReadDirectCenterContributor() {
        mediaUser(2L, 20L);
        when(permissions.hasAnyPermissions(1L, MediaLeadAccess.QUERY)).thenReturn(true);
        when(permissions.hasAnyPermissions(1L, MediaLeadAccess.DEPARTMENT)).thenReturn(true);
        when(permissions.hasTenantReadAllAccess(1L)).thenReturn(true);
        when(orgs.selectList()).thenReturn(List.of(org(20, 20, "CENTER")));
        assertDoesNotThrow(() -> access.authorize(q("USER", 2L), false));
    }
}
