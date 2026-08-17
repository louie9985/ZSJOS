package cn.iocoder.yudao.module.system.controller.app.partner;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.message.NotifyMessageMyPageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.message.NotifyMessageRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyMessageDO;
import cn.iocoder.yudao.module.system.service.notify.NotifyMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "兼职端 - 站内消息")
@RestController
@RequestMapping("/zsjos/messages")
@Validated
@PreAuthorize("@ss.hasRole('part_time_partner')")
public class PartnerAppMessageController {

    private static final Integer ADMIN_USER_TYPE = UserTypeEnum.ADMIN.getValue();

    @Resource
    private NotifyMessageService notifyMessageService;

    @GetMapping("/page")
    @Operation(summary = "获得本人的站内消息分页")
    public CommonResult<PageResult<NotifyMessageRespVO>> getPage(@Valid NotifyMessageMyPageReqVO request) {
        PageResult<NotifyMessageDO> page = notifyMessageService.getMyMyNotifyMessagePage(
                request, getLoginUserId(), ADMIN_USER_TYPE);
        return success(BeanUtils.toBean(page, NotifyMessageRespVO.class));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获得本人的站内消息详情")
    public CommonResult<NotifyMessageRespVO> get(@PathVariable("id") Long id) {
        NotifyMessageDO message = notifyMessageService.getMyNotifyMessage(id, getLoginUserId(), ADMIN_USER_TYPE);
        return success(BeanUtils.toBean(message, NotifyMessageRespVO.class));
    }

    @PutMapping("/read")
    @Operation(summary = "将本人的站内消息标记为已读")
    public CommonResult<Boolean> read(@Valid @RequestBody ReadReqVO request) {
        notifyMessageService.updateNotifyMessageRead(request.getIds(), getLoginUserId(), ADMIN_USER_TYPE);
        return success(true);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "获得本人的未读站内消息数量")
    public CommonResult<Long> getUnreadCount() {
        return success(notifyMessageService.getUnreadNotifyMessageCount(
                getLoginUserId(), ADMIN_USER_TYPE));
    }

    @Data
    public static class ReadReqVO {
        @NotEmpty
        private List<Long> ids;
    }
}
