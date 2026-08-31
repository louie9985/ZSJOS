package cn.iocoder.yudao.module.zsjos.job.employeereminder;
import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.hrm.api.employeereminder.*;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.zsjos.service.employeereminder.EmployeeReminderConstants;
import cn.iocoder.yudao.module.zsjos.service.task.*;
import jakarta.annotation.Resource; import lombok.extern.slf4j.Slf4j; import org.springframework.stereotype.Component;
import java.time.*; import java.time.format.DateTimeFormatter; import java.util.*;
@Component @Slf4j public class EmployeeContractAnniversaryReminderJob implements JobHandler {
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai"); private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyy年MM月dd日");
    @Resource private HrmEmployeeReminderApi api; @Resource private BusinessTaskCommandService tasks; @Resource private NotifyBusinessEventApi notify;
    @Override @TenantJob public String execute(String param){ LocalDateTime now=LocalDateTime.now(ZONE); int count=process(api.getDueContractExpiryDispatch(now.toLocalDate(),now.toLocalTime()), true, now); count+=process(api.getDueEntryAnniversaryDispatch(now.toLocalDate(),now.toLocalTime()), false, now); return "员工合同/周年提醒：处理 "+count+" 条"; }
    private int process(HrmEmployeeReminderDispatch d, boolean contract, LocalDateTime now){ if(d.isEmpty()) return 0; int n=0; for(var e:d.employees()) for(Long recipient:d.recipientUserIds()){ String key="hrm-employee-reminder:"+(contract?"contract":"anniversary")+":"+d.targetDate()+":"+e.id()+":"+recipient; try { String summary=(e.deptName()==null?"":e.deptName()+" · ")+(contract?"合同到期 "+d.targetDate().format(DATE):"入职 "+e.anniversaryYears()+" 周年"); String taskType=contract?EmployeeReminderConstants.CONTRACT_TASK_TYPE:EmployeeReminderConstants.ANNIVERSARY_TASK_TYPE; String bizType=contract?EmployeeReminderConstants.CONTRACT_BIZ_TYPE:EmployeeReminderConstants.ANNIVERSARY_BIZ_TYPE; String action=contract?EmployeeReminderConstants.CONTRACT_ACTION:EmployeeReminderConstants.ANNIVERSARY_ACTION; tasks.create(new BusinessTaskCreateCommand(taskType,bizType,e.id(),recipient,(contract?"合同到期提醒：":"入职周年提醒：")+e.name(),summary,action,d.dueAt(),d.dueAt(),null,key)); Map<String,Object> p=new LinkedHashMap<>(); p.put("recipientUserId",recipient); p.put("employeeName",e.name()); p.put("departmentName",e.deptName()); p.put(contract?"contractEndDate":"anniversaryDate",d.targetDate().format(DATE)); if(!contract)p.put("anniversaryYears",e.anniversaryYears()); notify.publish(NotifyBusinessEvent.builder().tenantId(TenantContextHolder.getRequiredTenantId()).sceneCode(contract?"hrm.employee.contract_expiry":"hrm.employee.entry_anniversary").sourceEventKey(key).bizType(bizType).bizId(e.id()).occurredAt(now).payload(p).build()); n++; } catch(RuntimeException ex){ log.warn("员工提醒处理失败，员工={},接收人={}",e.id(),recipient); } } return n; }
}
