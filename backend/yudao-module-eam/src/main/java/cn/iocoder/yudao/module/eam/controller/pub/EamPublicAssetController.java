package cn.iocoder.yudao.module.eam.controller.pub;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.eam.controller.pub.vo.EamPublicAssetClearUsageReqVO;
import cn.iocoder.yudao.module.eam.controller.pub.vo.EamPublicAssetUpdateReqVO;
import cn.iocoder.yudao.module.eam.service.publicedit.EamPublicEditService;
import cn.iocoder.yudao.module.eam.service.publicedit.EamPublicEditService.PublicAsset;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/eam/asset")
@TenantIgnore
public class EamPublicAssetController {
    @Resource private EamPublicEditService service;
    @GetMapping public CommonResult<PublicAsset> get(@RequestParam String assetCode) { return success(service.getPublicAssetByCode(assetCode)); }
    @PostMapping("/verify") public CommonResult<Boolean> verify(@RequestParam String assetCode,
                                                                 @RequestHeader("X-EAM-Edit-Code") String code,
                                                                 HttpServletRequest request) {
        service.verifyPublicEditCode(assetCode, code, request.getRemoteAddr()); return success(true);
    }
    @PutMapping public CommonResult<Boolean> update(@RequestParam String assetCode, @Valid @RequestBody EamPublicAssetUpdateReqVO req,
                                                                  @RequestHeader("X-EAM-Edit-Code") String code, HttpServletRequest request) {
        service.updatePublicAssetByCode(assetCode, req, code, request.getRemoteAddr()); return success(true);
    }

    @PutMapping("/clear-usage")
    public CommonResult<Boolean> clearUsage(@RequestParam String assetCode,
                                            @Valid @RequestBody EamPublicAssetClearUsageReqVO req,
                                            @RequestHeader("X-EAM-Edit-Code") String code,
                                            HttpServletRequest request) {
        service.clearPublicAssetUsage(assetCode, req.getVersion(), code, request.getRemoteAddr());
        return success(true);
    }
}
