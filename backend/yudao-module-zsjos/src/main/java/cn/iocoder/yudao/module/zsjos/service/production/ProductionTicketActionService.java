package cn.iocoder.yudao.module.zsjos.service.production;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PRODUCTION_TICKET_ASSIGNMENT_REJECT_REASON_REQUIRED;

@Service
public class ProductionTicketActionService {
    private static final String ACTION_REJECT_ASSIGNMENT = "reject-assignment";
    private static final String ACTION_CLAIM = "claim";

    @Resource private ProductionTicketCommandService commandService;
    @Resource private ProductionTicketService ticketService;

    @Transactional(rollbackFor = Exception.class)
    public boolean rejectAssignment(Long id, Integer version, String reason, String idempotencyKey,
                                    Long operatorUserId) {
        String normalized = normalizedReason(reason, PRODUCTION_TICKET_ASSIGNMENT_REJECT_REASON_REQUIRED);
        String fingerprint = commandService.fingerprint(
                ACTION_REJECT_ASSIGNMENT, id, version, normalized, operatorUserId);
        var claim = commandService.begin(idempotencyKey,
                new ProductionTicketCommandService.Command(ACTION_REJECT_ASSIGNMENT, null, id, version,
                        operatorUserId, fingerprint), Boolean.class);
        if (!claim.created()) return claim.result();
        ticketService.rejectAssignment(id, version, normalized, idempotencyKey);
        commandService.complete(idempotencyKey, operatorUserId, true);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean claim(Long id, Integer version, String idempotencyKey, Long operatorUserId) {
        String fingerprint = commandService.fingerprint(ACTION_CLAIM, id, version, operatorUserId);
        var claim = commandService.begin(idempotencyKey,
                new ProductionTicketCommandService.Command(ACTION_CLAIM, null, id, version,
                        operatorUserId, fingerprint), Boolean.class);
        if (!claim.created()) return claim.result();
        ticketService.claim(id, version, idempotencyKey, operatorUserId);
        commandService.complete(idempotencyKey, operatorUserId, true);
        return true;
    }

    private static String normalizedReason(String reason, ErrorCode error) {
        String normalized = reason == null ? null : reason.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > 500) throw exception(error);
        return normalized;
    }
}
