package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.service.delivery.DeliveryPositioningSource;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountProfileVO.DiagnosisTodo;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class MediaAccountDiagnosisReminderService {
    @Resource private BusinessTaskMapper tasks;
    @Resource private MediaAccountMapper accounts;
    @Resource private MediaAccountObjectPermissionProvider objects;
    @Resource private BusinessEventMapper events;
    @Resource private MediaWorkflowEventService eventService;

    public List<DiagnosisTodo> pending(Long userId) {
        return project(tasks.selectMyPendingByBusiness(userId, "media_account_diagnosis", null), userId);
    }

    private List<DiagnosisTodo> project(List<BusinessTaskDO> pending, Long userId) {
        var result = new ArrayList<DiagnosisTodo>();
        // Multiple templates share one account; validate its current ownership and positioning once per read.
        Map<Long, Optional<MediaAccountDO>> readable = new HashMap<>();
        for (var task : pending) {
            if (!"media_account_diagnosis".equals(task.getBizType()) || task.getDueAt() == null) continue;
            var account = readable.computeIfAbsent(task.getBizId(), id -> eligibleAccount(id, userId)).orElse(null);
            if (account == null) continue;
            var payload = DeliveryPositioningSource.parse(task.getPayload());
            var ctx = MediaAccountDiagnosisScheduler.context(account);
            if (payload.get("roundKey") == null || !Objects.equals(String.valueOf(payload.get("roundKey")), String.valueOf(ctx.get("roundKey")))) continue;
            result.add(new DiagnosisTodo(task.getId(),account.getId(),account.getStudentPersonId(),account.getNickname(),
                    task.getTitleSnapshot(),String.valueOf(payload.get("templateType")),((Number)payload.get("cycle")).intValue(),task.getDueAt(),payload));
        }
        result.sort(Comparator.comparing(DiagnosisTodo::dueAt).thenComparing(DiagnosisTodo::taskId));
        return result;
    }

    private Optional<MediaAccountDO> eligibleAccount(Long accountId, Long userId) {
        var account = accounts.selectById(accountId);
        if (account == null || !Objects.equals(account.getDirectorUserId(), userId)
                || !objects.hasPermission(accountId, "edit", userId)) return Optional.empty();
        return Optional.of(account);
    }
    public List<DiagnosisTodo> reminders(Long userId) {
        // Start daily reminders 24 hours before the deadline; completed tasks are excluded by pending().
        var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        return pending(userId).stream().filter(t -> !t.dueAt().minusDays(1).isAfter(now))
                .filter(t -> events.selectByIdempotencyKey(key(t.taskId(),userId)) == null).toList();
    }
    @ZsjosPermission(bizType="media-account",bizId="#accountId",action="edit")
    public List<DiagnosisTodo> accountTasks(Long accountId,Long userId) {
        return project(tasks.selectMyPendingByBusiness(userId, "media_account_diagnosis", accountId), userId);
    }
    private String key(Long taskId,Long userId) { return "diagnosis-seen:"+taskId+":"+userId+":"+LocalDate.now(ZoneId.of("Asia/Shanghai")); }
    @Transactional(rollbackFor=Exception.class)
    public void acknowledge(Long userId,List<Long> ids) {
        var allowed = pending(userId).stream().map(DiagnosisTodo::taskId).collect(java.util.stream.Collectors.toSet());
        for (Long id : ids) if (!allowed.contains(id)) throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED);
        for (Long id : ids) eventService.transition("media_account_diagnosis",id,userId,null,"REMINDER_SEEN",null,key(id,userId));
    }
}
