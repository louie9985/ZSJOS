package cn.iocoder.yudao.module.zsjos.controller.admin.personnel;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.SubordinatePartnerPageReqVO;
import cn.iocoder.yudao.module.zsjos.service.personnel.SubordinatePartnerService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos/subordinate-partners")
public class SubordinatePartnerController {
    @Resource private SubordinatePartnerService service;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('zsjos:subordinate-partner:query')")
    public CommonResult<PageResult<PartnerRespVO>> page(@Valid SubordinatePartnerPageReqVO reqVO) {
        return success(service.getPage(reqVO, getLoginUserId()));
    }

    @GetMapping("/{partnerId}/leads/page")
    @PreAuthorize("@ss.hasPermission('zsjos:subordinate-partner:query')")
    public CommonResult<PageResult<LeadManagementRespVO>> leadPage(@PathVariable Long partnerId,
            @Valid LeadManagementPageReqVO reqVO) {
        return success(service.getLeadPage(partnerId, reqVO, getLoginUserId()));
    }

    @GetMapping("/leads/{leadId}")
    @PreAuthorize("@ss.hasPermission('zsjos:subordinate-partner:query')")
    public CommonResult<LeadManagementRespVO> lead(@PathVariable Long leadId) {
        return success(service.getLead(leadId, getLoginUserId()));
    }
}
