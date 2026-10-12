package cn.iocoder.yudao.module.hrm.service.employee.info;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.hrm.controller.admin.employeereminder.vo.HrmLifecycleReminderConfigVO;
import cn.iocoder.yudao.module.hrm.controller.admin.employeereminder.vo.HrmLifecycleReminderConfigVO.Rule;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.employment.HrmEmployeeChangeRecordDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.info.HrmEmployeeMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.employment.HrmEmployeeChangeRecordMapper;
import cn.iocoder.yudao.module.hrm.service.config.HrmConfigService;
import cn.iocoder.yudao.module.hrm.enums.config.HrmConfigTypeEnum;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
public class HrmLifecycleReminderService {
    public static final String SCENE = "hrm.employee.lifecycle_";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    @Resource private HrmConfigService config;
    @Resource private HrmEmployeeMapper employees;
    @Resource private HrmEmployeeChangeRecordMapper records;
    @Resource private DeptApi departments;
    @Resource private AdminUserApi users;
    @Resource private NotifyBusinessEventApi notifications;
    public record Item(String kind, Long employeeId, Long planId, LocalDateTime target, String name) { }
    public record Preview(List<Long> recipientUserIds, int pendingCount) { }

    public HrmLifecycleReminderConfigVO getConfig() {
        var values = config.getConfigValueList(HrmConfigTypeEnum.LIFECYCLE_REMINDER.getType());
        return values.isEmpty() ? new HrmLifecycleReminderConfigVO() : JsonUtils.parseObject(values.getFirst(), HrmLifecycleReminderConfigVO.class);
    }
    @Transactional(rollbackFor = Exception.class)
    public void save(HrmLifecycleReminderConfigVO next) {
        var old = getConfig();
        prepare(next.getRegular(), old.getRegular()); prepare(next.getLeave(), old.getLeave()); prepare(next.getChange(), old.getChange());
        config.replaceConfigValueList(HrmConfigTypeEnum.LIFECYCLE_REMINDER.getType(), List.of(JsonUtils.toJsonString(next)));
    }
    private void prepare(Rule r, Rule old) {
        departments.validateDeptList(r.getDeptIds()); departments.validateDeptList(r.getRecipientDeptIds());
        users.validateUserList(r.getRecipientUserIds());
        if (r.isEnabled() && (r.getDeptIds().isEmpty() || recipients(r).isEmpty())) throw exception(HrmEmployeeManualLifecycle.INVALID, "启用提醒必须配置员工范围及有效接收人");
        r.setEnabledSince(r.isEnabled() ? old.isEnabled() && old.getEnabledSince() != null ? old.getEnabledSince() : LocalDateTime.now(ZONE) : null);
    }
    private Set<Long> scope(Rule r) {
        Set<Long> ids = new HashSet<>(r.getDeptIds());
        if (r.isIncludeChildDepartments() && !ids.isEmpty()) departments.getChildDeptList(r.getDeptIds()).forEach(d -> ids.add(d.getId()));
        return ids;
    }
    public List<Long> recipients(Rule r) {
        Set<Long> ids = new HashSet<>(r.getRecipientUserIds());
        if (!r.getRecipientDeptIds().isEmpty()) users.getUserListByDeptIds(r.getRecipientDeptIds()).forEach(u -> ids.add(u.getId()));
        if (ids.isEmpty()) return List.of();
        return users.getUserList(ids).stream().filter(u -> Objects.equals(u.getStatus(), 0)).map(u -> u.getId()).sorted().toList();
    }
    public Map<String, Preview> preview(HrmLifecycleReminderConfigVO c) {
        return Map.of("regular", new Preview(recipients(c.getRegular()), items("regular", c.getRegular()).size()),
                "leave", new Preview(recipients(c.getLeave()), items("leave", c.getLeave()).size()),
                "change", new Preview(recipients(c.getChange()), items("change", c.getChange()).size()));
    }
    private List<Item> items(String kind, Rule r) {
        Set<Long> scope = scope(r);
        if (scope.isEmpty()) return List.of();
        List<HrmEmployeeDO> list = employees.selectList(new LambdaQueryWrapperX<HrmEmployeeDO>().in(HrmEmployeeDO::getDeptId, scope)
                .in(HrmEmployeeDO::getEntryStatus, List.of(1, 3)));
        List<Item> result = new ArrayList<>();
        for (var e : list) {
            if (kind.equals("regular") && Objects.equals(e.getStatus(), 2) && e.getPlannedRegularTime() != null)
                result.add(new Item(kind, e.getId(), null, e.getPlannedRegularTime(), e.getName()));
            if (kind.equals("leave") && e.getPlannedLeaveTime() != null)
                result.add(new Item(kind, e.getId(), null, e.getPlannedLeaveTime(), e.getName()));
            if (kind.equals("change")) for (var p : records.selectList(new LambdaQueryWrapperX<HrmEmployeeChangeRecordDO>()
                    .eq(HrmEmployeeChangeRecordDO::getEmployeeId, e.getId()).eq(HrmEmployeeChangeRecordDO::getState, "PENDING")
                    .notIn(HrmEmployeeChangeRecordDO::getType, List.of(4, 11)))) {
                if (p.getPlannedTime() != null && (p.getBeforeSnapshot() == null || p.getBeforeSnapshot().equals(HrmEmployeeManualLifecycle.snapshot(e))))
                    result.add(new Item(kind, e.getId(), p.getId(), p.getPlannedTime(), e.getName()));
            }
        }
        return result;
    }
    public String dispatch() {
        var c = getConfig(); int accepted = 0;
        LocalDateTime now = LocalDateTime.now(ZONE);
        Map<String, Rule> rules = Map.of("regular", c.getRegular(), "leave", c.getLeave(), "change", c.getChange());
        for (var entry : rules.entrySet()) {
            Rule rule = entry.getValue();
            if (!rule.isEnabled() || rule.getEnabledSince() == null || now.toLocalTime().isBefore(LocalTime.parse(rule.getTriggerTime()))) continue;
            for (Item item : items(entry.getKey(), rule)) {
                LocalDate date = item.target().toLocalDate();
                if (date.isBefore(rule.getEnabledSince().toLocalDate())) continue;
                String phase = date.equals(now.toLocalDate()) ? "due"
                        : rule.getAdvanceDays() > 0 && date.minusDays(rule.getAdvanceDays()).equals(now.toLocalDate()) ? "advance" : null;
                if (phase == null) continue;
                for (Long recipient : recipients(rule)) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("employeeId", item.employeeId()); payload.put("employeeName", item.name());
                    payload.put("planId", item.planId()); payload.put("target", item.target().toString());
                    payload.put("kind", item.kind()); payload.put("phase", phase); payload.put("recipientUserId", recipient);
                    String key = "hrm-lifecycle:" + item.kind() + ":" + item.employeeId() + ":" + item.planId() + ":" + item.target() + ":" + phase + ":" + recipient;
                    accepted += notifications.publishDurable(NotifyBusinessEvent.builder().sceneCode(SCENE + item.kind())
                            .sourceEventKey(key).bizType("hrm_employee").bizId(item.employeeId()).occurredAt(now).payload(payload)
                            .recipientMode("FIXED").fixedRecipients(List.of(
                                    cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO.admin(recipient))).build());
                }
            }
        }
        return "人事到期提醒已受理规则数：" + accepted + "；员工状态未修改";
    }
    public boolean isCurrent(NotifyBusinessEvent event) {
        var c = getConfig(); String kind = String.valueOf(event.getPayload().get("kind"));
        Rule r = switch (kind) { case "regular" -> c.getRegular(); case "leave" -> c.getLeave(); default -> c.getChange(); };
        Object userId = event.getPayload().get("recipientUserId");
        if (!r.isEnabled() || !(userId instanceof Number n) || !recipients(r).contains(n.longValue())) return false;
        return items(kind, r).stream().anyMatch(i -> Objects.equals(i.employeeId(), event.getBizId())
                && Objects.equals(i.target().toString(), event.getPayload().get("target"))
                && Objects.equals(String.valueOf(i.planId()), String.valueOf(event.getPayload().get("planId"))));
    }
}
