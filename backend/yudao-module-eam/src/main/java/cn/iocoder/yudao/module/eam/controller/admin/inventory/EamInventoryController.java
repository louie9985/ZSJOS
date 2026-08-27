package cn.iocoder.yudao.module.eam.controller.admin.inventory;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.eam.controller.admin.inventory.vo.EamInventoryCheckReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.inventory.vo.EamInventoryCreateReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.inventory.vo.EamInventoryDetailRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.inventory.vo.EamInventoryPageReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.inventory.vo.EamInventoryRespVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.inventory.EamInventoryDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.inventory.EamInventoryDetailDO;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetService;
import cn.iocoder.yudao.module.eam.service.inventory.EamInventoryService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.hrm.api.employee.HrmEmployeeApi;
import cn.iocoder.yudao.module.hrm.api.employee.dto.HrmEmployeeRespDTO;
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

@Tag(name = "管理后台 - EAM 资产盘点")
@RestController
@RequestMapping("/eam/inventory")
@Validated
public class EamInventoryController {

    @Resource
    private EamInventoryService inventoryService;
    @Resource
    private EamAssetService assetService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private HrmEmployeeApi employeeApi;

    @PostMapping("/create")
    @Operation(summary = "创建盘点单", description = "按范围快照生成盘点明细")
    @PreAuthorize("@ss.hasPermission('eam:inventory:create')")
    public CommonResult<Long> createInventory(@Valid @RequestBody EamInventoryCreateReqVO reqVO) {
        return success(inventoryService.createInventory(reqVO));
    }

    @PutMapping("/check")
    @Operation(summary = "录入盘点结果")
    @PreAuthorize("@ss.hasPermission('eam:inventory:update')")
    public CommonResult<Boolean> checkDetail(@Valid @RequestBody EamInventoryCheckReqVO reqVO) {
        inventoryService.checkDetail(reqVO);
        return success(true);
    }

    @PutMapping("/finish")
    @Operation(summary = "完成盘点")
    @Parameter(name = "id", description = "盘点单编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:inventory:update')")
    public CommonResult<Boolean> finishInventory(@RequestParam("id") Long id) {
        inventoryService.finishInventory(id);
        return success(true);
    }

    @PutMapping("/sync-detail")
    @Operation(summary = "同步实盘归属回资产", description = "用于处理位置不符的明细")
    @Parameter(name = "detailId", description = "盘点明细编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:inventory:update')")
    public CommonResult<Boolean> syncDetailToAsset(@RequestParam("detailId") Long detailId) {
        inventoryService.syncDetailToAsset(detailId);
        return success(true);
    }

    @PutMapping("/mark-lost")
    @Operation(summary = "标记资产丢失", description = "用于处理未找到的明细")
    @Parameter(name = "detailId", description = "盘点明细编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:inventory:update')")
    public CommonResult<Boolean> markDetailAssetLost(@RequestParam("detailId") Long detailId) {
        inventoryService.markDetailAssetLost(detailId);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除盘点单")
    @Parameter(name = "id", description = "盘点单编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:inventory:delete')")
    public CommonResult<Boolean> deleteInventory(@RequestParam("id") Long id) {
        inventoryService.deleteInventory(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得盘点单")
    @Parameter(name = "id", description = "盘点单编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:inventory:query')")
    public CommonResult<EamInventoryRespVO> getInventory(@RequestParam("id") Long id) {
        EamInventoryDO inventory = inventoryService.getInventory(id);
        return success(BeanUtils.toBean(inventory, EamInventoryRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得盘点单分页")
    @PreAuthorize("@ss.hasPermission('eam:inventory:query')")
    public CommonResult<PageResult<EamInventoryRespVO>> getInventoryPage(
            @Valid EamInventoryPageReqVO reqVO) {
        PageResult<EamInventoryDO> pageResult = inventoryService.getInventoryPage(reqVO);
        return success(BeanUtils.toBean(pageResult, EamInventoryRespVO.class));
    }

    @GetMapping("/detail-list")
    @Operation(summary = "获得盘点明细列表")
    @Parameter(name = "inventoryId", description = "盘点单编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:inventory:query')")
    public CommonResult<List<EamInventoryDetailRespVO>> getDetailList(
            @RequestParam("inventoryId") Long inventoryId) {
        List<EamInventoryDetailDO> details = inventoryService.getDetailListByInventoryId(inventoryId);
        List<EamInventoryDetailRespVO> result =
                BeanUtils.toBean(details, EamInventoryDetailRespVO.class);
        if (result.isEmpty()) {
            return success(result);
        }
        Map<Long, EamAssetDO> assetMap = assetService.getAssetList(
                        convertSet(details, EamInventoryDetailDO::getAssetId)).stream()
                .collect(Collectors.toMap(EamAssetDO::getId, a -> a, (a, b) -> a));
        Map<Long, HrmEmployeeRespDTO> employeeMap = employeeApi.getEmployeeList(
                        convertSet(details, EamInventoryDetailDO::getExpectEmployeeId)).stream()
                .collect(Collectors.toMap(HrmEmployeeRespDTO::getId, item -> item, (a, b) -> a));

        result.forEach(vo -> {
            EamAssetDO asset = assetMap.get(vo.getAssetId());
            if (asset != null) {
                vo.setAssetName(asset.getName());
                vo.setAssetCode(asset.getAssetCode());
            }
            HrmEmployeeRespDTO employee = employeeMap.get(vo.getExpectEmployeeId());
            vo.setExpectEmployeeName(employee != null ? employee.getName() : null);
        });
        return success(result);
    }

}
