package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.validation.ValidationUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadActivationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadActivationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.ACTIVATED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadContactActivationService {
    @Resource private PersonMapper personMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private LeadActivationMapper activationMapper;
    @Resource private LeadNotifyEventPublisher notifyEventPublisher;
    @Resource private LeadDispatchService dispatchService;

    /** 提交权限授权按联系方式触发提醒，不授予历史客资的读取或改写权限。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean activate(String rawMobile, String rawWechat, String requestKey, Long actorId,
                            String sourceType, Long partnerId) {
        String mobile = StrUtil.trimToNull(rawMobile);
        String wechat = StrUtil.trimToNull(rawWechat);
        if (mobile == null && wechat == null) throw exception(LEAD_CONTACT_REQUIRED);
        if (mobile != null && !ValidationUtils.isMobile(mobile)) throw exception(LEAD_MOBILE_INVALID);
        if (wechat != null) wechat = wechat.toLowerCase(Locale.ROOT);
        List<Long> people = personMapper.selectDuplicateCandidates(mobile, wechat).stream()
                .map(PersonDO::getId).distinct().toList();
        List<LeadDO> leads = leadMapper.selectContactActivationLeads(people);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("mobile", mobile);
        snapshot.put("wechatId", wechat);
        // 绑定提交身份及输入，防止不同端同号身份或修改联系方式后复用请求键串单。
        String operation = DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(
                sourceType, actorId, partnerId == null ? 0L : partnerId, requestKey, snapshot)));
        boolean matched = false;
        // 所有调用按客资 ID 排序加锁；事务内去重使并发重试不会重复写激活或发送通知。
        for (Long id : leads.stream().map(LeadDO::getId).distinct().sorted().toList()) {
            LeadDO lead = leadMapper.selectByIdForUpdate(id, TenantContextHolder.getRequiredTenantId());
            if (lead == null) continue;
            matched = true;
            String key = "contact:" + operation + ":" + id;
            if (activationMapper.selectByIdempotencyKey(key) != null) continue;
            LeadActivationDO activation = new LeadActivationDO();
            activation.setPersonId(lead.getPersonId());
            activation.setLeadId(id);
            activation.setSourceType(sourceType);
            activation.setSourceUserId(partnerId == null ? actorId : null);
            activation.setPartnerId(partnerId);
            activation.setSubmissionSnapshot(JsonUtils.toJsonString(snapshot));
            activation.setNotificationTargets(JsonUtils.toJsonString(lead.getOwnerUserId() == null
                    ? List.of() : List.of(lead.getOwnerUserId())));
            activation.setActivatedAt(LocalDateTime.now());
            activation.setIdempotencyKey(key);
            activationMapper.insert(activation);
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("operatorUserType", partnerId != null
                    ? cn.iocoder.yudao.framework.common.enums.UserTypeEnum.PARTNER.getValue()
                    : cn.iocoder.yudao.framework.common.enums.UserTypeEnum.ADMIN.getValue());
            context.put("ownerUserId", lead.getOwnerUserId());
            context.put("submitterUserId", lead.getSourceUserId());
            notifyEventPublisher.publish(ACTIVATED, id, key, actorId, activation.getActivatedAt(), context);
            dispatchService.notifyActivation(lead);
        }
        return matched;
    }
}
