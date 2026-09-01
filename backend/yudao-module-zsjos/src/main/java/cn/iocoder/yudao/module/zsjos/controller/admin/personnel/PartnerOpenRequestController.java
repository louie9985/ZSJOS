package cn.iocoder.yudao.module.zsjos.controller.admin.personnel;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerOpenRequestCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerOpenRequestPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerOpenRequestRespVO;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerOpenRequestService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos/partner-open-request")
@Validated
public class PartnerOpenRequestController {

    @Resource private PartnerOpenRequestService partnerOpenRequestService;

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('zsjos:partner-open-request:create')")
    public CommonResult<Long> create(@Valid @RequestBody PartnerOpenRequestCreateReqVO reqVO) {
        return success(partnerOpenRequestService.create(reqVO, getLoginUserId()));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:partner-open-request:query', 'zsjos:partner-open-request:review')")
    public CommonResult<PageResult<PartnerOpenRequestRespVO>> page(@Valid PartnerOpenRequestPageReqVO reqVO) {
        return success(partnerOpenRequestService.getPage(reqVO, getLoginUserId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:partner-open-request:query', 'zsjos:partner-open-request:review')")
    public CommonResult<PartnerOpenRequestRespVO> get(@PathVariable Long id) {
        return success(partnerOpenRequestService.getDetail(id, getLoginUserId()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@ss.hasPermission('zsjos:partner-open-request:cancel')")
    public CommonResult<Boolean> cancel(@PathVariable Long id) {
        partnerOpenRequestService.cancel(id, getLoginUserId());
        return success(true);
    }

    @GetMapping("/assignee-candidates")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:partner-open-request:create', 'zsjos:partner-open-request:query')")
    public CommonResult<PageResult<LeadAssignmentUserRespVO>> assigneeCandidates(
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "100") Integer pageSize) {
        return success(partnerOpenRequestService.getAssigneeCandidatePage(deptId, keyword, pageNo, pageSize));
    }
}
