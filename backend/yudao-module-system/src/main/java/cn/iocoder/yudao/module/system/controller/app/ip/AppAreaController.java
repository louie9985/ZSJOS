package cn.iocoder.yudao.module.system.controller.app.ip;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.controller.app.ip.vo.AppAreaNodeRespVO;
import cn.iocoder.yudao.module.system.service.ip.AreaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - 地区")
@RestController
@RequestMapping("/system/area")
@Validated
public class AppAreaController {

    @Resource
    private AreaService areaService;

    @GetMapping("/tree")
    @Operation(summary = "获得地区树")
    @PermitAll
    public CommonResult<List<AppAreaNodeRespVO>> getAreaTree() {
        return success(BeanUtils.toBean(areaService.getEnabledChinaTree(), AppAreaNodeRespVO.class));
    }

}
