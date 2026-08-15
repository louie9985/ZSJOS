package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyBusinessOutboxDO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyRuleDO;
import cn.iocoder.yudao.module.system.dal.mysql.notify.NotifyBusinessOutboxMapper;
import cn.iocoder.yudao.module.system.dal.mysql.notify.NotifyMessageMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class NotifyBusinessOutboxService {
    private static final int[] RETRY_DELAYS_SECONDS = {1, 5, 30};
    @Resource private NotifyBusinessOutboxMapper outboxMapper;
    @Resource private NotifyBusinessEventProcessor eventProcessor;
    @Resource private NotifyMessageMapper notifyMessageMapper;

    @Transactional(propagation = Propagation.REQUIRED)
    public void enqueue(NotifyBusinessEvent event, List<NotifyRuleDO> rules) {
        LocalDateTime now = LocalDateTime.now();
        for (NotifyRuleDO rule : rules) {
            NotifyBusinessOutboxDO outbox = new NotifyBusinessOutboxDO();
            outbox.setTenantId(event.getTenantId()); outbox.setSceneCode(event.getSceneCode());
            outbox.setSourceEventKey(event.getSourceEventKey()); outbox.setTargetRuleId(rule.getId());
            outbox.setBizType(event.getBizType()); outbox.setBizId(event.getBizId());
            outbox.setOperatorUserId(event.getOperatorUserId()); outbox.setOccurredAt(event.getOccurredAt());
            outbox.setPayload(JsonUtils.toJsonString(event.getPayload())); outbox.setStatus("pending");
            outbox.setAttemptCount(0); outbox.setNextAttemptAt(now);
            try {
                outboxMapper.insert(outbox);
            } catch (DuplicateKeyException exception) {
                if (outboxMapper.selectByEventAndRule(event.getTenantId(), event.getSourceEventKey(), rule.getId()) == null) {
                    throw exception;
                }
            }
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void deliverDue() {
        LocalDateTime now = LocalDateTime.now();
        for (NotifyBusinessOutboxDO outbox : outboxMapper.selectDue(now, 100)) {
            try {
                String claimToken = UUID.randomUUID().toString();
                if (outboxMapper.claim(outbox.getId(), now, now.plusMinutes(2), claimToken) != 1) {
                    continue;
                }
                outbox.setClaimToken(claimToken);
                deliver(outbox);
            } catch (Exception exception) {
                log.warn("[deliverDue][outboxId({}) isolated delivery failure]", outbox.getId(), exception);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void deliver(NotifyBusinessOutboxDO outbox) {
        LocalDateTime now = LocalDateTime.now();
        String claimToken = outbox.getClaimToken();
        try {
            Map<String, Object> payload = outbox.getPayload() == null ? Map.of()
                    : JsonUtils.parseObject(outbox.getPayload(), Map.class);
            NotifySendResult result = eventProcessor.processConfirmed(NotifyBusinessEvent.builder()
                    .tenantId(outbox.getTenantId()).sceneCode(outbox.getSceneCode())
                    .sourceEventKey(outbox.getSourceEventKey()).targetRuleId(outbox.getTargetRuleId())
                    .bizType(outbox.getBizType()).bizId(outbox.getBizId())
                    .operatorUserId(outbox.getOperatorUserId()).occurredAt(outbox.getOccurredAt())
                    .payload(payload).build());
            if (result.isSuccess()) {
                outbox.setStatus("succeeded"); outbox.setSucceededAt(now); outbox.setLeaseUntil(null);
                outbox.setLastError(null); outbox.setClaimToken(null);
                outboxMapper.updateDeliveryState(outbox, claimToken, now); return;
            }
            fail(outbox, result.getErrorCode() + ": " + result.getErrorMessage(), result.isRetryable(), claimToken, now);
        } catch (Exception exception) {
            log.warn("[deliver][outboxId({}) failed]", outbox.getId(), exception);
            // Delivery-contract failures are returned as NotifySendResult. Exceptions escaping that boundary are
            // retried only when Spring identifies a transient database failure; malformed payloads and programming
            // errors must not consume the retry schedule repeatedly.
            fail(outbox, exception.getMessage(), exception instanceof TransientDataAccessException,
                    claimToken, now);
        }
    }

    private void fail(NotifyBusinessOutboxDO outbox, String error, boolean retryable,
                      String claimToken, LocalDateTime now) {
        int attempts = outbox.getAttemptCount() + 1;
        outbox.setAttemptCount(attempts); outbox.setLeaseUntil(null);
        outbox.setClaimToken(null);
        outbox.setLastError(error == null ? "通知投递失败" : error.substring(0, Math.min(error.length(), 1000)));
        if (retryable && attempts <= RETRY_DELAYS_SECONDS.length) {
            outbox.setStatus("pending"); outbox.setNextAttemptAt(now.plusSeconds(RETRY_DELAYS_SECONDS[attempts - 1]));
        } else {
            outbox.setStatus("failed"); outbox.setNextAttemptAt(now);
        }
        outboxMapper.updateDeliveryState(outbox, claimToken, now);
    }

    @Scheduled(cron = "0 20 3 * * ?")
    public void cleanExpired() {
        LocalDateTime now = LocalDateTime.now();
        outboxMapper.deleteExpired(now.minusDays(30), now.minusDays(90));
        notifyMessageMapper.deleteCreatedBefore(now.minusYears(3));
    }
}
