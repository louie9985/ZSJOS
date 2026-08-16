package cn.iocoder.yudao.module.system.controller.app.partner;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.profile.UserProfileRespVO;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.profile.UserProfileUpdatePasswordReqVO;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.profile.UserProfileUpdateReqVO;
import cn.iocoder.yudao.module.system.convert.user.UserConvert;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.PostDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import cn.iocoder.yudao.module.system.service.dept.PostService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.permission.RoleService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos/profile")
@PreAuthorize("@ss.hasRole('part_time_partner')")
public class PartnerAppProfileController {
    @Resource private AdminUserService userService;
    @Resource private DeptService deptService;
    @Resource private PostService postService;
    @Resource private PermissionService permissionService;
    @Resource private RoleService roleService;

    @GetMapping("/get")
    @DataPermission(enable = false)
    public CommonResult<UserProfileRespVO> get() {
        AdminUserDO user = userService.getUser(getLoginUserId());
        List<RoleDO> roles = roleService.getRoleListFromCache(permissionService.getUserRoleIdListByUserId(user.getId()));
        DeptDO dept = user.getDeptId() == null ? null : deptService.getDept(user.getDeptId());
        List<PostDO> posts = CollUtil.isEmpty(user.getPostIds()) ? null : postService.getPostList(user.getPostIds());
        return success(UserConvert.INSTANCE.convert(user, roles, dept, posts));
    }

    @PutMapping("/update")
    public CommonResult<Boolean> update(@Valid @RequestBody UserProfileUpdateReqVO request) {
        userService.updateUserProfile(getLoginUserId(), request); return success(true);
    }

    @PutMapping("/update-password")
    public CommonResult<Boolean> updatePassword(@Valid @RequestBody UserProfileUpdatePasswordReqVO request) {
        userService.updateUserPassword(getLoginUserId(), request); return success(true);
    }
}
