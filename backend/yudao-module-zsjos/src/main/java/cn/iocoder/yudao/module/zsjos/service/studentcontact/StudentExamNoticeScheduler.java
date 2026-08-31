package cn.iocoder.yudao.module.zsjos.service.studentcontact;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.framework.websocket.core.sender.WebSocketMessageSender;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactConstants.NOTIFY_EXAM_NOTICE;

/** Scans exam dates and emits the planner reminder once per date/version. */
@Component
@Slf4j
public class StudentExamNoticeScheduler {
    private static final int DEFAULT_ADVANCE_DAYS = 7;
    @Resource private TenantFrameworkService tenantFrameworkService;
    @Resource private MaintenanceModeApi maintenanceModeApi;
    @Resource private ServiceRelationMapper relationMapper;
    @Resource private StudentContactNotifyPublisher notifyPublisher;
    @Resource private WebSocketMessageSender webSocketSender;

    @Scheduled(fixedDelay = 60_000L)
    public void scanAllTenants() {
        if (maintenanceModeApi.isEnabled()) return;
        for (Long tenantId : tenantFrameworkService.getTenantIds()) {
            TenantUtils.execute(tenantId, () -> {
                try { scan(tenantId); }
                catch (RuntimeException ex) { log.error("[scan][tenantId({}) exam reminder failed]", tenantId, ex); }
            });
        }
    }

    void scan(Long tenantId) {
        LocalDate today = LocalDate.now();
        for (ServiceRelationDO relation : relationMapper.selectExamNoticeCandidates(tenantId)) {
            if (relation.getExamDate() == null || relation.getExamDate().isBefore(today)) continue;
            if (relation.getExamDate().isAfter(today.plusDays(DEFAULT_ADVANCE_DAYS))) continue;
            LocalDateTime now = LocalDateTime.now();
            if (relationMapper.markExamNoticeSent(relation.getId(), relation.getExamDate(), now, relation.getVersion()) != 1) continue;
            String key = "student-exam-notice:" + relation.getId() + ":" + relation.getExamDateVersion() + ":" + relation.getExamDate();
            notifyPublisher.publish(NOTIFY_EXAM_NOTICE, relation.getId(), key, 0L, now,
                    Map.of("plannerUserId", relation.getOwnerUserId(), "examDate", relation.getExamDate().toString(), "advanceDays", DEFAULT_ADVANCE_DAYS));
            try {
                webSocketSender.sendObject(UserTypeEnum.ADMIN.getValue(), relation.getOwnerUserId(), "ZSJOS_STUDENT_EXAM_NOTICE",
                        Map.of("serviceRelationId", relation.getId(), "examDate", relation.getExamDate().toString()));
            } catch (RuntimeException ex) {
                log.warn("[scan][relationId({}) websocket reminder failed]", relation.getId(), ex);
            }
        }
    }
}
