package cn.iocoder.yudao.module.zsjos.service.order;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderCommandDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderCommandMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.SALES_ORDER_IDEMPOTENCY_CONFLICT;

@Service
public class SalesOrderCommandService {
    @Resource private SalesOrderCommandMapper commandMapper;

    public boolean replay(String key, Command command) {
        SalesOrderCommandDO existing = commandMapper.selectByIdempotencyKey(key);
        if (existing == null) return false;
        requireSame(existing, command);
        return true;
    }

    public boolean replayDecision(String key, Command command) {
        SalesOrderCommandDO existing = commandMapper.selectByIdempotencyKey(key);
        if (existing == null) return false;
        requireSame(existing, new Command(command.orderId(), command.roundId(), command.processInstanceId(),
                command.commandType(), existing.getTaskDefinitionKey(), command.taskId(),
                command.operatorUserId(), command.requestFingerprint()));
        return true;
    }

    public void register(String key, Command command) {
        SalesOrderCommandDO row = new SalesOrderCommandDO();
        row.setIdempotencyKey(key); row.setOrderId(command.orderId()); row.setApprovalRoundId(command.roundId());
        row.setProcessInstanceId(command.processInstanceId()); row.setCommandType(command.commandType());
        row.setTaskDefinitionKey(command.taskDefinitionKey()); row.setBpmTaskId(command.taskId());
        row.setOperatorUserId(command.operatorUserId()); row.setRequestFingerprint(command.requestFingerprint());
        if (commandMapper.insertIgnore(TenantContextHolder.getRequiredTenantId(), row) == 1) return;
        SalesOrderCommandDO existing = commandMapper.selectByIdempotencyKey(key);
        if (existing == null) throw exception(SALES_ORDER_IDEMPOTENCY_CONFLICT);
        requireSame(existing, command);
    }

    public String fingerprint(Object... values) {
        StringBuilder source = new StringBuilder();
        for (Object value : values) source.append(value == null ? "<null>" : value).append('\u001f');
        return DigestUtil.sha256Hex(source.toString());
    }

    private void requireSame(SalesOrderCommandDO row, Command command) {
        if (!Objects.equals(row.getOrderId(), command.orderId())
                || !Objects.equals(row.getApprovalRoundId(), command.roundId())
                || !Objects.equals(row.getProcessInstanceId(), command.processInstanceId())
                || !Objects.equals(row.getCommandType(), command.commandType())
                || !Objects.equals(row.getTaskDefinitionKey(), command.taskDefinitionKey())
                || !Objects.equals(row.getBpmTaskId(), command.taskId())
                || !Objects.equals(row.getOperatorUserId(), command.operatorUserId())
                || !Objects.equals(row.getRequestFingerprint(), command.requestFingerprint())) {
            throw exception(SALES_ORDER_IDEMPOTENCY_CONFLICT);
        }
    }

    public record Command(Long orderId, Long roundId, String processInstanceId, String commandType,
                          String taskDefinitionKey, String taskId, Long operatorUserId,
                          String requestFingerprint) {}
}
