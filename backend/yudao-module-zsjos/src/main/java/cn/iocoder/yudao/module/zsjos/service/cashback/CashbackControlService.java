package cn.iocoder.yudao.module.zsjos.service.cashback;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback.CashbackDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.audit.BusinessAuditLogDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.cashback.CashbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.audit.BusinessAuditLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.audit.BusinessAuditService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.CashbackConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.audit.AuditActionCatalog.*;
@Service
public class CashbackControlService {
    @Resource private CashbackMapper mapper;
    @Resource private SalesOrderMapper orders;
    @Resource private BusinessAuditService audit;
    @Resource private BusinessAuditLogMapper logs;

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "cashback", bizId = "#id", action = "block")
    public void block(Long id, CashbackControlReqVO request) { change(id, request, true); }

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "cashback", bizId = "#id", action = "unblock")
    public void unblock(Long id, CashbackControlReqVO request) { change(id, request, false); }

    private void change(Long id, CashbackControlReqVO request, boolean block) {
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (reason.isEmpty() || reason.length() > 500) throw exception(CASHBACK_CONTROL_REASON_INVALID);
        // Same tenant-bound row lock as withdrawal application: only one command consumes the source state.
        CashbackDO row = mapper.selectByIdForUpdate(id, TenantContextHolder.getRequiredTenantId());
        if (row == null) throw exception(CASHBACK_NOT_EXISTS);
        if (!Objects.equals(row.getVersion(), request.getVersion())) throw exception(CASHBACK_CONTROL_STALE);
        String from = row.getStatus();
        if (block ? !Set.of(STATUS_PENDING, STATUS_AVAILABLE).contains(from) : !STATUS_BLOCKED.equals(from)) {
            throw exception(CASHBACK_CONTROL_STALE);
        }
        String to = STATUS_BLOCKED;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime settledAt = row.getSettledAt();
        if (!block) {
            to = row.getBlockedFromStatus();
            if (to == null || !Set.of(STATUS_PENDING, STATUS_AVAILABLE).contains(to)) throw exception(CASHBACK_STATE_INVALID);
            if (STATUS_PENDING.equals(to) && row.getAvailableAt() != null && !row.getAvailableAt().isAfter(now)) {
                boolean eligible = TYPE_VALID.equals(row.getType());
                if (TYPE_DEAL.equals(row.getType()) && row.getOrderId() != null) {
                    var order = orders.selectById(row.getOrderId());
                    eligible = order != null && "effective".equals(order.getStatus());
                }
                if (eligible) { to = STATUS_AVAILABLE; settledAt = now; }
            }
        }
        int count = mapper.update(null, new LambdaUpdateWrapper<CashbackDO>()
                .eq(CashbackDO::getId, id).eq(CashbackDO::getVersion, request.getVersion()).eq(CashbackDO::getStatus, from)
                .set(CashbackDO::getStatus, to).set(CashbackDO::getVersion, row.getVersion() + 1)
                .set(CashbackDO::getBlockedFromStatus, block ? from : null)
                .set(CashbackDO::getBlockReason, block ? reason : null)
                .set(CashbackDO::getBlockedByUserId, block ? getLoginUserId() : null)
                .set(CashbackDO::getBlockedAt, block ? now : null).set(CashbackDO::getSettledAt, settledAt));
        if (count != 1) throw exception(CASHBACK_CONTROL_STALE);
        audit.record(CATEGORY_CASHBACK, block ? CASHBACK_BLOCK : CASHBACK_UNBLOCK, "cashback", String.valueOf(id),
                "finance", Map.of("reason", reason, "fromStatus", from, "toStatus", to));
    }

    @ZsjosPermission(bizType = "cashback", bizId = "#id", action = "read")
    public PageResult<CashbackControlLogRespVO> history(Long id, PageParam page) {
        var result = logs.selectPage(page, new LambdaQueryWrapper<BusinessAuditLogDO>()
                .eq(BusinessAuditLogDO::getTargetType, "cashback").eq(BusinessAuditLogDO::getTargetId, String.valueOf(id))
                .eq(BusinessAuditLogDO::getCategoryCode, CATEGORY_CASHBACK).eq(BusinessAuditLogDO::getResultStatus, "SUCCESS")
                .in(BusinessAuditLogDO::getActionCode, CASHBACK_BLOCK, CASHBACK_UNBLOCK)
                .orderByDesc(BusinessAuditLogDO::getOccurredAt).orderByDesc(BusinessAuditLogDO::getId));
        return new PageResult<>(result.getList().stream().map(log -> {
            var details = JsonUtils.parseObject(log.getDetailJson(), Map.class);
            return new CashbackControlLogRespVO().setId(log.getId())
                    .setAction(CASHBACK_BLOCK.equals(log.getActionCode()) ? "block" : "unblock")
                    .setReason(details == null ? null : (String) details.get("reason"))
                    .setOperatorName(log.getOperatorNameSnapshot()).setOccurredAt(log.getOccurredAt());
        }).toList(), result.getTotal());
    }
}
