package cn.iocoder.yudao.module.zsjos.service.production;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.production.ProductionTicketCommandDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.production.ProductionTicketCommandMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PRODUCTION_TICKET_ACTION_IDEMPOTENCY_CONFLICT;

@Service
public class ProductionTicketCommandService {
    @Resource private ProductionTicketCommandMapper commandMapper;

    public <T> Claim<T> begin(String idempotencyKey, Command command, Class<T> resultType) {
        ProductionTicketCommandDO existing = commandMapper.selectByOperatorAndKey(
                command.operatorUserId(), idempotencyKey);
        if (existing != null) return replay(existing, command, resultType);
        ProductionTicketCommandDO row = new ProductionTicketCommandDO();
        row.setOperatorUserId(command.operatorUserId()); row.setIdempotencyKey(idempotencyKey);
        row.setActionType(command.actionType()); row.setAccountId(command.accountId());
        row.setTicketId(command.ticketId()); row.setExpectedVersion(command.expectedVersion());
        row.setRequestFingerprint(command.requestFingerprint()); row.setCompleted(false);
        try {
            commandMapper.insert(row);
            return new Claim<>(true, null);
        } catch (DuplicateKeyException ex) {
            return replay(commandMapper.selectByOperatorAndKey(command.operatorUserId(), idempotencyKey),
                    command, resultType);
        }
    }

    public void complete(String idempotencyKey, Long operatorUserId, Object result) {
        if (commandMapper.complete(operatorUserId, idempotencyKey, JsonUtils.toJsonString(result)) != 1) {
            throw exception(PRODUCTION_TICKET_ACTION_IDEMPOTENCY_CONFLICT);
        }
    }

    public String fingerprint(Object... values) {
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(Arrays.asList(values)));
    }

    private <T> Claim<T> replay(ProductionTicketCommandDO row, Command command, Class<T> resultType) {
        if (row == null || !Objects.equals(row.getActionType(), command.actionType())
                || !Objects.equals(row.getAccountId(), command.accountId())
                || !Objects.equals(row.getTicketId(), command.ticketId())
                || !Objects.equals(row.getExpectedVersion(), command.expectedVersion())
                || !Objects.equals(row.getRequestFingerprint(), command.requestFingerprint())
                || !Boolean.TRUE.equals(row.getCompleted()) || row.getResultJson() == null) {
            throw exception(PRODUCTION_TICKET_ACTION_IDEMPOTENCY_CONFLICT);
        }
        return new Claim<>(false, JsonUtils.parseObject(row.getResultJson(), resultType));
    }

    public record Command(String actionType, Long accountId, Long ticketId, Integer expectedVersion,
                          Long operatorUserId, String requestFingerprint) {}

    public record Claim<T>(boolean created, T result) {}
}
