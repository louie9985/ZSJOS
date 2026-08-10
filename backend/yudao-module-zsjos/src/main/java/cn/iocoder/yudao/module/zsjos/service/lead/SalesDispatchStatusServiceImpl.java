package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.dispatch.SalesDispatchStatusRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.SalesDispatchPreferenceDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.SalesDispatchPreferenceMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;

@Service
public class SalesDispatchStatusServiceImpl implements SalesDispatchStatusService {

    @Resource private LeadAssignmentService assignmentService;
    @Resource private SalesDispatchPreferenceMapper preferenceMapper;
    @Resource private LeadDispatchRedisRepository redisRepository;

    @Override
    public SalesDispatchStatusRespVO getMyStatus(Long userId) {
        boolean eligible = isEligible(userId);
        boolean accepting = eligible && isAccepting(userId);
        if (eligible) {
            redisRepository.cacheMode(userId, accepting);
        }
        return buildStatus(userId, eligible, accepting);
    }

    @Override
    public SalesDispatchStatusRespVO heartbeat(Long userId) {
        boolean eligible = isEligible(userId);
        if (!eligible) {
            redisRepository.offline(userId);
            return buildStatus(userId, false, false);
        }
        boolean accepting = isAccepting(userId);
        redisRepository.heartbeat(userId, accepting);
        return buildStatus(userId, true, accepting);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesDispatchStatusRespVO updateMode(Long userId, boolean accepting) {
        requireEligible(userId);
        savePreference(userId, accepting);
        redisRepository.cacheMode(userId, accepting);
        return buildStatus(userId, true, accepting);
    }

    @Override
    public SalesDispatchStatusRespVO offline(Long userId) {
        boolean eligible = isEligible(userId);
        boolean accepting = eligible && isAccepting(userId);
        redisRepository.offline(userId);
        return buildStatus(userId, eligible, accepting);
    }

    private void savePreference(Long userId, boolean accepting) {
        SalesDispatchPreferenceDO preference = preferenceMapper.selectByUserId(userId);
        if (preference != null) {
            preference.setAcceptingEnabled(accepting);
            preferenceMapper.updateById(preference);
            return;
        }
        preference = new SalesDispatchPreferenceDO();
        preference.setUserId(userId);
        preference.setAcceptingEnabled(accepting);
        try {
            preferenceMapper.insert(preference);
        } catch (DuplicateKeyException duplicate) {
            SalesDispatchPreferenceDO existing = preferenceMapper.selectByUserId(userId);
            existing.setAcceptingEnabled(accepting);
            preferenceMapper.updateById(existing);
        }
    }

    private boolean isAccepting(Long userId) {
        SalesDispatchPreferenceDO preference = preferenceMapper.selectByUserId(userId);
        return preference != null && Boolean.TRUE.equals(preference.getAcceptingEnabled());
    }

    private boolean isEligible(Long userId) {
        return assignmentService.getEligibleSalesUsers().stream().anyMatch(user -> userId.equals(user.getId()));
    }

    private void requireEligible(Long userId) {
        if (!isEligible(userId)) {
            throw exception(LEAD_PERMISSION_DENIED);
        }
    }

    private SalesDispatchStatusRespVO buildStatus(Long userId, boolean eligible, boolean accepting) {
        boolean online = eligible && redisRepository.isOnline(userId);
        SalesDispatchStatusRespVO result = new SalesDispatchStatusRespVO();
        result.setEligible(eligible);
        result.setPresence(online ? "online" : "offline");
        result.setMode(accepting ? "accepting" : "paused");
        result.setEffectiveStatus(!online ? "offline" : accepting ? "online" : "busy");
        return result;
    }
}
