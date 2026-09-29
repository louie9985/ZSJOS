package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.MediaLeadVO;
import cn.iocoder.yudao.module.zsjos.enums.ZsjosPostCodeConstants;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.MediaLeadOrgDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.MediaLeadOrgMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Service
public class MediaLeadAccess {
    private static final String CONTENT_DIRECTOR_POST = "content_director";
    public static final String QUERY = "zsjos:media-lead-analysis:query";
    public static final String SELF = "zsjos:media-lead-analysis:self";
    public static final String DEPARTMENT = "zsjos:media-lead-analysis:department";
    public static final String CENTER = "zsjos:media-lead-analysis:center";
    public static final String DETAIL = "zsjos:media-lead-analysis:detail";
    public static final String TARGET_QUERY = "zsjos:media-lead-target:query";
    public static final String TARGET_UPDATE = "zsjos:media-lead-target:update";
    public static final String TARGET_CONFIGURE = "zsjos:media-lead-target:configure";

    @Resource private PermissionApi permissionApi;
    @Resource private DeptApi deptApi;
    @Resource private PostApi postApi;
    @Resource private AdminUserApi userApi;
    @Resource private MediaLeadOrgMapper orgMapper;

    public static ServiceException denied() {
        return PerformanceAccess.denied();
    }

    public boolean has(String permission) {
        return permissionApi.hasAnyPermissions(getLoginUserId(), permission);
    }

    public boolean deptAllowed(Long deptId) {
        if (deptId == null) return false;
        if (permissionApi.hasTenantReadAllAccess(getLoginUserId())) return true;
        var scope = permissionApi.getDeptDataPermission(getLoginUserId());
        return scope != null && (Boolean.TRUE.equals(scope.getAll())
                || scope.getDeptIds() != null && scope.getDeptIds().contains(deptId));
    }

    public List<MediaLeadOrgDO> organizations() {
        var centers = orgMapper.selectList().stream().filter(x -> "CENTER".equals(x.getKind())).toList();
        List<MediaLeadOrgDO> result = new ArrayList<>(centers);
        for (var center : centers) {
            for (var child : deptApi.getChildDeptList(center.getDeptId())) {
                var department = new MediaLeadOrgDO();
                department.setKind("DEPT");
                department.setDeptId(child.getId());
                department.setCenterId(center.getDeptId());
                result.add(department);
            }
        }
        return result;
    }

    public MediaLeadOrgDO organization(String type, Long id) {
        return organizations().stream().filter(x -> Objects.equals(x.getDeptId(), id)
                && Objects.equals(x.getKind(), type)).findFirst().orElse(null);
    }

    public void authorize(MediaLeadVO.Query query, boolean target) {
        String type = "SELF".equals(query.scopeType()) ? "USER" : query.scopeType();
        Long id = "SELF".equals(query.scopeType()) ? getLoginUserId() : query.scopeId();
        if (id == null) throw PerformanceAccess.invalid("请选择统计范围");
        if (target && !has(TARGET_QUERY)) throw denied();
        if (!target && !has(QUERY)) throw denied();
        if ("USER".equals(type)) {
            var user = userApi.getUser(id);
            if (user == null || !isMediaUser(user)) throw denied();
            if (!target && Objects.equals(id, getLoginUserId()) && has(SELF)) return;
            if (!deptAllowed(user.getDeptId())) throw denied();
            if (!target && (!has(DEPARTMENT) || organization("DEPT", user.getDeptId()) == null
                    && organization("CENTER", user.getDeptId()) == null)) throw denied();
            return;
        }
        if (!"DEPT".equals(type) && !"CENTER".equals(type)) throw denied();
        var org = organization(type, id);
        if (org == null || !deptAllowed(org.getDeptId())) throw denied();
        if (!target && !has("CENTER".equals(type) ? CENTER : DEPARTMENT)) throw denied();
        if ("CENTER".equals(type) && organizations().stream()
                .filter(x -> Objects.equals(x.getCenterId(), id) && "DEPT".equals(x.getKind()))
                .anyMatch(x -> !deptAllowed(x.getDeptId()))) throw denied();
    }

    public List<AdminUserRespDTO> mediaUsers() {
        Set<Long> postIds = new LinkedHashSet<>();
        for (String code : List.of(ZsjosPostCodeConstants.NEW_MEDIA_OPERATOR, CONTENT_DIRECTOR_POST)) {
            var post = postApi.getPostByCode(code);
            if (post != null) postIds.add(post.getId());
        }
        if (postIds.isEmpty()) return List.of();
        return userApi.getUserListByPostIds(new ArrayList<>(postIds)).stream()
                .filter(x -> CommonStatusEnum.ENABLE.getStatus().equals(x.getStatus()))
                .toList();
    }

    public boolean isMediaUser(AdminUserRespDTO user) {
        return mediaUsers().stream().anyMatch(x -> Objects.equals(x.getId(), user.getId()));
    }

    public AdminUserRespDTO user(Long id) { return userApi.getUser(id); }
    public cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO dept(Long id) { return deptApi.getDept(id); }

    public boolean writeDeptAllowed(Long deptId) {
        if (deptId == null) return false;
        var scope = permissionApi.getDeptDataPermission(getLoginUserId());
        return scope != null && (Boolean.TRUE.equals(scope.getAll())
                || scope.getDeptIds() != null && scope.getDeptIds().contains(deptId));
    }

    public List<cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO> departmentOptions() {
        if (!has(TARGET_QUERY)) throw denied();
        return deptApi.getChildDeptList(0L).stream().filter(x -> deptAllowed(x.getId())).toList();
    }
    public void authorizeTargetWrite(String type, Long id) {
        if (!has(TARGET_UPDATE)) throw denied();
        var query = new MediaLeadVO.Query(type, id, java.time.LocalDate.now(), java.time.LocalDate.now());
        authorize(query, true);
        Long deptId = id;
        if ("USER".equals(type)) {
            var user = user(id);
            if (user == null || !isMediaUser(user)) throw denied();
            deptId = user.getDeptId();
        } else if ("CENTER".equals(type)) {
            var org = organization(type, id);
            deptId = org == null ? null : org.getDeptId();
        }
        if (!writeDeptAllowed(deptId)) throw denied();
    }

    public List<MediaLeadVO.ScopeNode> tree() {
        List<MediaLeadVO.ScopeNode> result = new ArrayList<>();
        var self = user(getLoginUserId());
        if (has(SELF) && self != null && isMediaUser(self)) result.add(new MediaLeadVO.ScopeNode("SELF", null, "我的新媒体客资", "SELF", getLoginUserId()));
        var organizations = organizations();
        Set<String> visibleKeys = new HashSet<>();
        for (var org : organizations) {
            String permission = "CENTER".equals(org.getKind()) ? CENTER : DEPARTMENT;
            if (!has(permission) || !deptAllowed(org.getDeptId())) continue;
            if ("CENTER".equals(org.getKind()) && organizations.stream()
                    .filter(x -> "DEPT".equals(x.getKind()) && Objects.equals(x.getCenterId(), org.getDeptId()))
                    .anyMatch(x -> !deptAllowed(x.getDeptId()))) continue;
            visibleKeys.add(org.getKind() + ":" + org.getDeptId());
        }
        for (var org : organizations) {
            if (!visibleKeys.contains(org.getKind() + ":" + org.getDeptId())) continue;
            var dept = dept(org.getDeptId());
            if (dept == null) continue;
            String parent = "CENTER".equals(org.getKind()) ? null
                    : Objects.equals(dept.getParentId(), org.getCenterId())
                    ? "CENTER:" + org.getCenterId() : "DEPT:" + dept.getParentId();
            if (parent != null && !visibleKeys.contains(parent)) parent = null;
            result.add(new MediaLeadVO.ScopeNode(org.getKind() + ":" + org.getDeptId(), parent,
                    dept.getName(), org.getKind(), org.getDeptId()));
        }
        if (has(DEPARTMENT)) for (var user : mediaUsers()) {
            if (!deptAllowed(user.getDeptId())) continue;
            String parent = organizations.stream().anyMatch(x -> "CENTER".equals(x.getKind())
                    && Objects.equals(x.getDeptId(), user.getDeptId()))
                    ? "CENTER:" + user.getDeptId() : "DEPT:" + user.getDeptId();
            if (!visibleKeys.contains(parent) && organizations.stream().noneMatch(x ->
                    Objects.equals(x.getDeptId(), user.getDeptId()))) continue;
            result.add(new MediaLeadVO.ScopeNode("USER:" + user.getId(),
                    visibleKeys.contains(parent) ? parent : null,
                    user.getNickname(), "USER", user.getId()));
        }
        return result;
    }
}
