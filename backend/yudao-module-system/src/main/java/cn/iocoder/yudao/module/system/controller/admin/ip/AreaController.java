package cn.iocoder.yudao.module.system.controller.admin.ip;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.ip.core.utils.IPUtils;
import cn.iocoder.yudao.module.system.controller.admin.ip.vo.AreaListReqVO;
import cn.iocoder.yudao.module.system.controller.admin.ip.vo.AreaNodeRespVO;
import cn.iocoder.yudao.module.system.controller.admin.ip.vo.AreaRespVO;
import cn.iocoder.yudao.module.system.controller.admin.ip.vo.AreaSaveReqVO;
import cn.iocoder.yudao.module.system.controller.admin.ip.vo.AreaUpdateStatusReqVO;
import cn.iocoder.yudao.module.system.service.ip.AreaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 地区")
@RestController
@RequestMapping("/system/area")
@Validated
public class AreaController {

    @Resource
    private AreaService areaService;

    @GetMapping("/tree")
    @Operation(summary = "获得地区树")
    public CommonResult<List<AreaNodeRespVO>> getAreaTree() {
        return success(areaService.getEnabledChinaTree());
    }

    @GetMapping("/list")
    @Operation(summary = "获得地区管理列表")
    @PreAuthorize("@ss.hasPermission('system:area:query')")
    public CommonResult<List<AreaRespVO>> getAreaList(@Valid AreaListReqVO reqVO) {
        return success(BeanUtils.toBean(areaService.getAreaList(reqVO), AreaRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得地区详情")
    @PreAuthorize("@ss.hasPermission('system:area:query')")
    public CommonResult<AreaRespVO> getArea(@RequestParam("id") Integer id) {
        return success(BeanUtils.toBean(areaService.getArea(id), AreaRespVO.class));
    }

    @PostMapping("/create")
    @Operation(summary = "创建地区")
    @PreAuthorize("@ss.hasPermission('system:area:create')")
    public CommonResult<Integer> createArea(@Valid @RequestBody AreaSaveReqVO reqVO) {
        return success(areaService.createArea(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新地区")
    @PreAuthorize("@ss.hasPermission('system:area:update')")
    public CommonResult<Boolean> updateArea(@Valid @RequestBody AreaSaveReqVO reqVO) {
        areaService.updateArea(reqVO);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "更新地区状态")
    @PreAuthorize("@ss.hasPermission('system:area:update')")
    public CommonResult<Boolean> updateAreaStatus(@Valid @RequestBody AreaUpdateStatusReqVO reqVO) {
        areaService.updateAreaStatus(reqVO.getId(), reqVO.getStatus());
        return success(true);
    }

    @GetMapping("/get-by-ip")
    @Operation(summary = "获得 IP 对应的地区名")
    @Parameter(name = "ip", description = "IP", required = true)
    public CommonResult<String> getAreaByIp(@RequestParam("ip") String ip) {
        // 获得城市
        Integer areaId = IPUtils.getAreaId(ip);
        if (areaId == null) {
            return success("未知");
        }
        String result = areaService.format(areaId);
        return success(result != null ? result : "未知");
    }

}
