package cn.iocoder.yudao.module.system.controller.app.partner;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginReqVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthPermissionInfoRespVO;
import cn.iocoder.yudao.module.system.convert.auth.AuthConvert;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.enums.logger.LoginLogTypeEnum;
import cn.iocoder.yudao.module.system.service.auth.AdminAuthService;
import cn.iocoder.yudao.module.system.service.permission.MenuService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.permission.RoleService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.AUTH_LOGIN_BAD_CREDENTIALS;

/**
 * Partner portal authentication facade. Partner accounts remain System admin users;
 * the app-api prefix is only a transport boundary for the independent frontend.
 */
@RestController
@RequestMapping("/zsjos/auth")
public class PartnerAppAuthController {

    @Resource private AdminAuthService authService;
    @Resource private AdminUserService userService;
    @Resource private RoleService roleService;
    @Resource private MenuService menuService;
    @Resource private PermissionService permissionService;
    @Resource private ConfigApi configApi;
    @Resource private SecurityProperties securityProperties;

    @PostMapping("/login")
    @PermitAll
    public CommonResult<AuthLoginRespVO> login(@Valid @RequestBody AuthLoginReqVO reqVO) {
        return success(requirePartner(authService.login(reqVO)));
    }

    @PostMapping("/logout")
    @PermitAll
    public CommonResult<Boolean> logout(HttpServletRequest request) {
        String token = SecurityFrameworkUtils.obtainAuthorization(request,
                securityProperties.getTokenHeader(), securityProperties.getTokenParameter());
        if (StrUtil.isNotBlank(token)) authService.logout(token, LoginLogTypeEnum.LOGOUT_SELF.getType());
        return success(true);
    }

    @PostMapping("/refresh-token")
    @PermitAll
    public CommonResult<AuthLoginRespVO> refreshToken(@RequestParam String refreshToken,
                                                       @RequestParam(required = false) String clientId) {
        return success(requirePartner(authService.refreshToken(refreshToken, clientId)));
    }

    @GetMapping("/permission-info")
    @PreAuthorize("@ss.hasRole('part_time_partner')")
    @DataPermission(enable = false)
    public CommonResult<AuthPermissionInfoRespVO> permissionInfo() {
        AdminUserDO user = userService.getUser(getLoginUserId());
        if (user == null) return success(null);
        Set<Long> roleIds = permissionService.getUserRoleIdListByUserId(getLoginUserId());
        if (CollUtil.isEmpty(roleIds)) return success(withDefaultAvatar(
                AuthConvert.INSTANCE.convert(user, Collections.emptyList(), Collections.emptyList())));
        List<RoleDO> roles = roleService.getRoleList(roleIds);
        roles.removeIf(role -> !CommonStatusEnum.ENABLE.getStatus().equals(role.getStatus()));
        Set<Long> menuIds = permissionService.getRoleMenuListByRoleId(convertSet(roles, RoleDO::getId));
        List<MenuDO> menus = menuService.filterDisableMenus(menuService.getMenuList(menuIds));
        return success(withDefaultAvatar(AuthConvert.INSTANCE.convert(user, roles, menus)));
    }

    private AuthPermissionInfoRespVO withDefaultAvatar(AuthPermissionInfoRespVO info) {
        return info.setDefaultAvatar(configApi.getDefaultUserAvatar());
    }

    private AuthLoginRespVO requirePartner(AuthLoginRespVO login) {
        if (permissionService.hasAnyRoles(login.getUserId(), "part_time_partner")) return login;
        authService.logout(login.getAccessToken(), LoginLogTypeEnum.LOGOUT_SELF.getType());
        throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
    }
}
