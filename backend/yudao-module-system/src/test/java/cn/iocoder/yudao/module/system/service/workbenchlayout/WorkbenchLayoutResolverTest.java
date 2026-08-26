package cn.iocoder.yudao.module.system.service.workbenchlayout;

import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;
import cn.iocoder.yudao.module.system.service.workbenchlayout.model.WorkbenchLayoutSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.module.system.service.workbenchlayout.model.WorkbenchLayoutSnapshot.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkbenchLayoutResolverTest {

    private WorkbenchLayoutResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new WorkbenchLayoutResolver();
    }

    @Test
    void shouldKeepResolvedSalaryPathAfterRoleMoveFromThirdLevelGroup() {
        List<MenuDO> menus = List.of(
                menu(10L, 0L, 1, "HRM 人力资源", "/hrm", "native", true),
                menu(11L, 10L, 1, "HRM 员工端", "portal", "native", true),
                menu(12L, 11L, 1, "薪酬中心", "salary", "native", true),
                menu(14L, 12L, 2, "我的工资条", "slip", "admin_embed", true));

        WorkbenchLayoutSnapshot global = resolver.createInitialGlobalSnapshot(menus);
        WorkbenchLayoutSnapshot.Node salary = findByMenuId(global.getNodes(), 14L);
        assertThat(salary).isNotNull();
        assertThat(groupDepth(global.getNodes(), salary.getKey(), 0)).isEqualTo(3);

        List<WorkbenchLayoutSnapshot.Node> roleTree = copyNodes(global.getNodes());
        WorkbenchLayoutSnapshot.Node moved = removeByMenuId(roleTree, 14L);
        roleTree.add(0, moved);
        WorkbenchLayoutSnapshot difference = resolver.normalizeRoleDraft(global,
                WorkbenchLayoutSnapshot.builder().scopeType("ROLE").enabled(true).priority(1)
                        .nodes(roleTree).build());
        WorkbenchLayoutSnapshot effective = resolver.expandRoleSnapshot(global, difference);

        WorkbenchLayoutResolver.RenderResult rendered = resolver.render(effective, menus);
        assertThat(rendered.getMenus()).anySatisfy(item -> {
            assertThat(item.getSourceMenuId()).isEqualTo(14L);
            assertThat(item.getPath()).isEqualTo("/hrm/portal/salary/slip");
        });
    }

    @Test
    void shouldRestoreGloballyHiddenPageInRoleDifference() {
        List<MenuDO> menus = List.of(menu(21L, 0L, 2, "我的工资条", "/hrm/portal/salary/slip", "native", true));
        WorkbenchLayoutSnapshot global = resolver.createInitialGlobalSnapshot(menus);
        findByMenuId(global.getNodes(), 21L).setHidden(true);

        List<WorkbenchLayoutSnapshot.Node> roleTree = copyNodes(global.getNodes());
        findByMenuId(roleTree, 21L).setHidden(false);
        WorkbenchLayoutSnapshot difference = resolver.normalizeRoleDraft(global,
                WorkbenchLayoutSnapshot.builder().scopeType("ROLE").enabled(true).priority(2)
                        .nodes(roleTree).build());

        WorkbenchLayoutResolver.RenderResult rendered = resolver.render(
                resolver.expandRoleSnapshot(global, difference), menus);
        assertThat(rendered.getMenus()).extracting("sourceMenuId").containsExactly(21L);
    }

    @Test
    void shouldPutNewAuthorizedPageIntoUnclassified() {
        MenuDO first = menu(31L, 0L, 2, "页面一", "/one", "native", true);
        WorkbenchLayoutSnapshot global = resolver.createInitialGlobalSnapshot(List.of(first));
        MenuDO added = menu(32L, 0L, 2, "页面二", "/two", "native", true);

        WorkbenchLayoutResolver.RenderResult rendered = resolver.render(global, List.of(first, added));
        assertThat(flattenMenuIds(rendered.getMenus())).containsExactlyInAnyOrder(31L, 32L);
        assertThat(rendered.getMenus()).anySatisfy(group -> {
            if (UNCLASSIFIED_NAME.equals(group.getName())) {
                assertThat(flattenMenuIds(group.getChildren())).contains(32L);
            }
        });
    }

    @Test
    void shouldNeverRestoreAdminOnlyOrSourceHiddenPage() {
        MenuDO adminOnly = menu(41L, 0L, 2, "编排管理", "/system/workbench-layout", "admin_only", true);
        MenuDO sourceHidden = menu(42L, 0L, 2, "隐藏页", "/hidden", "native", false);
        WorkbenchLayoutSnapshot snapshot = WorkbenchLayoutSnapshot.builder().scopeType("GLOBAL")
                .nodes(new ArrayList<>(List.of(
                        page(41L, false), page(42L, false), unclassified()))).build();

        WorkbenchLayoutResolver.RenderResult rendered = resolver.render(snapshot, List.of(adminOnly, sourceHidden));
        assertThat(flattenMenuIds(rendered.getMenus())).isEmpty();
        assertThat(rendered.getFilteredItems()).extracting("reason")
                .containsOnly("SOURCE_MENU_UNAVAILABLE");
    }

    @Test
    void shouldRejectDuplicatePageAndFourthGroupLevel() {
        WorkbenchLayoutSnapshot duplicate = WorkbenchLayoutSnapshot.builder().scopeType("GLOBAL")
                .nodes(new ArrayList<>(List.of(page(51L, false), page(51L, false), unclassified()))).build();
        assertThatThrownBy(() -> resolver.normalizeGlobalDraft(duplicate,
                List.of(menu(51L, 0L, 2, "重复页", "/duplicate", "native", true))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("重复");

        WorkbenchLayoutSnapshot.Node level4 = group("g4", "四级", new ArrayList<>());
        WorkbenchLayoutSnapshot.Node level3 = group("g3", "三级", new ArrayList<>(List.of(level4)));
        WorkbenchLayoutSnapshot.Node level2 = group("g2", "二级", new ArrayList<>(List.of(level3)));
        WorkbenchLayoutSnapshot.Node level1 = group("g1", "一级", new ArrayList<>(List.of(level2)));
        WorkbenchLayoutSnapshot tooDeep = WorkbenchLayoutSnapshot.builder().scopeType("GLOBAL")
                .nodes(new ArrayList<>(List.of(level1, unclassified()))).build();
        assertThatThrownBy(() -> resolver.normalizeGlobalDraft(tooDeep, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("最多三级");
    }

    @Test
    void shouldAllowEmptyGroupInDraftButRejectPublish() {
        WorkbenchLayoutSnapshot snapshot = WorkbenchLayoutSnapshot.builder().scopeType("GLOBAL")
                .nodes(new ArrayList<>(List.of(group("empty", "空分组", new ArrayList<>()), unclassified())))
                .build();
        WorkbenchLayoutSnapshot draft = resolver.normalizeGlobalDraft(snapshot, List.of());
        assertThat(draft.getNodes()).hasSize(2);
        assertThatThrownBy(() -> resolver.validateGlobalForPublish(draft, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不能为空");
    }

    @Test
    void shouldPersistDisabledRoleAsEmptyDifference() {
        List<MenuDO> menus = List.of(menu(61L, 0L, 2, "页面", "/page", "native", true));
        WorkbenchLayoutSnapshot global = resolver.createInitialGlobalSnapshot(menus);
        List<WorkbenchLayoutSnapshot.Node> roleTree = copyNodes(global.getNodes());
        findByMenuId(roleTree, 61L).setHidden(true);

        WorkbenchLayoutSnapshot difference = resolver.normalizeRoleDraft(global,
                WorkbenchLayoutSnapshot.builder().scopeType("ROLE").enabled(false).priority(9)
                        .nodes(roleTree).build());

        assertThat(difference.getEnabled()).isFalse();
        assertThat(difference.getPriority()).isNull();
        assertThat(difference.getOperations()).isEmpty();
        assertThat(resolver.expandRoleSnapshot(global, difference).getNodes())
                .usingRecursiveComparison().isEqualTo(global.getNodes());
    }

    private MenuDO menu(Long id, Long parentId, int type, String name, String path,
                        String renderMode, boolean visible) {
        return new MenuDO().setId(id).setParentId(parentId).setType(type).setName(name).setPath(path)
                .setIcon("ep:menu").setWorkbenchRenderMode(renderMode).setVisible(visible)
                .setKeepAlive(true).setAlwaysShow(true).setStatus(0).setSort(Math.toIntExact(id));
    }

    private WorkbenchLayoutSnapshot.Node page(Long id, boolean hidden) {
        return WorkbenchLayoutSnapshot.Node.builder().key("menu-" + id).type(NODE_TYPE_PAGE)
                .sourceMenuId(id).name("页面" + id).hidden(hidden).children(new ArrayList<>()).build();
    }

    private WorkbenchLayoutSnapshot.Node group(String key, String name,
                                               List<WorkbenchLayoutSnapshot.Node> children) {
        return WorkbenchLayoutSnapshot.Node.builder().key(key).type(NODE_TYPE_GROUP)
                .name(name).children(children).build();
    }

    private WorkbenchLayoutSnapshot.Node unclassified() {
        return group(UNCLASSIFIED_KEY, UNCLASSIFIED_NAME, new ArrayList<>());
    }

    private WorkbenchLayoutSnapshot.Node findByMenuId(List<WorkbenchLayoutSnapshot.Node> nodes, Long menuId) {
        for (WorkbenchLayoutSnapshot.Node node : nodes) {
            if (menuId.equals(node.getSourceMenuId())) return node;
            WorkbenchLayoutSnapshot.Node child = findByMenuId(node.getChildren(), menuId);
            if (child != null) return child;
        }
        return null;
    }

    private WorkbenchLayoutSnapshot.Node removeByMenuId(List<WorkbenchLayoutSnapshot.Node> nodes, Long menuId) {
        for (int i = 0; i < nodes.size(); i++) {
            if (menuId.equals(nodes.get(i).getSourceMenuId())) return nodes.remove(i);
            WorkbenchLayoutSnapshot.Node child = removeByMenuId(nodes.get(i).getChildren(), menuId);
            if (child != null) return child;
        }
        return null;
    }

    private int groupDepth(List<WorkbenchLayoutSnapshot.Node> nodes, String key, int depth) {
        for (WorkbenchLayoutSnapshot.Node node : nodes) {
            if (key.equals(node.getKey())) return depth;
            int found = groupDepth(node.getChildren(), key,
                    depth + (NODE_TYPE_GROUP.equals(node.getType()) ? 1 : 0));
            if (found >= 0) return found;
        }
        return -1;
    }

    private List<WorkbenchLayoutSnapshot.Node> copyNodes(List<WorkbenchLayoutSnapshot.Node> nodes) {
        return nodes.stream().map(node -> WorkbenchLayoutSnapshot.Node.builder()
                .key(node.getKey()).type(node.getType()).sourceMenuId(node.getSourceMenuId())
                .name(node.getName()).icon(node.getIcon()).hidden(node.getHidden()).sort(node.getSort())
                .children(copyNodes(node.getChildren())).build())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private List<Long> flattenMenuIds(List<cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthPermissionInfoRespVO.MenuVO> menus) {
        List<Long> ids = new ArrayList<>();
        for (var menu : menus) {
            if (menu.getSourceMenuId() != null) ids.add(menu.getSourceMenuId());
            ids.addAll(flattenMenuIds(menu.getChildren()));
        }
        return ids;
    }

}
