package cn.iocoder.yudao.module.zsjos.controller.app.partner;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.message.NotifyMessageMyPageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.message.NotifyMessageRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyMessageDO;
import cn.iocoder.yudao.module.system.service.notify.NotifyMessageService;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerAccountService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos/messages")
@Validated
public class PartnerAppMessageController {
    @Resource private NotifyMessageService notifyMessageService;
    @Resource private PartnerAccountService accountService;

    @GetMapping("/page")
    public CommonResult<PageResult<NotifyMessageRespVO>> page(@Valid NotifyMessageMyPageReqVO reqVO) {
        accountService.requireContext(getLoginUserId());
        PageResult<NotifyMessageDO> page = notifyMessageService.getMyMyNotifyMessagePage(reqVO, getLoginUserId(),
                UserTypeEnum.PARTNER.getValue());
        return success(BeanUtils.toBean(page, NotifyMessageRespVO.class));
    }

    @GetMapping("/{id}")
    public CommonResult<NotifyMessageRespVO> get(@PathVariable Long id) {
        accountService.requireContext(getLoginUserId());
        return success(BeanUtils.toBean(notifyMessageService.getMyNotifyMessage(id, getLoginUserId(),
                UserTypeEnum.PARTNER.getValue()), NotifyMessageRespVO.class));
    }

    @PutMapping("/read")
    public CommonResult<Boolean> read(@Valid @RequestBody ReadReqVO reqVO) {
        accountService.requireContext(getLoginUserId());
        notifyMessageService.updateNotifyMessageRead(reqVO.getIds(), getLoginUserId(), UserTypeEnum.PARTNER.getValue());
        return success(true);
    }

    @GetMapping("/unread-count")
    public CommonResult<Long> unreadCount() {
        accountService.requireContext(getLoginUserId());
        return success(notifyMessageService.getUnreadNotifyMessageCount(getLoginUserId(),
                UserTypeEnum.PARTNER.getValue()));
    }

    @Data
    public static class ReadReqVO {
        @NotEmpty private List<Long> ids;
    }
}
