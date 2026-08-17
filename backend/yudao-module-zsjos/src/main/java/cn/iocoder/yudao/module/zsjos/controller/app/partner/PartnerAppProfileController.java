package cn.iocoder.yudao.module.zsjos.controller.app.partner;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerPasswordUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerProfileRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerProfileUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerAccountService;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerProfileService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos/profile")
public class PartnerAppProfileController {
    @Resource private PartnerProfileService profileService;
    @Resource private PartnerAccountService accountService;

    @GetMapping("/get")
    public CommonResult<PartnerProfileRespVO> get() {
        return success(profileService.get(getLoginUserId()));
    }

    @PutMapping("/update")
    public CommonResult<Boolean> update(@Valid @RequestBody PartnerProfileUpdateReqVO reqVO) {
        profileService.update(getLoginUserId(), reqVO);
        return success(true);
    }

    @PutMapping("/update-password")
    public CommonResult<Boolean> updatePassword(@Valid @RequestBody PartnerPasswordUpdateReqVO reqVO) {
        accountService.updatePassword(getLoginUserId(), reqVO.getOldPassword(), reqVO.getNewPassword());
        return success(true);
    }
}
