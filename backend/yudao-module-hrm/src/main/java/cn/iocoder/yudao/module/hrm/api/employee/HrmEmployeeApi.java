package cn.iocoder.yudao.module.hrm.api.employee;

import cn.iocoder.yudao.module.hrm.api.employee.dto.HrmEmployeeRespDTO;

import java.util.Collection;
import java.util.List;

/**
 * HRM employee public API.
 */
public interface HrmEmployeeApi {

    HrmEmployeeRespDTO getEmployee(Long employeeId);

    HrmEmployeeRespDTO getEmployeeByUserId(Long userId);

    List<HrmEmployeeRespDTO> getEmployeeList(Collection<Long> employeeIds);

    /**
     * 获取员工选择所需的最小投影；调用方不得持久化该结果作为员工副本。
     */
    List<HrmEmployeeRespDTO> getEmployeeList();

}
