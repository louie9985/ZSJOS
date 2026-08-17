package cn.iocoder.yudao.module.zsjos.controller.admin.personnel;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.*;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerManagementService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController @RequestMapping("/zsjos/partner")
public class PartnerManagementController {
    @Resource private PartnerManagementService service;
    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('zsjos:partner:create')")
    public CommonResult<Long> create(@Valid @RequestBody PartnerCreateReqVO reqVO) { return success(service.create(reqVO)); }
    @GetMapping("/list") @PreAuthorize("@ss.hasPermission('zsjos:partner:query')")
    public CommonResult<List<PartnerRespVO>> list() { return success(service.list()); }
    @PutMapping("/{id}/disable") @PreAuthorize("@ss.hasPermission('zsjos:partner:update-state')")
    public CommonResult<Boolean> disable(@PathVariable Long id, @Valid @RequestBody PartnerStateReqVO reqVO) { service.disable(id, reqVO); return success(true); }
    @PutMapping("/{id}/enable") @PreAuthorize("@ss.hasPermission('zsjos:partner:update-state')")
    public CommonResult<Boolean> enable(@PathVariable Long id, @Valid @RequestBody PartnerStateReqVO reqVO) { service.enable(id, reqVO); return success(true); }
    @PostMapping("/{id}/convert") @PreAuthorize("@ss.hasPermission('zsjos:partner:convert')")
    public CommonResult<Boolean> convert(@PathVariable Long id, @Valid @RequestBody PartnerConvertReqVO reqVO) { service.convert(id, reqVO); return success(true); }
    @PutMapping("/{id}/mobile") @PreAuthorize("@ss.hasPermission('zsjos:partner:update-state')")
    public CommonResult<Boolean> updateMobile(@PathVariable Long id, @Valid @RequestBody PartnerMobileUpdateReqVO reqVO) { service.updateMobile(id, reqVO); return success(true); }
    @PutMapping("/{id}/reset-password") @PreAuthorize("@ss.hasPermission('zsjos:partner:update-state')")
    public CommonResult<Boolean> resetPassword(@PathVariable Long id, @Valid @RequestBody PartnerPasswordResetReqVO reqVO) { service.resetPassword(id, reqVO); return success(true); }
}
