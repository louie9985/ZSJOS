package cn.iocoder.yudao.module.zsjos.service.withdrawal;

import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.WithdrawalBatchPayoutReqVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawalBatchPayoutService {
    @Resource private WithdrawalService withdrawalService;
    @Resource private WithdrawalObjectPermissionProvider objectPermissionProvider;

    @Transactional(rollbackFor = Exception.class)
    public void recordPayouts(Long userId, WithdrawalBatchPayoutReqVO request) {
        var ids = request.getIds().stream().distinct().sorted().toList();
        // Authorize the entire selection before mutation; stable order prevents overlapping batch deadlocks.
        ids.forEach(id -> objectPermissionProvider.check(id, "payout", userId));
        // Call the proxied single-record boundary so its object check and transaction remain effective.
        ids.forEach(id -> withdrawalService.recordPayout(id, userId, request));
    }
}
