package cn.iocoder.yudao.module.zsjos.controller.pub.positioning;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.zsjos.controller.pub.positioning.vo.PublicPositioningConfirmationRespVO;
import cn.iocoder.yudao.module.zsjos.controller.pub.positioning.vo.PublicPositioningDecisionReqVO;
import cn.iocoder.yudao.module.zsjos.service.positioning.PositioningConfirmationService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/zsjos/positioning-confirmation")
@Validated
@PermitAll
@TenantIgnore
public class PublicPositioningConfirmationController {
    public static final String TOKEN_HEADER = "X-Positioning-Token";
    @Resource private PositioningConfirmationService service;

    @GetMapping("/detail")
    public CommonResult<PublicPositioningConfirmationRespVO> detail(
            @RequestHeader(TOKEN_HEADER) @NotBlank String token) {
        return success(service.publicDetail(token));
    }

    @PostMapping("/decision")
    public CommonResult<Boolean> decide(@RequestHeader(TOKEN_HEADER) @NotBlank String token,
                                        @Valid @RequestBody PublicPositioningDecisionReqVO request) {
        service.decide(token, request);
        return success(true);
    }
}
