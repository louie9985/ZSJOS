package cn.iocoder.yudao.module.zsjos.controller.app.partner;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerLoginReqVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerLoginRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerPermissionInfoRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerActivateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerWecomLoginReqVO;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerAuthService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos/auth")
public class PartnerAppAuthController {
    @Resource private PartnerAuthService authService;
    @Resource private SecurityProperties securityProperties;

    @PostMapping("/login") @PermitAll
    public CommonResult<PartnerLoginRespVO> login(@Valid @RequestBody PartnerLoginReqVO reqVO,
                                                   HttpServletRequest request) {
        return success(authService.login(reqVO, ServletUtils.getClientIP(request)));
    }

    @PostMapping("/activate") @PermitAll
    public CommonResult<PartnerLoginRespVO> activate(@Valid @RequestBody PartnerActivateReqVO reqVO,
                                                     HttpServletRequest request) {
        return success(authService.activate(reqVO, ServletUtils.getClientIP(request)));
    }

    @GetMapping("/wecom-authorize-url") @PermitAll
    public CommonResult<String> wecomAuthorizeUrl(@RequestParam @NotBlank String redirectUri) {
        return success(authService.getWecomAuthorizeUrl(redirectUri));
    }

    @PostMapping("/wecom-login") @PermitAll
    public CommonResult<PartnerLoginRespVO> wecomLogin(@Valid @RequestBody PartnerWecomLoginReqVO reqVO,
                                                       HttpServletRequest request) {
        return success(authService.wecomLogin(reqVO, ServletUtils.getClientIP(request)));
    }

    @PostMapping("/logout") @PermitAll
    public CommonResult<Boolean> logout(HttpServletRequest request) {
        String token = SecurityFrameworkUtils.obtainAuthorization(request,
                securityProperties.getTokenHeader(), securityProperties.getTokenParameter());
        if (StrUtil.isNotBlank(token)) authService.logout(token);
        return success(true);
    }

    @PostMapping("/refresh-token") @PermitAll
    public CommonResult<PartnerLoginRespVO> refresh(@RequestParam String refreshToken,
                                                     @RequestParam(required = false) String clientId) {
        return success(authService.refresh(refreshToken, clientId));
    }

    @GetMapping("/permission-info")
    public CommonResult<PartnerPermissionInfoRespVO> permissionInfo() {
        return success(authService.getPermissionInfo(getLoginUserId()));
    }
}
