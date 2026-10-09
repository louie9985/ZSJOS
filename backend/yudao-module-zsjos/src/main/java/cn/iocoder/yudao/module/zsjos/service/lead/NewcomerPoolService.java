package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.NewcomerPoolConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.NewcomerPoolDailyDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.NewcomerPoolConfigMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.NewcomerPoolDailyMapper;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;

@Service
public class NewcomerPoolService {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private Clock clock = Clock.system(BEIJING);

    @Resource private NewcomerPoolConfigMapper configMapper;
    @Resource private NewcomerPoolDailyMapper dailyMapper;

    public LocalDate today() { return LocalDate.now(clock); }

    public NewcomerPoolConfigDO config(Long userId) { return configMapper.selectByUserId(userId); }

    public int accepted(Long userId, LocalDate date) {
        NewcomerPoolDailyDO daily = dailyMapper.selectByUserAndDate(userId, date);
        return daily == null ? 0 : daily.getAcceptedCount();
    }

    public Set<Long> availableUsers(Collection<Long> userIds, LocalDate date) {
        Set<Long> result = new HashSet<>();
        if (userIds.isEmpty()) return result;
        var configs = configMapper.selectList(new LambdaQueryWrapperX<NewcomerPoolConfigDO>()
                .in(NewcomerPoolConfigDO::getUserId, userIds).eq(NewcomerPoolConfigDO::getEnabled, true));
        if (configs.isEmpty()) return result;
        Map<Long, Integer> counts = dailyMapper.selectList(new LambdaQueryWrapperX<NewcomerPoolDailyDO>()
                .in(NewcomerPoolDailyDO::getUserId, configs.stream().map(NewcomerPoolConfigDO::getUserId).toList())
                .eq(NewcomerPoolDailyDO::getBusinessDate, date)).stream()
                .collect(Collectors.toMap(NewcomerPoolDailyDO::getUserId, NewcomerPoolDailyDO::getAcceptedCount));
        for (NewcomerPoolConfigDO config : configs) {
            if (counts.getOrDefault(config.getUserId(), 0) < config.getDailyQuota()) result.add(config.getUserId());
        }
        return result;
    }

    public void update(Long userId, boolean enabled, int dailyQuota) {
        configMapper.save(TenantContextHolder.getRequiredTenantId(), userId, enabled, dailyQuota,
                String.valueOf(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId()));
    }

    public boolean lockAvailable(Long userId) {
        // Current reads serialize dispatch with quota edits and acceptance, avoiding an old transaction snapshot.
        NewcomerPoolConfigDO config = configMapper.selectByUserIdForUpdate(userId);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) return false;
        NewcomerPoolDailyDO daily = dailyMapper.selectByUserAndDateForUpdate(userId, today());
        return daily == null || daily.getAcceptedCount() < config.getDailyQuota();
    }

    public void recordAcceptance(Long userId) {
        configMapper.selectByUserIdForUpdate(userId);
        dailyMapper.increment(TenantContextHolder.getRequiredTenantId(), userId, today());
    }
}
