package cn.iocoder.yudao.module.system.service.workbenchlayout;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.controller.admin.workbenchlayout.vo.WorkbenchLayoutPublishReqVO;
import cn.iocoder.yudao.module.system.controller.admin.workbenchlayout.vo.WorkbenchLayoutRestoreReqVO;
import cn.iocoder.yudao.module.system.controller.admin.workbenchlayout.vo.WorkbenchLayoutSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;
import cn.iocoder.yudao.module.system.dal.dataobject.workbenchlayout.WorkbenchLayoutDO;
import cn.iocoder.yudao.module.system.dal.dataobject.workbenchlayout.WorkbenchLayoutVersionDO;
import cn.iocoder.yudao.module.system.dal.mysql.workbenchlayout.WorkbenchLayoutMapper;
import cn.iocoder.yudao.module.system.dal.mysql.workbenchlayout.WorkbenchLayoutVersionMapper;
import cn.iocoder.yudao.module.system.service.permission.MenuService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.permission.RoleService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import cn.iocoder.yudao.module.system.service.workbenchlayout.model.WorkbenchLayoutSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.WORKBENCH_LAYOUT_REVISION_CONFLICT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkbenchLayoutServiceImplTest {

    @InjectMocks
    private WorkbenchLayoutServiceImpl service;
    @Spy
    private WorkbenchLayoutResolver resolver = new WorkbenchLayoutResolver();
    @Mock
    private WorkbenchLayoutMapper layoutMapper;
    @Mock
    private WorkbenchLayoutVersionMapper versionMapper;
    @Mock
    private MenuService menuService;
    @Mock
    private RoleService roleService;
    @Mock
    private PermissionService permissionService;
    @Mock
    private AdminUserService userService;

    private MenuDO salaryMenu;

    @BeforeEach
    void setUp() {
        salaryMenu = new MenuDO().setId(100L).setParentId(0L).setType(2).setSort(1)
                .setName("我的工资条").setPath("/hrm/portal/salary/slip").setIcon("ep:money")
                .setWorkbenchRenderMode("native").setVisible(true).setKeepAlive(true)
                .setAlwaysShow(true).setStatus(0);
    }

    @Test
    void shouldFallbackWhenGlobalLayoutIsNotPublished() {
        when(layoutMapper.selectByScope("GLOBAL", 0L)).thenReturn(null);

        var projection = service.getProjection(Set.of(1L), List.of(salaryMenu));

        assertThat(projection.getMenus()).isEmpty();
        assertThat(projection.getMeta().getFallback()).isTrue();
        assertThat(projection.getMeta().getFallbackReason()).isEqualTo("GLOBAL_LAYOUT_NOT_PUBLISHED");
    }

    @Test
    void shouldUseHighestPriorityEnabledRoleLayout() {
        WorkbenchLayoutSnapshot global = resolver.createInitialGlobalSnapshot(List.of(salaryMenu));
        WorkbenchLayoutDO globalLayout = new WorkbenchLayoutDO().setPublishedVersionId(10L);
        WorkbenchLayoutVersionDO globalVersion = new WorkbenchLayoutVersionDO().setId(10L).setVersionNo(3)
                .setSnapshotJson(JsonUtils.toJsonString(global));

        List<WorkbenchLayoutSnapshot.Node> roleNodes = copyNodes(global.getNodes());
        findPage(roleNodes, 100L).setHidden(true);
        WorkbenchLayoutSnapshot roleDifference = resolver.normalizeRoleDraft(global,
                WorkbenchLayoutSnapshot.builder().scopeType("ROLE").enabled(true).priority(1)
                        .nodes(roleNodes).build());
        WorkbenchLayoutDO winningRole = new WorkbenchLayoutDO().setScopeId(2L)
                .setPublishedVersionId(20L).setPublishedVersionNo(1)
                .setPublishedEnabled(true).setPublishedPriority(1);
        WorkbenchLayoutVersionDO roleVersion = new WorkbenchLayoutVersionDO().setId(20L).setVersionNo(1)
                .setSnapshotJson(JsonUtils.toJsonString(roleDifference));

        when(layoutMapper.selectByScope("GLOBAL", 0L)).thenReturn(globalLayout);
        when(versionMapper.selectById(10L)).thenReturn(globalVersion);
        when(layoutMapper.selectPublishedRoleLayouts(Set.of(1L, 2L))).thenReturn(List.of(winningRole));
        when(versionMapper.selectById(20L)).thenReturn(roleVersion);

        var projection = service.getProjection(Set.of(1L, 2L), List.of(salaryMenu));

        assertThat(projection.getMenus()).isEmpty();
        assertThat(projection.getMeta().getFallback()).isFalse();
        assertThat(projection.getMeta().getWinningRoleId()).isEqualTo(2L);
        assertThat(projection.getMeta().getGlobalVersionNo()).isEqualTo(3);
        assertThat(projection.getMeta().getRoleVersionNo()).isEqualTo(1);
    }

    @Test
    void shouldRejectStaleDraftRevision() {
        WorkbenchLayoutSnapshot global = resolver.createInitialGlobalSnapshot(List.of(salaryMenu));
        WorkbenchLayoutSaveReqVO request = new WorkbenchLayoutSaveReqVO()
                .setScopeType("GLOBAL").setScopeId(0L).setDraftRevision(1).setSnapshot(global);
        when(menuService.getMenuListByTenant(any())).thenReturn(List.of(salaryMenu));
        when(layoutMapper.selectByScope("GLOBAL", 0L)).thenReturn(
                new WorkbenchLayoutDO().setId(1L).setDraftRevision(2));

        assertServiceException(() -> service.saveDraft(request), WORKBENCH_LAYOUT_REVISION_CONFLICT);
    }

    @Test
    void shouldIgnoreDisabledPublishedRoleWhenCalculatingGlobalImpact() {
        WorkbenchLayoutSnapshot global = resolver.createInitialGlobalSnapshot(List.of(salaryMenu));
        WorkbenchLayoutDO globalLayout = new WorkbenchLayoutDO().setId(1L).setScopeType("GLOBAL")
                .setScopeId(0L).setDraftSnapshotJson(JsonUtils.toJsonString(global)).setDraftRevision(1);
        WorkbenchLayoutDO disabledRole = new WorkbenchLayoutDO().setId(2L).setScopeType("ROLE")
                .setScopeId(9L).setPublishedVersionId(20L).setPublishedEnabled(false);
        when(layoutMapper.selectByScope("GLOBAL", 0L)).thenReturn(globalLayout);
        when(menuService.getMenuListByTenant(any())).thenReturn(List.of(salaryMenu));
        when(layoutMapper.selectListByScopeType("ROLE")).thenReturn(List.of(disabledRole));
        when(roleService.getRoleList()).thenReturn(new ArrayList<>());

        var impact = service.getPublishImpact("GLOBAL", 0L);

        assertThat(impact.getPublishable()).isTrue();
        assertThat(impact.getAffectedRoleCount()).isZero();
        verify(versionMapper, never()).selectById(20L);
    }

    @Test
    void shouldPublishImmutableVersionAndAdvanceRevision() {
        WorkbenchLayoutSnapshot global = resolver.createInitialGlobalSnapshot(List.of(salaryMenu));
        String snapshotJson = JsonUtils.toJsonString(global);
        WorkbenchLayoutDO layout = new WorkbenchLayoutDO().setId(1L).setScopeType("GLOBAL")
                .setScopeId(0L).setDraftSnapshotJson(snapshotJson).setDraftRevision(2)
                .setPublishedVersionNo(3);
        when(layoutMapper.selectByScope("GLOBAL", 0L)).thenReturn(layout);
        when(layoutMapper.selectByIdForUpdate(1L)).thenReturn(layout);
        when(menuService.getMenuListByTenant(any())).thenReturn(List.of(salaryMenu));
        when(layoutMapper.selectListByScopeType("ROLE")).thenReturn(List.of());
        when(roleService.getRoleList()).thenReturn(new ArrayList<>());
        when(versionMapper.insert(any(WorkbenchLayoutVersionDO.class))).thenAnswer(invocation -> {
            invocation.<WorkbenchLayoutVersionDO>getArgument(0).setId(50L);
            return 1;
        });
        when(layoutMapper.updatePublished(1L, 2, 50L, 4, true, null)).thenReturn(1);
        WorkbenchLayoutPublishReqVO request = new WorkbenchLayoutPublishReqVO()
                .setScopeType("GLOBAL").setScopeId(0L).setDraftRevision(2)
                .setPublishRemark("  调整员工导航  ");

        Long versionId = service.publish(request, 7L);

        assertThat(versionId).isEqualTo(50L);
        ArgumentCaptor<WorkbenchLayoutVersionDO> versionCaptor =
                ArgumentCaptor.forClass(WorkbenchLayoutVersionDO.class);
        verify(versionMapper).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue()).satisfies(version -> {
            assertThat(version.getVersionNo()).isEqualTo(4);
            assertThat(version.getSnapshotJson()).isEqualTo(snapshotJson);
            assertThat(version.getPublishRemark()).isEqualTo("调整员工导航");
            assertThat(version.getPublisherUserId()).isEqualTo(7L);
            assertThat(version.getEnabled()).isTrue();
        });
        verify(layoutMapper).updatePublished(1L, 2, 50L, 4, true, null);
    }

    @Test
    void shouldRestorePublishedVersionAsNewDraftOnly() {
        WorkbenchLayoutSnapshot historical = resolver.createInitialGlobalSnapshot(List.of(salaryMenu));
        String snapshotJson = JsonUtils.toJsonString(historical);
        WorkbenchLayoutDO layout = new WorkbenchLayoutDO().setId(1L).setScopeType("GLOBAL")
                .setScopeId(0L).setDraftRevision(5).setPublishedVersionId(30L);
        WorkbenchLayoutVersionDO version = new WorkbenchLayoutVersionDO().setId(20L).setLayoutId(1L)
                .setSnapshotJson(snapshotJson);
        when(layoutMapper.selectByScope("GLOBAL", 0L)).thenReturn(layout);
        when(layoutMapper.selectByIdForUpdate(1L)).thenReturn(layout);
        when(versionMapper.selectById(20L)).thenReturn(version);
        when(layoutMapper.updateDraft(1L, 5, snapshotJson, 20L)).thenReturn(1);
        WorkbenchLayoutRestoreReqVO request = new WorkbenchLayoutRestoreReqVO()
                .setScopeType("GLOBAL").setScopeId(0L).setVersionId(20L).setDraftRevision(5);

        assertThat(service.restoreDraft(request)).isEqualTo(6);

        verify(layoutMapper).updateDraft(1L, 5, snapshotJson, 20L);
        verify(layoutMapper, never()).updatePublished(any(), any(), any(), any(), any(), any());
    }

    private WorkbenchLayoutSnapshot.Node findPage(List<WorkbenchLayoutSnapshot.Node> nodes, Long menuId) {
        for (WorkbenchLayoutSnapshot.Node node : nodes) {
            if (menuId.equals(node.getSourceMenuId())) return node;
            WorkbenchLayoutSnapshot.Node child = findPage(node.getChildren(), menuId);
            if (child != null) return child;
        }
        return null;
    }

    private List<WorkbenchLayoutSnapshot.Node> copyNodes(List<WorkbenchLayoutSnapshot.Node> nodes) {
        List<WorkbenchLayoutSnapshot.Node> result = new ArrayList<>();
        for (WorkbenchLayoutSnapshot.Node node : nodes) {
            result.add(WorkbenchLayoutSnapshot.Node.builder()
                    .key(node.getKey()).type(node.getType()).sourceMenuId(node.getSourceMenuId())
                    .name(node.getName()).icon(node.getIcon()).hidden(node.getHidden()).sort(node.getSort())
                    .children(copyNodes(node.getChildren())).build());
        }
        return result;
    }

}
