package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.rule.NotifyDeliveryPageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.rule.NotifyDeliveryRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyBusinessOutboxDO;
import cn.iocoder.yudao.module.system.dal.mysql.notify.NotifyBusinessOutboxMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class NotifyDeliveryQueryService {
    @Resource private NotifyBusinessOutboxMapper mapper;

    public PageResult<NotifyDeliveryRespVO> page(NotifyDeliveryPageReqVO request) {
        var page = mapper.selectPage(request, new LambdaQueryWrapperX<NotifyBusinessOutboxDO>()
                .eq(NotifyBusinessOutboxDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eqIfPresent(NotifyBusinessOutboxDO::getTargetRuleId, request.getRuleId())
                .eqIfPresent(NotifyBusinessOutboxDO::getSceneCode, request.getSceneCode())
                .eqIfPresent(NotifyBusinessOutboxDO::getStatus, request.getStatus())
                .orderByDesc(NotifyBusinessOutboxDO::getId));
        return new PageResult<>(page.getList().stream().map(this::project).toList(), page.getTotal());
    }

    NotifyDeliveryRespVO project(NotifyBusinessOutboxDO row) {
        NotifyDeliveryRespVO result = new NotifyDeliveryRespVO();
        result.setId(row.getId()); result.setRuleId(row.getTargetRuleId()); result.setSceneCode(row.getSceneCode());
        result.setStatus(row.getStatus()); result.setAttemptCount(row.getAttemptCount());
        result.setNextAttemptAt(row.getNextAttemptAt()); result.setCreateTime(row.getCreateTime());
        result.setSucceededAt(row.getSucceededAt()); result.setErrorCode(safeCode(row.getLastError()));
        result.setChannelCode("in_app"); result.setRecipients(List.of());
        if (row.getPayload() == null) return result;
        try {
            Map<?, ?> payload = JsonUtils.parseObjectQuietly(row.getPayload(), Map.class);
            if (payload == null) { result.setErrorCode("NOTIFY_PAYLOAD_INVALID"); return result; }
            if (!WecomOutboxPayload.FORMAT.equals(payload.get("deliveryFormat"))) return result;
            result.setChannelCode("wecom");
            var state = JsonUtils.parseObjectQuietly(row.getPayload(), WecomOutboxPayload.class);
            if (state.getRecipients() != null) result.setRecipients(state.getRecipients().stream()
                    .map(item -> new NotifyDeliveryRespVO.Recipient(item.getContext().getUserType(),
                            item.getContext().getUserId(), item.getStatus(), item.getAttempts(), safeCode(item.getErrorCode())))
                    .toList());
        } catch (RuntimeException exception) { result.setErrorCode("NOTIFY_PAYLOAD_INVALID"); }
        return result;
    }

    private String safeCode(String value) {
        if (value == null) return null;
        String code = value.split(":", 2)[0];
        return code.matches("[A-Z][A-Z0-9_\\-]{0,80}") ? code : "NOTIFY_DELIVERY_FAILED";
    }
}
