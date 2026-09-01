package cn.iocoder.yudao.module.system.service.workbenchlayout;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthPermissionInfoRespVO;
import cn.iocoder.yudao.module.system.controller.admin.permission.vo.menu.MenuListReqVO;
import cn.iocoder.yudao.module.system.controller.admin.workbenchlayout.vo.*;
import cn.iocoder.yudao.module.system.convert.auth.AuthConvert;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.dataobject.workbenchlayout.WorkbenchLayoutDO;
import cn.iocoder.yudao.module.system.dal.dataobject.workbenchlayout.WorkbenchLayoutVersionDO;
import cn.iocoder.yudao.module.system.dal.mysql.workbenchlayout.WorkbenchLayoutMapper;
import cn.iocoder.yudao.module.system.dal.mysql.workbenchlayout.WorkbenchLayoutVersionMapper;
import cn.iocoder.yudao.module.system.enums.workbenchlayout.WorkbenchLayoutScopeTypeEnum;
import cn.iocoder.yudao.module.system.service.permission.MenuService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.permission.RoleService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import cn.iocoder.yudao.module.system.service.workbenchlayout.WorkbenchLayoutResolver.RenderResult;
import cn.iocoder.yudao.module.system.service.workbenchlayout.model.WorkbenchLayoutSnapshot;
import cn.iocoder.yudao.module.system.service.workbenchlayout.model.WorkbenchMenuProjection;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

@Service
@Slf4j
public class WorkbenchLayoutServiceImpl implements WorkbenchLayoutService {

    private static final long GLOBAL_SCOPE_ID = 0L;

    @Resource
    private WorkbenchLayoutMapper layoutMapper;
    @Resource
    private WorkbenchLayoutVersionMapper versionMapper;
    @Resource
    private WorkbenchLayoutResolver resolver;
    @Resource
    private MenuService menuService;
    @Resource
    private RoleService roleService;
    @Resource
    private PermissionService permissionService;
    @Resource
    private AdminUserService userService;

    @Override
    public WorkbenchLayoutCandidateRespVO getCandidates() {
        List<MenuDO> tenantMenus = getTenantMenus();
        Map<Long, String> paths = resolver.resolvePublicPaths(tenantMenus);
        List<WorkbenchLayoutCandidateRespVO.Page> pages = resolver.getEligiblePages(tenantMenus).stream()
                .map(menu -> WorkbenchLayoutCandidateRespVO.Page.builder()
                        .sourceMenuId(menu.getId()).name(menu.getName()).icon(menu.getIcon())
                        .path(paths.get(menu.getId())).workbenchRenderMode(menu.getWorkbenchRenderMode()).build())
                .toList();
        Map<Long, WorkbenchLayoutDO> roleLayouts = new HashMap<>();
        layoutMapper.selectListByScopeType(WorkbenchLayoutScopeTypeEnum.ROLE.getType())
                .forEach(layout -> roleLayouts.put(layout.getScopeId(), layout));
        List<WorkbenchLayoutCandidateRespVO.Role> roles = roleService.getRoleList().stream()
                .sorted(Comparator.comparing(RoleDO::getSort).thenComparing(RoleDO::getId))
                .map(role -> {
                    WorkbenchLayoutDO layout = roleLayouts.get(role.getId());
                    return WorkbenchLayoutCandidateRespVO.Role.builder()
                            .id(role.getId()).name(role.getName()).code(role.getCode()).status(role.getStatus())
                            .publishedVersionNo(layout == null ? null : layout.getPublishedVersionNo())
                            .publishedEnabled(layout == null ? null : layout.getPublishedEnabled())
                            .publishedPriority(layout == null ? null : layout.getPublishedPriority()).build();
                }).toList();
        return WorkbenchLayoutCandidateRespVO.builder().pages(pages).roles(roles).build();
    }

    @Override
    public WorkbenchLayoutDraftRespVO getDraft(String scopeType, Long scopeId) {
        Scope scope = validateScope(scopeType, scopeId);
        WorkbenchLayoutDO layout = layoutMapper.selectByScope(scope.getType(), scope.getId());
        List<MenuDO> roleMenus = scope.isGlobal() ? Collections.emptyList()
                : getAuthorizedMenus(Set.of(scope.getId()));
        WorkbenchLayoutSnapshot snapshot;
        int revision;
        if (layout == null) {
            revision = 0;
            if (scope.isGlobal()) {
                snapshot = resolver.createInitialGlobalSnapshot(getTenantMenus());
            } else {
                WorkbenchLayoutSnapshot global = getPublishedGlobalSnapshot();
                snapshot = resolver.createInitialRoleSnapshot(global, roleMenus);
            }
        } else {
            revision = layout.getDraftRevision();
            WorkbenchLayoutSnapshot persisted = parseSnapshot(layout.getDraftSnapshotJson());
            snapshot = scope.isGlobal() ? persisted
                    : resolver.reconcileRoleSnapshot(getPublishedGlobalSnapshot(), persisted, roleMenus);
        }
        return WorkbenchLayoutDraftRespVO.builder()
                .scopeType(scope.getType()).scopeId(scope.getId()).draftRevision(revision)
                .snapshot(snapshot).candidatePages(scope.isGlobal()
                        ? buildCandidatePages(getTenantMenus()) : buildCandidatePages(roleMenus))
                .publishedVersionId(layout == null ? null : layout.getPublishedVersionId())
                .publishedVersionNo(layout == null ? null : layout.getPublishedVersionNo())
                .publishedEnabled(layout == null ? null : layout.getPublishedEnabled())
                .publishedPriority(layout == null ? null : layout.getPublishedPriority()).build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer saveDraft(WorkbenchLayoutSaveReqVO reqVO) {
        Scope scope = validateScope(reqVO.getScopeType(), reqVO.getScopeId());
        WorkbenchLayoutSnapshot persisted;
        try {
            if (scope.isGlobal()) {
                persisted = resolver.normalizeGlobalDraft(reqVO.getSnapshot(), getTenantMenus());
            } else {
                persisted = resolver.normalizeRoleDraft(getPublishedGlobalSnapshot(), reqVO.getSnapshot(),
                        getAuthorizedMenus(Set.of(scope.getId())));
            }
        } catch (IllegalArgumentException ex) {
            throw exception(WORKBENCH_LAYOUT_SNAPSHOT_INVALID, ex.getMessage());
        }
        String json = JsonUtils.toJsonString(persisted);
        WorkbenchLayoutDO current = layoutMapper.selectByScope(scope.getType(), scope.getId());
        if (current == null) {
            if (reqVO.getDraftRevision() != 0) {
                throw exception(WORKBENCH_LAYOUT_REVISION_CONFLICT);
            }
            try {
                layoutMapper.insert(new WorkbenchLayoutDO().setScopeType(scope.getType()).setScopeId(scope.getId())
                        .setDraftSnapshotJson(json).setDraftRevision(1));
                return 1;
            } catch (DuplicateKeyException ex) {
                throw exception(WORKBENCH_LAYOUT_REVISION_CONFLICT);
            }
        }
        if (!Objects.equals(current.getDraftRevision(), reqVO.getDraftRevision())
                || layoutMapper.updateDraft(current.getId(), reqVO.getDraftRevision(), json,
                current.getDraftRestoredFromVersionId()) == 0) {
            throw exception(WORKBENCH_LAYOUT_REVISION_CONFLICT);
        }
        return reqVO.getDraftRevision() + 1;
    }

    @Override
    public WorkbenchLayoutPreviewRespVO preview(WorkbenchLayoutPreviewReqVO reqVO) {
        AdminUserDO user = userService.getUser(reqVO.getUserId());
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }
        Set<Long> roleIds = permissionService.getUserRoleIdListByUserId(user.getId());
        List<RoleDO> roles = roleService.getRoleList(roleIds);
        roles.removeIf(role -> !CommonStatusEnum.ENABLE.getStatus().equals(role.getStatus()));
        roleIds = convertSet(roles, RoleDO::getId);
        List<MenuDO> authorizedMenus = getAuthorizedMenus(roleIds);
        Set<String> permissions = convertSet(authorizedMenus, MenuDO::getPermission);

        ResolvedSnapshot resolved;
        try {
            resolved = resolvePreviewSnapshot(roleIds, reqVO);
            if (resolved.getSnapshot() == null) {
                return fallbackPreview(user, roleIds, permissions, authorizedMenus, resolved.getFallbackReason());
            }
            RenderResult rendered = resolver.render(resolved.getSnapshot(), authorizedMenus);
            return WorkbenchLayoutPreviewRespVO.builder()
                    .userId(user.getId()).userName(user.getNickname()).roleIds(roleIds).permissions(permissions)
                    .finalTree(rendered.getMenus()).filteredItems(rendered.getFilteredItems())
                    .meta(resolved.getMeta()).build();
        } catch (IllegalArgumentException ex) {
            throw exception(WORKBENCH_LAYOUT_SNAPSHOT_INVALID, ex.getMessage());
        }
    }

    @Override
    public WorkbenchLayoutImpactRespVO getPublishImpact(String scopeType, Long scopeId) {
        Scope scope = validateScope(scopeType, scopeId);
        WorkbenchLayoutDO layout = layoutMapper.selectByScope(scope.getType(), scope.getId());
        if (layout == null) {
            throw exception(WORKBENCH_LAYOUT_NOT_EXISTS);
        }
        return calculatePublishImpact(scope, parseSnapshot(layout.getDraftSnapshotJson()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publish(WorkbenchLayoutPublishReqVO reqVO, Long publisherUserId) {
        Scope scope = validateScope(reqVO.getScopeType(), reqVO.getScopeId());
        WorkbenchLayoutDO selected = layoutMapper.selectByScope(scope.getType(), scope.getId());
        if (selected == null) {
            throw exception(WORKBENCH_LAYOUT_NOT_EXISTS);
        }
        WorkbenchLayoutDO layout = layoutMapper.selectByIdForUpdate(selected.getId());
        if (layout == null || !Objects.equals(layout.getDraftRevision(), reqVO.getDraftRevision())) {
            throw exception(WORKBENCH_LAYOUT_REVISION_CONFLICT);
        }
        WorkbenchLayoutSnapshot snapshot = parseSnapshot(layout.getDraftSnapshotJson());
        snapshot = scope.isGlobal() ? resolver.normalizeGlobalDraft(snapshot, getTenantMenus())
                : resolver.normalizeRoleDraft(getPublishedGlobalSnapshot(), snapshot,
                getAuthorizedMenus(Set.of(scope.getId())));
        WorkbenchLayoutImpactRespVO impact = calculatePublishImpact(scope, snapshot);
        if (!Boolean.TRUE.equals(impact.getPublishable())) {
            String messages = impact.getIssues().stream().map(WorkbenchLayoutImpactRespVO.Issue::getMessage)
                    .distinct().reduce((left, right) -> left + "；" + right).orElse("结构校验失败");
            throw exception(WORKBENCH_LAYOUT_PUBLISH_BLOCKED, messages);
        }
        if (!scope.isGlobal() && Boolean.TRUE.equals(snapshot.getEnabled())) {
            validatePublishedPriority(scope.getId(), snapshot.getPriority());
        }
        int versionNo = Optional.ofNullable(layout.getPublishedVersionNo()).orElse(0) + 1;
        WorkbenchLayoutVersionDO version = new WorkbenchLayoutVersionDO()
                .setLayoutId(layout.getId()).setScopeType(scope.getType()).setScopeId(scope.getId())
                .setVersionNo(versionNo).setSnapshotJson(JsonUtils.toJsonString(snapshot))
                .setEnabled(scope.isGlobal() || Boolean.TRUE.equals(snapshot.getEnabled()))
                .setPriority(scope.isGlobal() ? null : snapshot.getPriority())
                .setPublishRemark(StrUtil.trim(reqVO.getPublishRemark()))
                .setRestoredFromVersionId(layout.getDraftRestoredFromVersionId())
                .setPublisherUserId(publisherUserId).setPublishTime(LocalDateTime.now());
        try {
            versionMapper.insert(version);
            int updated = layoutMapper.updatePublished(layout.getId(), reqVO.getDraftRevision(), version.getId(),
                    versionNo, version.getEnabled(), version.getPriority());
            if (updated == 0) {
                throw exception(WORKBENCH_LAYOUT_REVISION_CONFLICT);
            }
        } catch (DuplicateKeyException ex) {
            if (!scope.isGlobal() && Boolean.TRUE.equals(snapshot.getEnabled())) {
                throw exception(WORKBENCH_LAYOUT_PRIORITY_DUPLICATE, snapshot.getPriority(), "其他角色");
            }
            throw exception(WORKBENCH_LAYOUT_REVISION_CONFLICT);
        }
        return version.getId();
    }

    @Override
    public List<WorkbenchLayoutVersionRespVO> getVersions(String scopeType, Long scopeId) {
        Scope scope = validateScope(scopeType, scopeId);
        WorkbenchLayoutDO layout = layoutMapper.selectByScope(scope.getType(), scope.getId());
        if (layout == null) {
            return Collections.emptyList();
        }
        return versionMapper.selectListByLayoutId(layout.getId()).stream()
                .map(version -> WorkbenchLayoutVersionRespVO.builder()
                        .id(version.getId()).versionNo(version.getVersionNo())
                        .enabled(version.getEnabled()).priority(version.getPriority())
                        .publishRemark(version.getPublishRemark())
                        .restoredFromVersionId(version.getRestoredFromVersionId())
                        .publisherUserId(version.getPublisherUserId()).publishTime(version.getPublishTime()).build())
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer restoreDraft(WorkbenchLayoutRestoreReqVO reqVO) {
        Scope scope = validateScope(reqVO.getScopeType(), reqVO.getScopeId());
        WorkbenchLayoutDO selected = layoutMapper.selectByScope(scope.getType(), scope.getId());
        if (selected == null) {
            throw exception(WORKBENCH_LAYOUT_NOT_EXISTS);
        }
        WorkbenchLayoutDO layout = layoutMapper.selectByIdForUpdate(selected.getId());
        if (layout == null || !Objects.equals(layout.getDraftRevision(), reqVO.getDraftRevision())) {
            throw exception(WORKBENCH_LAYOUT_REVISION_CONFLICT);
        }
        WorkbenchLayoutVersionDO version = versionMapper.selectById(reqVO.getVersionId());
        if (version == null || !Objects.equals(version.getLayoutId(), layout.getId())) {
            throw exception(WORKBENCH_LAYOUT_VERSION_NOT_EXISTS);
        }
        WorkbenchLayoutSnapshot restored = parseSnapshot(version.getSnapshotJson());
        restored = scope.isGlobal() ? resolver.normalizeGlobalDraft(restored, getTenantMenus())
                : resolver.normalizeRoleDraft(getPublishedGlobalSnapshot(), restored,
                getAuthorizedMenus(Set.of(scope.getId())));
        if (layoutMapper.updateDraft(layout.getId(), reqVO.getDraftRevision(), JsonUtils.toJsonString(restored),
                version.getId()) == 0) {
            throw exception(WORKBENCH_LAYOUT_REVISION_CONFLICT);
        }
        return reqVO.getDraftRevision() + 1;
    }

    @Override
    public WorkbenchMenuProjection getProjection(Set<Long> roleIds, List<MenuDO> authorizedMenus) {
        try {
            ResolvedSnapshot resolved = resolvePublishedSnapshot(roleIds);
            if (resolved.getSnapshot() == null) {
                return fallbackProjection(resolved.getFallbackReason());
            }
            RenderResult rendered = resolver.render(resolved.getSnapshot(), authorizedMenus);
            return WorkbenchMenuProjection.builder().menus(rendered.getMenus()).meta(resolved.getMeta()).build();
        } catch (Exception ex) {
            log.error("[getProjection][Workbench 布局读取或解析失败，回退原授权菜单树]", ex);
            return fallbackProjection("LAYOUT_READ_OR_PARSE_FAILED");
        }
    }

    private WorkbenchLayoutImpactRespVO calculatePublishImpact(Scope scope,
                                                                WorkbenchLayoutSnapshot draft) {
        List<WorkbenchLayoutImpactRespVO.Issue> issues = new ArrayList<>();
        List<MenuDO> tenantMenus = getTenantMenus();
        if (scope.isGlobal()) {
            try {
                resolver.validateGlobalForPublish(draft, tenantMenus);
            } catch (IllegalArgumentException ex) {
                issues.add(issue(null, null, ex.getMessage()));
            }
            List<WorkbenchLayoutDO> roleLayouts = layoutMapper.selectListByScopeType(
                    WorkbenchLayoutScopeTypeEnum.ROLE.getType()).stream()
                    .filter(layout -> layout.getPublishedVersionId() != null
                            && Boolean.TRUE.equals(layout.getPublishedEnabled())).toList();
            Map<Long, RoleDO> roleMap = new HashMap<>();
            roleService.getRoleList().forEach(role -> roleMap.put(role.getId(), role));
            for (WorkbenchLayoutDO roleLayout : roleLayouts) {
                try {
                    WorkbenchLayoutVersionDO roleVersion = versionMapper.selectById(roleLayout.getPublishedVersionId());
                    if (roleVersion == null) {
                        throw new IllegalArgumentException("角色发布版本不存在");
                    }
                    resolver.validateRoleSnapshotForPublish(draft, parseSnapshot(roleVersion.getSnapshotJson()),
                            getAuthorizedMenus(Set.of(roleLayout.getScopeId())));
                } catch (Exception ex) {
                    RoleDO role = roleMap.get(roleLayout.getScopeId());
                    issues.add(issue(roleLayout.getScopeId(), role == null ? null : role.getName(), ex.getMessage()));
                }
            }
            return WorkbenchLayoutImpactRespVO.builder().publishable(issues.isEmpty())
                    .affectedRoleCount(roleLayouts.size()).issues(issues).build();
        }
        try {
            if (Boolean.TRUE.equals(draft.getEnabled())) {
                resolver.validateRoleSnapshotForPublish(getPublishedGlobalSnapshot(), draft,
                        getAuthorizedMenus(Set.of(scope.getId())));
                validatePublishedPriority(scope.getId(), draft.getPriority());
            }
        } catch (Exception ex) {
            issues.add(issue(scope.getId(), Optional.ofNullable(roleService.getRole(scope.getId()))
                    .map(RoleDO::getName).orElse(null), ex.getMessage()));
        }
        return WorkbenchLayoutImpactRespVO.builder().publishable(issues.isEmpty())
                .affectedRoleCount(1).issues(issues).build();
    }

    private ResolvedSnapshot resolvePublishedSnapshot(Set<Long> roleIds) {
        WorkbenchLayoutDO globalLayout = layoutMapper.selectByScope(
                WorkbenchLayoutScopeTypeEnum.GLOBAL.getType(), GLOBAL_SCOPE_ID);
        if (globalLayout == null || globalLayout.getPublishedVersionId() == null) {
            return new ResolvedSnapshot(null, null, "GLOBAL_LAYOUT_NOT_PUBLISHED");
        }
        WorkbenchLayoutVersionDO globalVersion = versionMapper.selectById(globalLayout.getPublishedVersionId());
        if (globalVersion == null) {
            return new ResolvedSnapshot(null, null, "GLOBAL_VERSION_MISSING");
        }
        WorkbenchLayoutSnapshot global = parseSnapshot(globalVersion.getSnapshotJson());
        List<RoleCandidate> candidates = loadPublishedRoleCandidates(roleIds, global);
        WorkbenchLayoutSnapshot effective = resolver.mergeRoleSnapshots(global,
                candidates.stream().map(RoleCandidate::getSnapshot).toList());
        WorkbenchMenuProjection.Meta meta = WorkbenchMenuProjection.Meta.builder()
                .globalVersionId(globalVersion.getId()).globalVersionNo(globalVersion.getVersionNo())
                .appliedRoleLayouts(toAppliedRoleLayouts(candidates))
                .fallback(false).build();
        return new ResolvedSnapshot(effective, meta, null);
    }

    private ResolvedSnapshot resolvePreviewSnapshot(Set<Long> roleIds, WorkbenchLayoutPreviewReqVO reqVO) {
        String overrideType = reqVO.getSnapshot() == null ? null : reqVO.getScopeType();
        Long overrideId = reqVO.getSnapshot() == null ? null : reqVO.getScopeId();
        WorkbenchLayoutVersionDO globalVersion = null;
        WorkbenchLayoutSnapshot global;
        if (WorkbenchLayoutScopeTypeEnum.GLOBAL.getType().equals(overrideType)) {
            validateScope(overrideType, overrideId);
            global = resolver.normalizeGlobalDraft(reqVO.getSnapshot(), getTenantMenus());
        } else {
            WorkbenchLayoutDO globalLayout = layoutMapper.selectByScope(
                    WorkbenchLayoutScopeTypeEnum.GLOBAL.getType(), GLOBAL_SCOPE_ID);
            if (globalLayout == null || globalLayout.getPublishedVersionId() == null) {
                return new ResolvedSnapshot(null, null, "GLOBAL_LAYOUT_NOT_PUBLISHED");
            }
            globalVersion = versionMapper.selectById(globalLayout.getPublishedVersionId());
            if (globalVersion == null) {
                return new ResolvedSnapshot(null, null, "GLOBAL_VERSION_MISSING");
            }
            global = parseSnapshot(globalVersion.getSnapshotJson());
        }

        List<RoleCandidate> candidates = loadPublishedRoleCandidates(roleIds, global);
        if (WorkbenchLayoutScopeTypeEnum.ROLE.getType().equals(overrideType)) {
            Scope roleScope = validateScope(overrideType, overrideId);
            WorkbenchLayoutSnapshot difference = resolver.normalizeRoleDraft(global, reqVO.getSnapshot(),
                    getAuthorizedMenus(Set.of(roleScope.getId())));
            candidates.removeIf(candidate -> Objects.equals(candidate.getRoleId(), roleScope.getId()));
            if (roleIds.contains(roleScope.getId()) && Boolean.TRUE.equals(difference.getEnabled())) {
                candidates.add(new RoleCandidate(roleScope.getId(), difference.getPriority(), difference, null));
            }
        }
        candidates.sort(Comparator.comparing(RoleCandidate::getPriority).thenComparing(RoleCandidate::getRoleId));
        WorkbenchLayoutSnapshot effective = resolver.mergeRoleSnapshots(global,
                candidates.stream().map(RoleCandidate::getSnapshot).toList());
        WorkbenchMenuProjection.Meta meta = WorkbenchMenuProjection.Meta.builder()
                .globalVersionId(globalVersion == null ? null : globalVersion.getId())
                .globalVersionNo(globalVersion == null ? null : globalVersion.getVersionNo())
                .appliedRoleLayouts(toAppliedRoleLayouts(candidates))
                .fallback(false).build();
        return new ResolvedSnapshot(effective, meta, null);
    }

    private WorkbenchLayoutPreviewRespVO fallbackPreview(AdminUserDO user, Set<Long> roleIds,
                                                          Set<String> permissions, List<MenuDO> authorizedMenus,
                                                          String reason) {
        List<AuthPermissionInfoRespVO.MenuVO> tree = AuthConvert.INSTANCE.buildMenuTree(
                new ArrayList<>(authorizedMenus));
        return WorkbenchLayoutPreviewRespVO.builder()
                .userId(user.getId()).userName(user.getNickname()).roleIds(roleIds).permissions(permissions)
                .finalTree(tree).meta(fallbackProjection(reason).getMeta()).build();
    }

    private WorkbenchMenuProjection fallbackProjection(String reason) {
        return WorkbenchMenuProjection.builder().menus(Collections.emptyList())
                .meta(WorkbenchMenuProjection.Meta.builder().fallback(true).fallbackReason(reason).build()).build();
    }

    private WorkbenchLayoutSnapshot getPublishedGlobalSnapshot() {
        WorkbenchLayoutDO layout = layoutMapper.selectByScope(
                WorkbenchLayoutScopeTypeEnum.GLOBAL.getType(), GLOBAL_SCOPE_ID);
        if (layout == null || layout.getPublishedVersionId() == null) {
            throw exception(WORKBENCH_LAYOUT_GLOBAL_REQUIRED);
        }
        WorkbenchLayoutVersionDO version = versionMapper.selectById(layout.getPublishedVersionId());
        if (version == null) {
            throw exception(WORKBENCH_LAYOUT_GLOBAL_REQUIRED);
        }
        return parseSnapshot(version.getSnapshotJson());
    }

    private void validatePublishedPriority(Long roleId, Integer priority) {
        for (WorkbenchLayoutDO layout : layoutMapper.selectListByScopeType(
                WorkbenchLayoutScopeTypeEnum.ROLE.getType())) {
            if (!Objects.equals(layout.getScopeId(), roleId)
                    && Boolean.TRUE.equals(layout.getPublishedEnabled())
                    && Objects.equals(layout.getPublishedPriority(), priority)) {
                RoleDO role = roleService.getRole(layout.getScopeId());
                throw exception(WORKBENCH_LAYOUT_PRIORITY_DUPLICATE, priority,
                        role == null ? layout.getScopeId() : role.getName());
            }
        }
    }

    private List<MenuDO> getTenantMenus() {
        return menuService.getMenuListByTenant(new MenuListReqVO());
    }

    private List<MenuDO> getAuthorizedMenus(Set<Long> roleIds) {
        if (CollUtil.isEmpty(roleIds)) {
            return new ArrayList<>();
        }
        Set<Long> menuIds = permissionService.getRoleMenuListByRoleId(roleIds);
        return menuService.filterDisableMenus(menuService.getMenuList(menuIds));
    }

    private List<WorkbenchLayoutCandidateRespVO.Page> buildCandidatePages(List<MenuDO> menus) {
        Map<Long, String> paths = resolver.resolvePublicPaths(menus);
        return resolver.getEligiblePages(menus).stream()
                .map(menu -> WorkbenchLayoutCandidateRespVO.Page.builder()
                        .sourceMenuId(menu.getId()).name(menu.getName()).icon(menu.getIcon())
                        .path(paths.get(menu.getId())).workbenchRenderMode(menu.getWorkbenchRenderMode()).build())
                .toList();
    }

    private List<RoleCandidate> loadPublishedRoleCandidates(Set<Long> roleIds,
                                                             WorkbenchLayoutSnapshot global) {
        if (CollUtil.isEmpty(roleIds)) {
            return new ArrayList<>();
        }
        List<RoleCandidate> candidates = new ArrayList<>();
        for (WorkbenchLayoutDO layout : layoutMapper.selectPublishedRoleLayouts(roleIds)) {
            WorkbenchLayoutVersionDO version = versionMapper.selectById(layout.getPublishedVersionId());
            if (version == null) {
                throw new IllegalArgumentException("角色发布版本不存在：" + layout.getScopeId());
            }
            WorkbenchLayoutSnapshot snapshot = resolver.reconcileRoleSnapshot(global,
                    parseSnapshot(version.getSnapshotJson()), getAuthorizedMenus(Set.of(layout.getScopeId())));
            candidates.add(new RoleCandidate(layout.getScopeId(), layout.getPublishedPriority(), snapshot, version));
        }
        candidates.sort(Comparator.comparing(RoleCandidate::getPriority).thenComparing(RoleCandidate::getRoleId));
        return candidates;
    }

    private List<WorkbenchMenuProjection.AppliedRoleLayout> toAppliedRoleLayouts(
            List<RoleCandidate> candidates) {
        return candidates.stream().map(candidate -> WorkbenchMenuProjection.AppliedRoleLayout.builder()
                .roleId(candidate.getRoleId()).priority(candidate.getPriority())
                .versionId(candidate.getVersion() == null ? null : candidate.getVersion().getId())
                .versionNo(candidate.getVersion() == null ? null : candidate.getVersion().getVersionNo())
                .build()).toList();
    }

    private WorkbenchLayoutSnapshot parseSnapshot(String json) {
        try {
            WorkbenchLayoutSnapshot snapshot = JsonUtils.parseObject(json, WorkbenchLayoutSnapshot.class);
            if (snapshot == null || snapshot.getSchemaVersion() == null
                    || (snapshot.getSchemaVersion() != WorkbenchLayoutSnapshot.SCHEMA_VERSION
                    && snapshot.getSchemaVersion() != WorkbenchLayoutSnapshot.LEGACY_SCHEMA_VERSION)) {
                throw new IllegalArgumentException("不支持的快照版本");
            }
            return snapshot;
        } catch (RuntimeException ex) {
            if (ex instanceof IllegalArgumentException) {
                throw ex;
            }
            throw new IllegalArgumentException("快照 JSON 无法解析", ex);
        }
    }

    private Scope validateScope(String scopeType, Long scopeId) {
        WorkbenchLayoutScopeTypeEnum type = WorkbenchLayoutScopeTypeEnum.of(scopeType);
        if (type == null || scopeId == null
                || (type == WorkbenchLayoutScopeTypeEnum.GLOBAL && scopeId != GLOBAL_SCOPE_ID)
                || (type == WorkbenchLayoutScopeTypeEnum.ROLE && scopeId <= 0)) {
            throw exception(WORKBENCH_LAYOUT_SCOPE_INVALID);
        }
        if (type == WorkbenchLayoutScopeTypeEnum.ROLE && roleService.getRole(scopeId) == null) {
            throw exception(ROLE_NOT_EXISTS);
        }
        return new Scope(type.getType(), scopeId);
    }

    private WorkbenchLayoutImpactRespVO.Issue issue(Long roleId, String roleName, String message) {
        return WorkbenchLayoutImpactRespVO.Issue.builder()
                .roleId(roleId).roleName(roleName).message(message).build();
    }

    @Data
    @AllArgsConstructor
    private static class Scope {
        private String type;
        private Long id;

        boolean isGlobal() {
            return WorkbenchLayoutScopeTypeEnum.GLOBAL.getType().equals(type);
        }
    }

    @Data
    @AllArgsConstructor
    private static class ResolvedSnapshot {
        private WorkbenchLayoutSnapshot snapshot;
        private WorkbenchMenuProjection.Meta meta;
        private String fallbackReason;
    }

    @Data
    @AllArgsConstructor
    private static class RoleCandidate {
        private Long roleId;
        private Integer priority;
        private WorkbenchLayoutSnapshot snapshot;
        private WorkbenchLayoutVersionDO version;
    }

}
