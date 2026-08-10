package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterAdminRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterCapabilityRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterVersionRespVO;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadInboxFilterConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
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

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 客资筛选方案")
@RestController
@RequestMapping("/zsjos/lead/inbox-filter")
@Validated
public class LeadInboxFilterConfigController {

    @Resource
    private LeadInboxFilterConfigService configService;

    @GetMapping("/get")
    @Operation(summary = "获得客资筛选方案")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-filter:query')")
    public CommonResult<LeadInboxFilterAdminRespVO> get(
            @RequestParam("audience") @Pattern(regexp = "submitter|owner") String audience) {
        return success(configService.getAdminConfig(audience));
    }

    @GetMapping("/capabilities")
    @Operation(summary = "获得客资筛选条件能力")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-filter:query')")
    public CommonResult<List<LeadInboxFilterCapabilityRespVO>> getCapabilities() {
        return success(configService.getCapabilities());
    }

    @GetMapping("/versions")
    @Operation(summary = "获得客资筛选方案版本")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-filter:query')")
    public CommonResult<List<LeadInboxFilterVersionRespVO>> getVersions(
            @RequestParam("audience") @Pattern(regexp = "submitter|owner") String audience) {
        return success(configService.getVersions(audience));
    }

    @PutMapping("/draft")
    @Operation(summary = "保存客资筛选方案草稿")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-filter:update')")
    public CommonResult<Boolean> saveDraft(@Valid @RequestBody LeadInboxFilterSaveReqVO reqVO) {
        configService.saveDraft(reqVO);
        return success(true);
    }

    @PostMapping("/publish")
    @Operation(summary = "发布客资筛选方案")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-filter:publish')")
    public CommonResult<Integer> publish(
            @RequestParam("audience") @Pattern(regexp = "submitter|owner") String audience) {
        return success(configService.publish(audience, getLoginUserId()));
    }

    @PostMapping("/rollback")
    @Operation(summary = "回滚并发布客资筛选方案")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-filter:publish')")
    public CommonResult<Integer> rollback(
            @RequestParam("audience") @Pattern(regexp = "submitter|owner") String audience,
            @RequestParam("versionNo") Integer versionNo) {
        return success(configService.rollback(audience, versionNo, getLoginUserId()));
    }
}
