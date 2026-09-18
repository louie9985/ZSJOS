package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
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
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryPlanMapper plans;

    public List<DiagnosisTodo> pending(Long userId) {
        var result = new ArrayList<DiagnosisTodo>();
        for (var task : tasks.selectMyPending(userId)) {
            if (!"media_account_diagnosis".equals(task.getBizType()) || task.getDueAt() == null) continue;
            var account = accounts.selectById(task.getBizId());
            if (account == null || !Objects.equals(account.getDirectorUserId(), userId) || !objects.hasPermission(account.getId(),"edit",userId)) continue;
            var payload = DeliveryPositioningSource.parse(task.getPayload());
            var ctx = MediaAccountDiagnosisScheduler.context(account);
            var latest = plans.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryPlanDO>()
                    .eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryPlanDO::getAccountId, account.getId())
                    .orderByDesc(cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryPlanDO::getId).last("LIMIT 1"));
            if (latest != null && "REPOSITIONING".equals(latest.getStatus())) continue;
            if (payload.get("roundKey") == null || !Objects.equals(String.valueOf(payload.get("roundKey")), String.valueOf(ctx.get("roundKey")))) continue;
            result.add(new DiagnosisTodo(task.getId(),account.getId(),account.getStudentPersonId(),account.getNickname(),
                    task.getTitleSnapshot(),String.valueOf(payload.get("templateType")),((Number)payload.get("cycle")).intValue(),task.getDueAt(),payload));
        }
        result.sort(Comparator.comparing(DiagnosisTodo::dueAt).thenComparing(DiagnosisTodo::taskId));
        return result;
    }
    public List<DiagnosisTodo> reminders(Long userId) {
        // Start daily reminders 24 hours before the deadline; completed tasks are excluded by pending().
        var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        return pending(userId).stream().filter(t -> !t.dueAt().minusDays(1).isAfter(now))
                .filter(t -> events.selectByIdempotencyKey(key(t.taskId(),userId)) == null).toList();
    }
    @ZsjosPermission(bizType="media-account",bizId="#accountId",action="edit")
    public List<DiagnosisTodo> accountTasks(Long accountId,Long userId) {
        return pending(userId).stream().filter(t -> t.accountId().equals(accountId)).toList();
    }
    private String key(Long taskId,Long userId) { return "diagnosis-seen:"+taskId+":"+userId+":"+LocalDate.now(ZoneId.of("Asia/Shanghai")); }
    @Transactional(rollbackFor=Exception.class)
    public void acknowledge(Long userId,List<Long> ids) {
        var allowed = pending(userId).stream().map(DiagnosisTodo::taskId).collect(java.util.stream.Collectors.toSet());
        for (Long id : ids) if (!allowed.contains(id)) throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED);
        for (Long id : ids) eventService.transition("media_account_diagnosis",id,userId,null,"REMINDER_SEEN",null,key(id,userId));
    }
}
