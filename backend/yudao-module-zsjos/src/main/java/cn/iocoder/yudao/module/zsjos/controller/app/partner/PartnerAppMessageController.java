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
import lombok.EqualsAndHashCode;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos/messages")
@Validated
public class PartnerAppMessageController {
    @Resource private NotifyMessageService notifyMessageService;
    @Resource private PartnerAccountService accountService;

    private static final List<MessageGroupRespVO> GROUPS = List.of(
            new MessageGroupRespVO("all", "全部", List.of()),
            new MessageGroupRespVO("lead", "客资", List.of("lead")),
            new MessageGroupRespVO("feedback", "反馈", List.of("feedback")),
            new MessageGroupRespVO("withdrawal", "提现", List.of("withdrawal")));
    private static final Map<String, String> GROUP_BIZ_TYPE = Map.of(
            "lead", "lead",
            "feedback", "feedback",
            "withdrawal", "withdrawal");

    @GetMapping("/page")
    public CommonResult<PageResult<NotifyMessageRespVO>> page(@Valid PartnerMessagePageReqVO reqVO) {
        accountService.requireContext(getLoginUserId());
        reqVO.setBizType(GROUP_BIZ_TYPE.get(reqVO.getGroup()));
        PageResult<NotifyMessageDO> page = notifyMessageService.getMyMyNotifyMessagePage(reqVO, getLoginUserId(),
                UserTypeEnum.PARTNER.getValue());
        return success(BeanUtils.toBean(page, NotifyMessageRespVO.class));
    }

    @GetMapping("/groups")
    public CommonResult<List<MessageGroupRespVO>> groups() {
        accountService.requireContext(getLoginUserId());
        return success(GROUPS);
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

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PartnerMessagePageReqVO extends NotifyMessageMyPageReqVO {
        private String group;
    }

    public record MessageGroupRespVO(String key, String label, List<String> bizTypes) {}
}
