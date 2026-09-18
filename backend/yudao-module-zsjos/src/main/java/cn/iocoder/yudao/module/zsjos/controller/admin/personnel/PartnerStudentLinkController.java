package cn.iocoder.yudao.module.zsjos.controller.admin.personnel;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerStudentManualLinkService;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerStudentLinkRespVO;
import cn.iocoder.yudao.framework.common.pojo.CommonResult; import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerStudentLinkService; import jakarta.annotation.Resource; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success; import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
@RestController @RequestMapping("/zsjos/partner-student-link") public class PartnerStudentLinkController { @Resource private PartnerStudentLinkService service;
 @Resource private PartnerStudentManualLinkService manualService;
 @GetMapping("/student") @PreAuthorize("@ss.hasPermission('zsjos:partner:manage-all')") public CommonResult<PartnerStudentLinkRespVO> student(@RequestParam Long studentPersonId){return success(manualService.getStudent(studentPersonId));}
 @PostMapping("/bind") @PreAuthorize("@ss.hasPermission('zsjos:partner:manage-all')") public CommonResult<Boolean> bind(@RequestParam Long partnerId,@RequestParam Long studentPersonId,@RequestParam(required=false) String reason){manualService.bind(partnerId,studentPersonId,reason,getLoginUserId());return success(true);}
 @PostMapping("/unbind") @PreAuthorize("@ss.hasPermission('zsjos:partner:manage-all')") public CommonResult<Boolean> unbind(@RequestParam Long partnerId,@RequestParam(required=false) String reason){service.unbind(partnerId,reason,getLoginUserId());return success(true);}
}
