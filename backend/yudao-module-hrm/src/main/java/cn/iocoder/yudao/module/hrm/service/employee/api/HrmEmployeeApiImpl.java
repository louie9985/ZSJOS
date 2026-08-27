package cn.iocoder.yudao.module.hrm.service.employee.api;

import cn.iocoder.yudao.module.hrm.api.employee.HrmEmployeeApi;
import cn.iocoder.yudao.module.hrm.api.employee.dto.HrmEmployeeRespDTO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.employee.HrmEmployeeListReqVO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.yudao.module.hrm.service.employee.info.HrmEmployeeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HrmEmployeeApiImpl implements HrmEmployeeApi {

    @Resource
    private HrmEmployeeService employeeService;

    @Override
    public HrmEmployeeRespDTO getEmployee(Long employeeId) {
        return convert(employeeService.getEmployee(employeeId));
    }

    @Override
    public HrmEmployeeRespDTO getEmployeeByUserId(Long userId) {
        return convert(employeeService.getEmployeeByUserId(userId));
    }

    @Override
    public List<HrmEmployeeRespDTO> getEmployeeList(Collection<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, HrmEmployeeDO> employeeMap = employeeService.getEmployeeListByIds(employeeIds).stream()
                .collect(Collectors.toMap(HrmEmployeeDO::getId, Function.identity()));
        return employeeIds.stream().map(employeeMap::get).filter(employee -> employee != null)
                .map(this::convert).toList();
    }

    @Override
    public List<HrmEmployeeRespDTO> getEmployeeList() {
        return employeeService.getEmployeeList(new HrmEmployeeListReqVO()).stream()
                .map(this::convert).toList();
    }

    private HrmEmployeeRespDTO convert(HrmEmployeeDO employee) {
        if (employee == null) {
            return null;
        }
        HrmEmployeeDO leader = employee.getLeaderEmployeeId() == null
                ? null : employeeService.getEmployee(employee.getLeaderEmployeeId());
        HrmEmployeeRespDTO result = new HrmEmployeeRespDTO();
        result.setId(employee.getId());
        result.setUserId(employee.getUserId());
        result.setName(employee.getName());
        result.setDeptId(employee.getDeptId());
        result.setLeaderEmployeeId(employee.getLeaderEmployeeId());
        result.setLeaderUserId(leader == null ? null : leader.getUserId());
        result.setEntryStatus(employee.getEntryStatus());
        result.setStatus(employee.getStatus());
        result.setEntryTime(employee.getEntryTime());
        result.setLeaveTime(employee.getLeaveTime());
        return result;
    }

}
