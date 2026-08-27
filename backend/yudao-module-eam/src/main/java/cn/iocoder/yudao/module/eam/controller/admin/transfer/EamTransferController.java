package cn.iocoder.yudao.module.eam.controller.admin.transfer;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.eam.controller.admin.transfer.vo.EamTransferCreateReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.transfer.vo.EamTransferPageReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.transfer.vo.EamTransferRespVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.transfer.EamTransferDO;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetService;
import cn.iocoder.yudao.module.eam.service.transfer.EamTransferService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

@Tag(name = "管理后台 - EAM 资产流转")
@RestController
@RequestMapping("/eam/transfer")
@Validated
public class EamTransferController {

    @Resource
    private EamTransferService transferService;
    @Resource
    private EamAssetService assetService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private HrmEmployeeApi employeeApi;

    @PostMapping("/create")
    @Operation(summary = "创建流转单", description = "领用/借用/调拨走审批，退还/归还直接生效")
    @PreAuthorize("@ss.hasPermission('eam:transfer:create')")
    public CommonResult<Long> createTransfer(@Valid @RequestBody EamTransferCreateReqVO reqVO) {
        return success(transferService.createTransfer(reqVO));
    }

    @PutMapping("/approve")
    @Operation(summary = "审批通过流转单")
    @Parameter(name = "id", description = "单据编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:transfer:update')")
    public CommonResult<Boolean> approveTransfer(@RequestParam("id") Long id) {
        transferService.approveTransfer(id);
        return success(true);
    }

    @PutMapping("/reject")
    @Operation(summary = "驳回流转单")
    @Parameter(name = "id", description = "单据编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:transfer:update')")
    public CommonResult<Boolean> rejectTransfer(@RequestParam("id") Long id,
                                                @RequestParam(value = "reason", required = false) String reason) {
        transferService.rejectTransfer(id, reason);
        return success(true);
    }

    @PutMapping("/cancel")
    @Operation(summary = "取消流转单")
    @Parameter(name = "id", description = "单据编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:transfer:update')")
    public CommonResult<Boolean> cancelTransfer(@RequestParam("id") Long id) {
        transferService.cancelTransfer(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得流转单")
    @Parameter(name = "id", description = "单据编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:transfer:query')")
    public CommonResult<EamTransferRespVO> getTransfer(@RequestParam("id") Long id) {
        EamTransferDO transfer = transferService.getTransfer(id);
        if (transfer == null) {
            return success(null);
        }
        return success(buildTransferVOList(List.of(transfer)).get(0));
    }

    @GetMapping("/page")
    @Operation(summary = "获得流转单分页")
    @PreAuthorize("@ss.hasPermission('eam:transfer:query')")
    public CommonResult<PageResult<EamTransferRespVO>> getTransferPage(@Valid EamTransferPageReqVO reqVO) {
        PageResult<EamTransferDO> pageResult = transferService.getTransferPage(reqVO);
        return success(new PageResult<>(buildTransferVOList(pageResult.getList()), pageResult.getTotal()));
    }

    private List<EamTransferRespVO> buildTransferVOList(List<EamTransferDO> list) {
        List<EamTransferRespVO> result = BeanUtils.toBean(list, EamTransferRespVO.class);
        if (result.isEmpty()) {
            return result;
        }
        Map<Long, EamAssetDO> assetMap = assetService.getAssetList(
                        convertSet(list, EamTransferDO::getAssetId)).stream()
                .collect(Collectors.toMap(EamAssetDO::getId, a -> a, (a, b) -> a));
        Set<Long> employeeIds = new HashSet<>();
        employeeIds.addAll(convertSet(list, EamTransferDO::getFromEmployeeId));
        employeeIds.addAll(convertSet(list, EamTransferDO::getToEmployeeId));
        Map<Long, HrmEmployeeRespDTO> employeeMap = employeeApi.getEmployeeList(employeeIds).stream()
                .collect(Collectors.toMap(HrmEmployeeRespDTO::getId, item -> item, (a, b) -> a));
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(
                convertSet(list, EamTransferDO::getApplyUserId));

        result.forEach(vo -> {
            EamAssetDO asset = assetMap.get(vo.getAssetId());
            if (asset != null) {
                vo.setAssetName(asset.getName());
                vo.setAssetCode(asset.getAssetCode());
            }
            vo.setFromEmployeeName(nameOf(employeeMap, vo.getFromEmployeeId()));
            vo.setToEmployeeName(nameOf(employeeMap, vo.getToEmployeeId()));
            vo.setApplyUserName(nicknameOf(userMap, vo.getApplyUserId()));
        });
        return result;
    }

    private String nicknameOf(Map<Long, AdminUserRespDTO> userMap, Long userId) {
        AdminUserRespDTO user = userId != null ? userMap.get(userId) : null;
        return user != null ? user.getNickname() : null;
    }

    private String nameOf(Map<Long, HrmEmployeeRespDTO> employeeMap, Long employeeId) {
        HrmEmployeeRespDTO employee = employeeId != null ? employeeMap.get(employeeId) : null;
        return employee != null ? employee.getName() : null;
    }

}
