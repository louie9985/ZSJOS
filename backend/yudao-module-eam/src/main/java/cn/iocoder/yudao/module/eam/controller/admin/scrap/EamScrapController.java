package cn.iocoder.yudao.module.eam.controller.admin.scrap;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.eam.controller.admin.scrap.vo.EamScrapCreateReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.scrap.vo.EamScrapPageReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.scrap.vo.EamScrapRespVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.scrap.EamScrapDO;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetService;
import cn.iocoder.yudao.module.eam.service.scrap.EamScrapService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

@Tag(name = "管理后台 - EAM 报废")
@RestController
@RequestMapping("/eam/scrap")
@Validated
public class EamScrapController {

    @Resource
    private EamScrapService scrapService;
    @Resource
    private EamAssetService assetService;
    @Resource
    private AdminUserApi adminUserApi;

    @PostMapping("/create")
    @Operation(summary = "申请报废")
    @PreAuthorize("@ss.hasPermission('eam:scrap:create')")
    public CommonResult<Long> createScrap(@Valid @RequestBody EamScrapCreateReqVO reqVO) {
        return success(scrapService.createScrap(reqVO));
    }

    @PutMapping("/approve")
    @Operation(summary = "审批通过报废单")
    @Parameter(name = "id", description = "单据编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:scrap:update')")
    public CommonResult<Boolean> approveScrap(@RequestParam("id") Long id) {
        scrapService.approveScrap(id);
        return success(true);
    }

    @PutMapping("/reject")
    @Operation(summary = "驳回报废单")
    @Parameter(name = "id", description = "单据编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:scrap:update')")
    public CommonResult<Boolean> rejectScrap(@RequestParam("id") Long id,
                                             @RequestParam(value = "reason", required = false) String reason) {
        scrapService.rejectScrap(id, reason);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得报废单")
    @Parameter(name = "id", description = "单据编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:scrap:query')")
    public CommonResult<EamScrapRespVO> getScrap(@RequestParam("id") Long id) {
        EamScrapDO scrap = scrapService.getScrap(id);
        if (scrap == null) {
            return success(null);
        }
        return success(buildScrapVOList(List.of(scrap)).get(0));
    }

    @GetMapping("/page")
    @Operation(summary = "获得报废单分页")
    @PreAuthorize("@ss.hasPermission('eam:scrap:query')")
    public CommonResult<PageResult<EamScrapRespVO>> getScrapPage(@Valid EamScrapPageReqVO reqVO) {
        PageResult<EamScrapDO> pageResult = scrapService.getScrapPage(reqVO);
        return success(new PageResult<>(buildScrapVOList(pageResult.getList()), pageResult.getTotal()));
    }

    private List<EamScrapRespVO> buildScrapVOList(List<EamScrapDO> list) {
        List<EamScrapRespVO> result = BeanUtils.toBean(list, EamScrapRespVO.class);
        if (result.isEmpty()) {
            return result;
        }
        Map<Long, EamAssetDO> assetMap = assetService.getAssetList(
                        convertSet(list, EamScrapDO::getAssetId)).stream()
                .collect(Collectors.toMap(EamAssetDO::getId, a -> a, (a, b) -> a));
        Map<Long, AdminUserRespDTO> userMap =
                adminUserApi.getUserMap(convertSet(list, EamScrapDO::getApplyUserId));

        result.forEach(vo -> {
            EamAssetDO asset = assetMap.get(vo.getAssetId());
            if (asset != null) {
                vo.setAssetName(asset.getName());
                vo.setAssetCode(asset.getAssetCode());
            }
            AdminUserRespDTO user = userMap.get(vo.getApplyUserId());
            vo.setApplyUserName(user != null ? user.getNickname() : null);
        });
        return result;
    }

}
