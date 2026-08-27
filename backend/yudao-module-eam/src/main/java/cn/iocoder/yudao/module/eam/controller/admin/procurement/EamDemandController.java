package cn.iocoder.yudao.module.eam.controller.admin.procurement;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.*;
import cn.iocoder.yudao.module.eam.service.procurement.EamDemandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - EAM 办公资产需求")
@RestController
@RequestMapping("/eam/demand")
@Validated
public class EamDemandController {
    @Resource private EamDemandService demandService;

    @PostMapping("/create")
    @Operation(summary = "代员工创建办公资产需求")
    @PreAuthorize("@ss.hasPermission('eam:demand:create')")
    public CommonResult<Long> create(@Valid @RequestBody EamDemandCreateReqVO reqVO) {
        return success(demandService.createDemand(reqVO, SecurityFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('eam:demand:query')")
    public CommonResult<EamDemandRespVO> get(@RequestParam Long id) { return success(demandService.getDemand(id)); }

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('eam:demand:query')")
    public CommonResult<List<EamDemandRespVO>> list() { return success(demandService.getDemandList()); }

    @GetMapping("/stock-candidates")
    @PreAuthorize("@ss.hasPermission('eam:stock:query')")
    public CommonResult<List<EamStockCandidateRespVO>> candidates(@RequestParam Long demandItemId) {
        return success(demandService.getCandidates(demandItemId));
    }

    @PutMapping("/reserve")
    @PreAuthorize("@ss.hasPermission('eam:stock:allocate')")
    public CommonResult<Long> reserve(@Valid @RequestBody EamStockReserveReqVO reqVO) {
        return success(demandService.reserve(reqVO));
    }

    @PutMapping("/reserve-and-allocate")
    @PreAuthorize("@ss.hasPermission('eam:stock:allocate')")
    public CommonResult<Long> reserveAndAllocate(@Valid @RequestBody EamStockReserveReqVO reqVO) {
        return success(demandService.reserveAndAllocate(reqVO));
    }

    @PutMapping("/allocate")
    @PreAuthorize("@ss.hasPermission('eam:stock:allocate')")
    public CommonResult<Boolean> allocate(@Valid @RequestBody EamStockAllocateReqVO reqVO) {
        demandService.allocate(reqVO);
        return success(true);
    }
}
