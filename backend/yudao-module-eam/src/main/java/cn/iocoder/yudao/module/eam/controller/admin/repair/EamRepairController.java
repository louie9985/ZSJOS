package cn.iocoder.yudao.module.eam.controller.admin.repair;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.eam.controller.admin.repair.vo.EamRepairCreateReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.repair.vo.EamRepairFinishReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.repair.vo.EamRepairPageReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.repair.vo.EamRepairRespVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.repair.EamRepairDO;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetService;
import cn.iocoder.yudao.module.eam.service.repair.EamRepairService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

@Tag(name = "管理后台 - EAM 维修记录")
@RestController
@RequestMapping("/eam/repair")
@Validated
public class EamRepairController {

    @Resource
    private EamRepairService repairService;
    @Resource
    private EamAssetService assetService;

    @PostMapping("/create")
    @Operation(summary = "送修", description = "创建维修记录并将资产置为维修中")
    @PreAuthorize("@ss.hasPermission('eam:repair:create')")
    public CommonResult<Long> createRepair(@Valid @RequestBody EamRepairCreateReqVO reqVO) {
        return success(repairService.createRepair(reqVO));
    }

    @PutMapping("/finish")
    @Operation(summary = "维修完成", description = "资产恢复到送修前状态")
    @PreAuthorize("@ss.hasPermission('eam:repair:update')")
    public CommonResult<Boolean> finishRepair(@Valid @RequestBody EamRepairFinishReqVO reqVO) {
        repairService.finishRepair(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除维修记录")
    @Parameter(name = "id", description = "维修记录编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:repair:delete')")
    public CommonResult<Boolean> deleteRepair(@RequestParam("id") Long id) {
        repairService.deleteRepair(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得维修记录")
    @Parameter(name = "id", description = "维修记录编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:repair:query')")
    public CommonResult<EamRepairRespVO> getRepair(@RequestParam("id") Long id) {
        EamRepairDO repair = repairService.getRepair(id);
        if (repair == null) {
            return success(null);
        }
        return success(buildRepairVOList(List.of(repair)).get(0));
    }

    @GetMapping("/list-by-asset")
    @Operation(summary = "获得某资产的维修记录列表")
    @Parameter(name = "assetId", description = "资产编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:repair:query')")
    public CommonResult<List<EamRepairRespVO>> getRepairListByAsset(@RequestParam("assetId") Long assetId) {
        return success(buildRepairVOList(repairService.getRepairListByAssetId(assetId)));
    }

    @GetMapping("/page")
    @Operation(summary = "获得维修记录分页")
    @PreAuthorize("@ss.hasPermission('eam:repair:query')")
    public CommonResult<PageResult<EamRepairRespVO>> getRepairPage(@Valid EamRepairPageReqVO reqVO) {
        PageResult<EamRepairDO> pageResult = repairService.getRepairPage(reqVO);
        return success(new PageResult<>(buildRepairVOList(pageResult.getList()), pageResult.getTotal()));
    }

    private List<EamRepairRespVO> buildRepairVOList(List<EamRepairDO> list) {
        List<EamRepairRespVO> result = BeanUtils.toBean(list, EamRepairRespVO.class);
        if (result.isEmpty()) {
            return result;
        }
        Map<Long, EamAssetDO> assetMap = assetService.getAssetList(
                        convertSet(list, EamRepairDO::getAssetId)).stream()
                .collect(Collectors.toMap(EamAssetDO::getId, a -> a, (a, b) -> a));
        result.forEach(vo -> {
            EamAssetDO asset = assetMap.get(vo.getAssetId());
            if (asset != null) {
                vo.setAssetName(asset.getName());
                vo.setAssetCode(asset.getAssetCode());
            }
        });
        return result;
    }

}
