package cn.iocoder.yudao.module.system.service.workbenchlayout;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthPermissionInfoRespVO;
import cn.iocoder.yudao.module.system.controller.admin.workbenchlayout.vo.WorkbenchLayoutPreviewRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;
import cn.iocoder.yudao.module.system.enums.permission.MenuTypeEnum;
import cn.iocoder.yudao.module.system.service.workbenchlayout.model.WorkbenchLayoutSnapshot;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO.ID_ROOT;
import static cn.iocoder.yudao.module.system.service.workbenchlayout.model.WorkbenchLayoutSnapshot.*;

@Component
public class WorkbenchLayoutResolver {

    private static final String ADMIN_ONLY = "admin_only";

    public WorkbenchLayoutSnapshot createInitialGlobalSnapshot(List<MenuDO> tenantMenus) {
        Map<Long, MenuDO> menuMap = toMenuMap(tenantMenus);
        Map<Long, List<MenuDO>> childrenMap = buildChildrenMap(tenantMenus);
        Set<Long> eligiblePageIds = getEligiblePageIds(tenantMenus);
        List<WorkbenchLayoutSnapshot.Node> nodes = new ArrayList<>();
        for (MenuDO root : childrenMap.getOrDefault(ID_ROOT, Collections.emptyList())) {
            appendInitialNode(root, nodes, 0, childrenMap, eligiblePageIds);
        }
        // A package may omit a parent while retaining a page. Keep the page available instead of losing it.
        Set<Long> arranged = collectPageIds(nodes);
        List<WorkbenchLayoutSnapshot.Node> unclassifiedChildren = tenantMenus.stream()
                .filter(menu -> eligiblePageIds.contains(menu.getId()) && !arranged.contains(menu.getId()))
                .sorted(menuComparator())
                .map(this::pageNode)
                .toList();
        nodes.add(unclassifiedNode(new ArrayList<>(unclassifiedChildren)));
        canonicalizeSort(nodes);
        return WorkbenchLayoutSnapshot.builder().scopeType("GLOBAL").nodes(nodes).build();
    }

    public WorkbenchLayoutSnapshot normalizeGlobalDraft(WorkbenchLayoutSnapshot submitted,
                                                         List<MenuDO> tenantMenus) {
        WorkbenchLayoutSnapshot result = copySnapshot(submitted);
        result.setSchemaVersion(SCHEMA_VERSION);
        result.setScopeType("GLOBAL");
        result.setEnabled(true);
        result.setPriority(null);
        result.setOperations(new ArrayList<>());
        validateTree(result.getNodes(), false);
        WorkbenchLayoutSnapshot.Node unclassified = findNode(result.getNodes(), UNCLASSIFIED_KEY);
        if (unclassified == null || !NODE_TYPE_GROUP.equals(unclassified.getType())) {
            throw invalid("必须保留一级“未分类”分组");
        }
        Set<Long> arranged = collectPageIds(result.getNodes());
        for (MenuDO menu : getEligiblePages(tenantMenus)) {
            if (arranged.add(menu.getId())) {
                unclassified.getChildren().add(pageNode(menu));
            }
        }
        canonicalizeSort(result.getNodes());
        validateTree(result.getNodes(), false);
        return result;
    }

    public WorkbenchLayoutSnapshot normalizeRoleDraft(WorkbenchLayoutSnapshot global,
                                                       WorkbenchLayoutSnapshot submitted) {
        WorkbenchLayoutSnapshot finalTree = copySnapshot(submitted);
        finalTree.setSchemaVersion(SCHEMA_VERSION);
        finalTree.setScopeType("ROLE");
        finalTree.setEnabled(Boolean.TRUE.equals(submitted.getEnabled()));
        if (Boolean.TRUE.equals(finalTree.getEnabled())
                && (finalTree.getPriority() == null || finalTree.getPriority() < 1)) {
            throw invalid("启用角色覆盖时优先级必须大于 0");
        }
        if (!Boolean.TRUE.equals(finalTree.getEnabled())) {
            finalTree.setPriority(null);
        }
        canonicalizeSort(finalTree.getNodes());
        validateTree(finalTree.getNodes(), false);
        if (!Boolean.TRUE.equals(finalTree.getEnabled())) {
            return WorkbenchLayoutSnapshot.builder().scopeType("ROLE").enabled(false)
                    .priority(null).operations(new ArrayList<>()).nodes(new ArrayList<>()).build();
        }
        WorkbenchLayoutSnapshot persisted = toRoleDifference(global, finalTree);
        validateRoleOperations(global, persisted);
        return persisted;
    }

    public WorkbenchLayoutSnapshot expandRoleSnapshot(WorkbenchLayoutSnapshot global,
                                                       WorkbenchLayoutSnapshot roleDifference) {
        validateRoleOperations(global, roleDifference);
        WorkbenchLayoutSnapshot result = copySnapshot(global);
        result.setScopeType("ROLE");
        result.setEnabled(Boolean.TRUE.equals(roleDifference.getEnabled()));
        result.setPriority(roleDifference.getPriority());
        applyOperations(result.getNodes(), roleDifference.getOperations());
        result.setOperations(new ArrayList<>());
        canonicalizeSort(result.getNodes());
        validateTree(result.getNodes(), false);
        return result;
    }

    public void validateGlobalForPublish(WorkbenchLayoutSnapshot snapshot, List<MenuDO> tenantMenus) {
        validateTree(snapshot.getNodes(), true);
        Set<Long> expected = getEligiblePageIds(tenantMenus);
        Set<Long> actual = collectPageIds(snapshot.getNodes());
        Set<Long> unknown = new LinkedHashSet<>(actual);
        unknown.removeAll(expected);
        if (!unknown.isEmpty()) {
            throw invalid("布局引用了不可用页面：" + unknown);
        }
        Set<Long> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        if (!missing.isEmpty()) {
            throw invalid("布局缺少页面：" + missing);
        }
    }

    public void validateRoleForPublish(WorkbenchLayoutSnapshot global,
                                       WorkbenchLayoutSnapshot roleDifference,
                                       List<MenuDO> tenantMenus) {
        WorkbenchLayoutSnapshot finalTree = expandRoleSnapshot(global, roleDifference);
        validateGlobalForPublish(finalTree, tenantMenus);
    }

    public RenderResult render(WorkbenchLayoutSnapshot effectiveSnapshot,
                               List<MenuDO> authorizedMenus) {
        WorkbenchLayoutSnapshot snapshot = copySnapshot(effectiveSnapshot);
        validateTree(snapshot.getNodes(), false);
        Map<Long, MenuDO> authorizedMap = toMenuMap(authorizedMenus);
        Set<Long> eligibleAuthorizedIds = getEligiblePageIds(authorizedMenus);
        WorkbenchLayoutSnapshot.Node unclassified = findNode(snapshot.getNodes(), UNCLASSIFIED_KEY);
        if (unclassified == null) {
            throw invalid("布局缺少“未分类”分组");
        }
        Set<Long> arranged = collectPageIds(snapshot.getNodes());
        authorizedMenus.stream()
                .filter(menu -> eligibleAuthorizedIds.contains(menu.getId()) && arranged.add(menu.getId()))
                .sorted(menuComparator())
                .map(this::pageNode)
                .forEach(unclassified.getChildren()::add);
        canonicalizeSort(snapshot.getNodes());

        Map<Long, String> paths = resolvePublicPaths(authorizedMenus);
        AtomicLong syntheticId = new AtomicLong(-1L);
        List<WorkbenchLayoutPreviewRespVO.FilteredItem> filtered = new ArrayList<>();
        List<AuthPermissionInfoRespVO.MenuVO> result = renderNodes(snapshot.getNodes(), ID_ROOT,
                authorizedMap, eligibleAuthorizedIds, paths, syntheticId, filtered, false);
        return new RenderResult(result, filtered);
    }

    public List<MenuDO> getEligiblePages(List<MenuDO> menus) {
        Set<Long> eligible = getEligiblePageIds(menus);
        return menus.stream().filter(menu -> eligible.contains(menu.getId())).sorted(menuComparator()).toList();
    }

    public Map<Long, String> resolvePublicPaths(List<MenuDO> menus) {
        Map<Long, MenuDO> menuMap = toMenuMap(menus);
        Map<Long, String> result = new HashMap<>();
        for (MenuDO menu : menus) {
            resolvePublicPath(menu, menuMap, result, new HashSet<>());
        }
        return result;
    }

    private WorkbenchLayoutSnapshot toRoleDifference(WorkbenchLayoutSnapshot global,
                                                      WorkbenchLayoutSnapshot finalTree) {
        Map<String, FlatNode> base = flatten(global.getNodes());
        Map<String, FlatNode> target = flatten(finalTree.getNodes());
        for (Map.Entry<String, FlatNode> entry : base.entrySet()) {
            FlatNode targetNode = target.get(entry.getKey());
            if (targetNode == null) {
                throw invalid("角色布局不能删除全局节点：" + entry.getKey());
            }
            WorkbenchLayoutSnapshot.Node baseNode = entry.getValue().getNode();
            WorkbenchLayoutSnapshot.Node candidate = targetNode.getNode();
            if (!Objects.equals(baseNode.getType(), candidate.getType())
                    || !Objects.equals(baseNode.getSourceMenuId(), candidate.getSourceMenuId())) {
                throw invalid("角色布局不能改变全局节点身份：" + entry.getKey());
            }
            if (NODE_TYPE_GROUP.equals(baseNode.getType())
                    && (!Objects.equals(baseNode.getName(), candidate.getName())
                    || !Objects.equals(emptyToNull(baseNode.getIcon()), emptyToNull(candidate.getIcon())))) {
                throw invalid("角色布局不能重命名全局分组：" + entry.getKey());
            }
        }

        List<WorkbenchLayoutSnapshot.Operation> operations = new ArrayList<>();
        for (FlatNode targetNode : target.values()) {
            WorkbenchLayoutSnapshot.Node node = targetNode.getNode();
            FlatNode baseNode = base.get(node.getKey());
            boolean isNew = baseNode == null;
            if (isNew && !NODE_TYPE_GROUP.equals(node.getType())) {
                throw invalid("角色布局只能新增分组，不能新增页面：" + node.getKey());
            }
            boolean changed = isNew
                    || !Objects.equals(baseNode.getParentKey(), targetNode.getParentKey())
                    || !Objects.equals(baseNode.getNode().getSort(), node.getSort())
                    || !Objects.equals(Boolean.TRUE.equals(baseNode.getNode().getHidden()),
                    Boolean.TRUE.equals(node.getHidden()));
            if (!changed) {
                continue;
            }
            operations.add(WorkbenchLayoutSnapshot.Operation.builder()
                    .key(node.getKey()).type(node.getType()).sourceMenuId(node.getSourceMenuId())
                    .parentKey(targetNode.getParentKey()).sort(node.getSort())
                    .hidden(isNew || !Objects.equals(Boolean.TRUE.equals(baseNode.getNode().getHidden()),
                            Boolean.TRUE.equals(node.getHidden())) ? Boolean.TRUE.equals(node.getHidden()) : null)
                    .name(isNew ? node.getName() : null).icon(isNew ? node.getIcon() : null)
                    .build());
        }
        return WorkbenchLayoutSnapshot.builder()
                .scopeType("ROLE")
                .enabled(Boolean.TRUE.equals(finalTree.getEnabled()))
                .priority(finalTree.getPriority())
                .operations(operations)
                .nodes(new ArrayList<>())
                .build();
    }

    private void validateRoleOperations(WorkbenchLayoutSnapshot global,
                                        WorkbenchLayoutSnapshot roleDifference) {
        if (roleDifference == null || roleDifference.getSchemaVersion() == null
                || roleDifference.getSchemaVersion() != SCHEMA_VERSION
                || !"ROLE".equals(roleDifference.getScopeType())) {
            throw invalid("角色布局快照版本或作用域不正确");
        }
        if (Boolean.TRUE.equals(roleDifference.getEnabled())
                && (roleDifference.getPriority() == null || roleDifference.getPriority() < 1)) {
            throw invalid("启用角色覆盖时优先级必须大于 0");
        }
        Set<String> baseKeys = flatten(global.getNodes()).keySet();
        Set<String> operationKeys = new HashSet<>();
        Set<String> newGroupKeys = new HashSet<>();
        for (WorkbenchLayoutSnapshot.Operation operation : safeOperations(roleDifference)) {
            if (StrUtil.isBlank(operation.getKey()) || !operationKeys.add(operation.getKey())) {
                throw invalid("角色差异节点键为空或重复");
            }
            if (!baseKeys.contains(operation.getKey())) {
                if (!NODE_TYPE_GROUP.equals(operation.getType()) || StrUtil.isBlank(operation.getName())) {
                    throw invalid("角色差异引用了失效节点：" + operation.getKey());
                }
                newGroupKeys.add(operation.getKey());
            }
            if (operation.getParentKey() != null && !baseKeys.contains(operation.getParentKey())
                    && !newGroupKeys.contains(operation.getParentKey())
                    && safeOperations(roleDifference).stream()
                    .noneMatch(candidate -> Objects.equals(candidate.getKey(), operation.getParentKey())
                            && NODE_TYPE_GROUP.equals(candidate.getType()))) {
                throw invalid("角色差异引用了失效父分组：" + operation.getParentKey());
            }
        }
    }

    private void applyOperations(List<WorkbenchLayoutSnapshot.Node> roots,
                                 List<WorkbenchLayoutSnapshot.Operation> operations) {
        Map<String, FlatNode> locations = flatten(roots);
        Map<String, WorkbenchLayoutSnapshot.Node> nodes = new LinkedHashMap<>();
        locations.forEach((key, value) -> nodes.put(key, value.getNode()));
        for (WorkbenchLayoutSnapshot.Operation operation : operations) {
            if (!nodes.containsKey(operation.getKey())) {
                WorkbenchLayoutSnapshot.Node node = WorkbenchLayoutSnapshot.Node.builder()
                        .key(operation.getKey()).type(NODE_TYPE_GROUP).name(operation.getName())
                        .icon(operation.getIcon()).hidden(Boolean.TRUE.equals(operation.getHidden()))
                        .sort(operation.getSort()).children(new ArrayList<>()).build();
                nodes.put(node.getKey(), node);
            }
        }
        for (WorkbenchLayoutSnapshot.Operation operation : operations) {
            FlatNode location = locations.get(operation.getKey());
            if (location == null) {
                continue;
            }
            if (location.getParentKey() == null) {
                roots.remove(location.getNode());
            } else {
                nodes.get(location.getParentKey()).getChildren().remove(location.getNode());
            }
        }
        for (WorkbenchLayoutSnapshot.Operation operation : operations) {
            WorkbenchLayoutSnapshot.Node node = nodes.get(operation.getKey());
            if (operation.getHidden() != null) {
                node.setHidden(operation.getHidden());
            }
            node.setSort(operation.getSort());
            if (operation.getParentKey() == null) {
                roots.add(node);
            } else {
                WorkbenchLayoutSnapshot.Node parent = nodes.get(operation.getParentKey());
                if (parent == null || !NODE_TYPE_GROUP.equals(parent.getType()) || containsNode(node, parent.getKey())) {
                    throw invalid("角色差异形成循环或引用非分组父节点：" + operation.getKey());
                }
                parent.getChildren().add(node);
            }
        }
        sortByDeclaredOrder(roots);
    }

    private List<AuthPermissionInfoRespVO.MenuVO> renderNodes(
            List<WorkbenchLayoutSnapshot.Node> nodes, Long parentId,
            Map<Long, MenuDO> authorizedMap, Set<Long> eligibleAuthorizedIds,
            Map<Long, String> paths, AtomicLong syntheticId,
            List<WorkbenchLayoutPreviewRespVO.FilteredItem> filtered, boolean parentHidden) {
        List<AuthPermissionInfoRespVO.MenuVO> result = new ArrayList<>();
        for (WorkbenchLayoutSnapshot.Node node : nodes) {
            boolean hidden = parentHidden || Boolean.TRUE.equals(node.getHidden());
            if (NODE_TYPE_PAGE.equals(node.getType())) {
                MenuDO menu = authorizedMap.get(node.getSourceMenuId());
                if (menu == null) {
                    filtered.add(filtered(node, null, "NOT_AUTHORIZED"));
                    continue;
                }
                if (!eligibleAuthorizedIds.contains(menu.getId())) {
                    filtered.add(filtered(node, menu, "SOURCE_MENU_UNAVAILABLE"));
                    continue;
                }
                if (hidden) {
                    filtered.add(filtered(node, menu, "NAVIGATION_HIDDEN"));
                    continue;
                }
                result.add(AuthPermissionInfoRespVO.MenuVO.builder()
                        .id(menu.getId()).sourceMenuId(menu.getId()).parentId(parentId)
                        .name(menu.getName()).path(paths.get(menu.getId()))
                        .component(menu.getComponent()).componentName(menu.getComponentName())
                        .workbenchRenderMode(menu.getWorkbenchRenderMode()).icon(menu.getIcon())
                        .visible(true).keepAlive(menu.getKeepAlive()).alwaysShow(menu.getAlwaysShow())
                        .children(Collections.emptyList()).build());
                continue;
            }
            long groupId = syntheticId.getAndDecrement();
            List<AuthPermissionInfoRespVO.MenuVO> children = renderNodes(node.getChildren(), groupId,
                    authorizedMap, eligibleAuthorizedIds, paths, syntheticId, filtered, hidden);
            if (hidden || children.isEmpty()) {
                continue;
            }
            result.add(AuthPermissionInfoRespVO.MenuVO.builder()
                    .id(groupId).layoutKey(node.getKey()).parentId(parentId).name(node.getName())
                    .path("/__workbench-group/" + safePathSegment(node.getKey()))
                    .workbenchRenderMode("native").icon(node.getIcon()).visible(true)
                    .keepAlive(false).alwaysShow(true).children(children).build());
        }
        return result;
    }

    private WorkbenchLayoutPreviewRespVO.FilteredItem filtered(WorkbenchLayoutSnapshot.Node node,
                                                                MenuDO menu, String reason) {
        return WorkbenchLayoutPreviewRespVO.FilteredItem.builder()
                .sourceMenuId(node.getSourceMenuId())
                .name(menu == null ? node.getName() : menu.getName())
                .reason(reason).build();
    }

    private void validateTree(List<WorkbenchLayoutSnapshot.Node> roots, boolean rejectEmptyGroups) {
        if (roots == null) {
            throw invalid("布局节点不能为空");
        }
        Set<String> keys = new HashSet<>();
        Set<Long> pages = new HashSet<>();
        int unclassifiedCount = 0;
        for (WorkbenchLayoutSnapshot.Node node : roots) {
            if (UNCLASSIFIED_KEY.equals(node.getKey())) {
                unclassifiedCount++;
            }
            validateNode(node, 0, true, rejectEmptyGroups, keys, pages);
        }
        if (unclassifiedCount != 1) {
            throw invalid("必须且只能保留一个一级“未分类”分组");
        }
    }

    private void validateNode(WorkbenchLayoutSnapshot.Node node, int groupDepth, boolean root,
                              boolean rejectEmptyGroups, Set<String> keys, Set<Long> pages) {
        if (node == null || StrUtil.isBlank(node.getKey()) || !keys.add(node.getKey())) {
            throw invalid("布局节点键为空或重复");
        }
        node.setChildren(node.getChildren() == null ? new ArrayList<>() : node.getChildren());
        node.setHidden(Boolean.TRUE.equals(node.getHidden()));
        if (NODE_TYPE_PAGE.equals(node.getType())) {
            if (node.getSourceMenuId() == null || !pages.add(node.getSourceMenuId())) {
                throw invalid("页面引用为空或重复：" + node.getSourceMenuId());
            }
            if (!node.getChildren().isEmpty()) {
                throw invalid("页面节点不能包含子节点：" + node.getKey());
            }
            return;
        }
        if (!NODE_TYPE_GROUP.equals(node.getType()) || StrUtil.isBlank(node.getName())) {
            throw invalid("分组类型或名称不正确：" + node.getKey());
        }
        int nextDepth = groupDepth + 1;
        if (nextDepth > 3) {
            throw invalid("分组最多三级：" + node.getKey());
        }
        if (UNCLASSIFIED_KEY.equals(node.getKey())) {
            if (!root || !UNCLASSIFIED_NAME.equals(node.getName())) {
                throw invalid("“未分类”必须是固定名称的一级分组");
            }
            if (node.getChildren().stream().anyMatch(child -> NODE_TYPE_GROUP.equals(child.getType()))) {
                throw invalid("“未分类”不能包含子分组");
            }
        } else if (rejectEmptyGroups && node.getChildren().isEmpty()) {
            throw invalid("普通分组不能为空：" + node.getName());
        }
        for (WorkbenchLayoutSnapshot.Node child : node.getChildren()) {
            validateNode(child, nextDepth, false, rejectEmptyGroups, keys, pages);
        }
    }

    private void appendInitialNode(MenuDO menu, List<WorkbenchLayoutSnapshot.Node> target,
                                   int groupDepth, Map<Long, List<MenuDO>> childrenMap,
                                   Set<Long> eligiblePageIds) {
        if (MenuTypeEnum.MENU.getType().equals(menu.getType())) {
            if (eligiblePageIds.contains(menu.getId())) {
                target.add(pageNode(menu));
            }
            for (MenuDO child : childrenMap.getOrDefault(menu.getId(), Collections.emptyList())) {
                appendInitialNode(child, target, groupDepth, childrenMap, eligiblePageIds);
            }
            return;
        }
        if (!MenuTypeEnum.DIR.getType().equals(menu.getType())) {
            return;
        }
        if (groupDepth >= 3) {
            for (MenuDO child : childrenMap.getOrDefault(menu.getId(), Collections.emptyList())) {
                appendInitialNode(child, target, groupDepth, childrenMap, eligiblePageIds);
            }
            return;
        }
        List<WorkbenchLayoutSnapshot.Node> children = new ArrayList<>();
        for (MenuDO child : childrenMap.getOrDefault(menu.getId(), Collections.emptyList())) {
            appendInitialNode(child, children, groupDepth + 1, childrenMap, eligiblePageIds);
        }
        if (!children.isEmpty()) {
            target.add(WorkbenchLayoutSnapshot.Node.builder()
                    .key("source-group-" + menu.getId()).type(NODE_TYPE_GROUP)
                    .name(menu.getName()).icon(menu.getIcon()).children(children).build());
        }
    }

    private Set<Long> getEligiblePageIds(List<MenuDO> menus) {
        Map<Long, MenuDO> map = toMenuMap(menus);
        Set<Long> result = new LinkedHashSet<>();
        for (MenuDO menu : menus) {
            if (MenuTypeEnum.MENU.getType().equals(menu.getType())
                    && isWorkbenchVisible(menu, map, new HashSet<>())) {
                result.add(menu.getId());
            }
        }
        return result;
    }

    private boolean isWorkbenchVisible(MenuDO menu, Map<Long, MenuDO> map, Set<Long> visiting) {
        if (!visiting.add(menu.getId())) {
            return false;
        }
        if (!CommonStatusEnum.ENABLE.getStatus().equals(menu.getStatus())
                || !Boolean.TRUE.equals(menu.getVisible())
                || ADMIN_ONLY.equals(menu.getWorkbenchRenderMode())) {
            return false;
        }
        if (ID_ROOT.equals(menu.getParentId())) {
            return true;
        }
        MenuDO parent = map.get(menu.getParentId());
        return parent != null && isWorkbenchVisible(parent, map, visiting);
    }

    private String resolvePublicPath(MenuDO menu, Map<Long, MenuDO> menuMap,
                                     Map<Long, String> cache, Set<Long> visiting) {
        String cached = cache.get(menu.getId());
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(menu.getId())) {
            throw invalid("原菜单存在父级循环：" + menu.getId());
        }
        String path = StrUtil.blankToDefault(menu.getPath(), "");
        if (path.matches("(?i)^https?://.*")) {
            cache.put(menu.getId(), path);
            return path;
        }
        String parentPath = "/";
        if (!ID_ROOT.equals(menu.getParentId())) {
            MenuDO parent = menuMap.get(menu.getParentId());
            if (parent != null) {
                parentPath = resolvePublicPath(parent, menuMap, cache, visiting);
            }
        }
        if (path.isEmpty()) {
            cache.put(menu.getId(), parentPath);
            return parentPath;
        }
        String resolved = (parentPath + (path.startsWith("/") ? path : "/" + path))
                .replaceAll("/{2,}", "/");
        cache.put(menu.getId(), resolved);
        return resolved;
    }

    private WorkbenchLayoutSnapshot.Node pageNode(MenuDO menu) {
        return WorkbenchLayoutSnapshot.Node.builder()
                .key("menu-" + menu.getId()).type(NODE_TYPE_PAGE)
                .sourceMenuId(menu.getId()).name(menu.getName()).icon(menu.getIcon())
                .children(new ArrayList<>()).build();
    }

    private WorkbenchLayoutSnapshot.Node unclassifiedNode(List<WorkbenchLayoutSnapshot.Node> children) {
        return WorkbenchLayoutSnapshot.Node.builder()
                .key(UNCLASSIFIED_KEY).type(NODE_TYPE_GROUP).name(UNCLASSIFIED_NAME)
                .icon("ep:folder").children(children).build();
    }

    private Map<Long, List<MenuDO>> buildChildrenMap(List<MenuDO> menus) {
        Map<Long, List<MenuDO>> result = new HashMap<>();
        for (MenuDO menu : menus) {
            if (!MenuTypeEnum.BUTTON.getType().equals(menu.getType())) {
                result.computeIfAbsent(menu.getParentId(), ignored -> new ArrayList<>()).add(menu);
            }
        }
        result.values().forEach(children -> children.sort(menuComparator()));
        return result;
    }

    private Comparator<MenuDO> menuComparator() {
        return Comparator.comparing(MenuDO::getSort, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(MenuDO::getId);
    }

    private Map<Long, MenuDO> toMenuMap(List<MenuDO> menus) {
        Map<Long, MenuDO> result = new LinkedHashMap<>();
        menus.forEach(menu -> result.put(menu.getId(), menu));
        return result;
    }

    private Set<Long> collectPageIds(List<WorkbenchLayoutSnapshot.Node> nodes) {
        Set<Long> result = new LinkedHashSet<>();
        collectPageIds(nodes, result);
        return result;
    }

    private void collectPageIds(List<WorkbenchLayoutSnapshot.Node> nodes, Set<Long> result) {
        for (WorkbenchLayoutSnapshot.Node node : nodes) {
            if (NODE_TYPE_PAGE.equals(node.getType())) {
                result.add(node.getSourceMenuId());
            } else {
                collectPageIds(node.getChildren(), result);
            }
        }
    }

    private Map<String, FlatNode> flatten(List<WorkbenchLayoutSnapshot.Node> roots) {
        Map<String, FlatNode> result = new LinkedHashMap<>();
        flatten(roots, null, result);
        return result;
    }

    private void flatten(List<WorkbenchLayoutSnapshot.Node> nodes, String parentKey,
                         Map<String, FlatNode> result) {
        for (WorkbenchLayoutSnapshot.Node node : nodes) {
            result.put(node.getKey(), new FlatNode(node, parentKey));
            flatten(node.getChildren(), node.getKey(), result);
        }
    }

    private WorkbenchLayoutSnapshot.Node findNode(List<WorkbenchLayoutSnapshot.Node> nodes, String key) {
        for (WorkbenchLayoutSnapshot.Node node : nodes) {
            if (Objects.equals(node.getKey(), key)) {
                return node;
            }
            WorkbenchLayoutSnapshot.Node child = findNode(node.getChildren(), key);
            if (child != null) {
                return child;
            }
        }
        return null;
    }

    private boolean containsNode(WorkbenchLayoutSnapshot.Node root, String key) {
        if (Objects.equals(root.getKey(), key)) {
            return true;
        }
        return root.getChildren().stream().anyMatch(child -> containsNode(child, key));
    }

    private void canonicalizeSort(List<WorkbenchLayoutSnapshot.Node> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            WorkbenchLayoutSnapshot.Node node = nodes.get(i);
            node.setSort((i + 1) * 10);
            canonicalizeSort(node.getChildren());
        }
    }

    private void sortByDeclaredOrder(List<WorkbenchLayoutSnapshot.Node> nodes) {
        nodes.sort(Comparator.comparing(WorkbenchLayoutSnapshot.Node::getSort,
                Comparator.nullsLast(Integer::compareTo)));
        nodes.forEach(node -> sortByDeclaredOrder(node.getChildren()));
    }

    private WorkbenchLayoutSnapshot copySnapshot(WorkbenchLayoutSnapshot source) {
        if (source == null) {
            throw invalid("布局快照不能为空");
        }
        return WorkbenchLayoutSnapshot.builder()
                .schemaVersion(source.getSchemaVersion()).scopeType(source.getScopeType())
                .enabled(source.getEnabled()).priority(source.getPriority())
                .nodes(copyNodes(source.getNodes())).operations(copyOperations(source.getOperations()))
                .build();
    }

    private List<WorkbenchLayoutSnapshot.Node> copyNodes(List<WorkbenchLayoutSnapshot.Node> nodes) {
        if (nodes == null) {
            return new ArrayList<>();
        }
        List<WorkbenchLayoutSnapshot.Node> result = new ArrayList<>();
        for (WorkbenchLayoutSnapshot.Node node : nodes) {
            result.add(WorkbenchLayoutSnapshot.Node.builder()
                    .key(node.getKey()).type(node.getType()).sourceMenuId(node.getSourceMenuId())
                    .name(node.getName()).icon(node.getIcon()).hidden(node.getHidden()).sort(node.getSort())
                    .children(copyNodes(node.getChildren())).build());
        }
        return result;
    }

    private List<WorkbenchLayoutSnapshot.Operation> copyOperations(
            List<WorkbenchLayoutSnapshot.Operation> operations) {
        if (operations == null) {
            return new ArrayList<>();
        }
        return operations.stream().map(operation -> WorkbenchLayoutSnapshot.Operation.builder()
                .key(operation.getKey()).type(operation.getType()).sourceMenuId(operation.getSourceMenuId())
                .parentKey(operation.getParentKey()).sort(operation.getSort()).hidden(operation.getHidden())
                .name(operation.getName()).icon(operation.getIcon()).build()).toList();
    }

    private List<WorkbenchLayoutSnapshot.Operation> safeOperations(WorkbenchLayoutSnapshot snapshot) {
        return snapshot.getOperations() == null ? Collections.emptyList() : snapshot.getOperations();
    }

    private String emptyToNull(String value) {
        return StrUtil.blankToDefault(value, null);
    }

    private String safePathSegment(String key) {
        return key.replaceAll("[^A-Za-z0-9_-]", "-");
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    @Data
    @AllArgsConstructor
    private static class FlatNode {
        private WorkbenchLayoutSnapshot.Node node;
        private String parentKey;
    }

    @Data
    @AllArgsConstructor
    public static class RenderResult {
        private List<AuthPermissionInfoRespVO.MenuVO> menus;
        private List<WorkbenchLayoutPreviewRespVO.FilteredItem> filteredItems;
    }

}
