package cn.iocoder.yudao.module.hrm.service.employee.api;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.hrm.api.employee.dto.HrmEmployeeRespDTO;
import cn.iocoder.yudao.module.hrm.api.employee.event.HrmEmployeeLifecycleEvent;
import cn.iocoder.yudao.module.hrm.api.employee.event.HrmEmployeeLifecycleEventType;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.info.HrmEmployeeMapper;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class HrmEmployeeLifecycleEventPublisher {

    @Resource
    private ApplicationEventPublisher eventPublisher;
    @Resource
    private HrmEmployeeMapper employeeMapper;

    public void publish(HrmEmployeeLifecycleEventType type, Long sourceId,
                        HrmEmployeeDO before, Long employeeId) {
        HrmEmployeeRespDTO beforeDTO = before == null ? null : snapshot(before);
        HrmEmployeeRespDTO afterDTO = snapshot(employeeMapper.selectById(employeeId));
        Long tenantId = TenantContextHolder.getTenantId();
        String eventKey = String.format("hrm:%s:%s:%s", type.name().toLowerCase(), sourceId, employeeId);
        eventPublisher.publishEvent(new HrmEmployeeLifecycleEvent(
                this, tenantId, eventKey, type, sourceId, beforeDTO, afterDTO));
    }

    private HrmEmployeeRespDTO snapshot(HrmEmployeeDO employee) {
        if (employee == null) {
            return null;
        }
        HrmEmployeeRespDTO result = new HrmEmployeeRespDTO();
        result.setId(employee.getId());
        result.setUserId(employee.getUserId());
        result.setName(employee.getName());
        result.setDeptId(employee.getDeptId());
        result.setLeaderEmployeeId(employee.getLeaderEmployeeId());
        if (employee.getLeaderEmployeeId() != null) {
            HrmEmployeeDO leader = employeeMapper.selectById(employee.getLeaderEmployeeId());
            result.setLeaderUserId(leader == null ? null : leader.getUserId());
        }
        result.setEntryStatus(employee.getEntryStatus());
        result.setStatus(employee.getStatus());
        result.setEntryTime(employee.getEntryTime());
        result.setLeaveTime(employee.getLeaveTime());
        return result;
    }

}
