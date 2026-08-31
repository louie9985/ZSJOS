package cn.iocoder.yudao.module.zsjos.controller.pub.wecom;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.zsjos.controller.pub.wecom.vo.PublicWecomClickRespVO;
import cn.iocoder.yudao.module.zsjos.service.wecom.WecomClickTicketService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/zsjos/wecom-click")
@Validated
@PermitAll
@TenantIgnore
public class PublicWecomClickController {

    @Resource
    private WecomClickTicketService wecomClickTicketService;

    @GetMapping("/resolve")
    public CommonResult<PublicWecomClickRespVO> resolve(@RequestParam @NotBlank String ticket) {
        return success(wecomClickTicketService.resolve(ticket));
    }
}
