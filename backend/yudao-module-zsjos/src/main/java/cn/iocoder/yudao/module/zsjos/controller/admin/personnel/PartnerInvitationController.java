package cn.iocoder.yudao.module.zsjos.controller.admin.personnel;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerInvitationCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerInvitationPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerInvitationRespVO;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerInvitationService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos/partner-invitation")
@Validated
public class PartnerInvitationController {

    @Resource
    private PartnerInvitationService invitationService;

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('zsjos:partner-invitation:create')")
    public CommonResult<PartnerInvitationRespVO> create(@Valid @RequestBody PartnerInvitationCreateReqVO reqVO) {
        return success(invitationService.create(reqVO, getLoginUserId()));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('zsjos:partner-invitation:query')")
    public CommonResult<PageResult<PartnerInvitationRespVO>> page(@Valid PartnerInvitationPageReqVO reqVO) {
        return success(invitationService.getPage(reqVO));
    }

    @PutMapping("/{id}/void")
    @PreAuthorize("@ss.hasPermission('zsjos:partner-invitation:void')")
    public CommonResult<Boolean> voidInvitation(@PathVariable Long id) {
        invitationService.voidInvitation(id, getLoginUserId());
        return success(true);
    }

    @GetMapping("/operator-candidates")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:partner-invitation:query', 'zsjos:partner-invitation:create')")
    public CommonResult<PageResult<LeadAssignmentUserRespVO>> operatorCandidates(
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "100") Integer pageSize) {
        return success(invitationService.getOperatorCandidatePage(keyword, pageNo, pageSize));
    }
}
