package cn.iocoder.yudao.module.hrm.service.employee.info;

import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.employee.HrmEmployeeSaveReqVO;
import java.time.LocalDateTime;

/** Only lifecycle fields belong in a plan or audit snapshot, not personal/contact information. */
public record HrmEmployeeLifecycleValues(Integer type, Integer status, Integer entryStatus,
        Integer probation, LocalDateTime entryTime, LocalDateTime regularTime, LocalDateTime leaveTime,
        Long deptId, Long leaderEmployeeId, String postName, String postLevel, String workAddress,
        String typeLabel, String statusLabel, String entryStatusLabel) {
    public static HrmEmployeeLifecycleValues of(HrmEmployeeDO e) {
        return new HrmEmployeeLifecycleValues(e.getType(), e.getStatus(), e.getEntryStatus(), e.getProbation(),
                e.getEntryTime(), e.getRegularTime(), e.getLeaveTime(), e.getDeptId(), e.getLeaderEmployeeId(),
                e.getPostName(), e.getPostLevel(), e.getWorkAddress(), e.getTypeLabel(), e.getStatusLabel(), e.getEntryStatusLabel());
    }
    public void apply(HrmEmployeeSaveReqVO r) {
        r.setType(type).setStatus(status).setEntryStatus(entryStatus).setProbation(probation)
                .setEntryTime(entryTime).setRegularTime(regularTime).setLeaveTime(leaveTime)
                .setDeptId(deptId).setLeaderEmployeeId(leaderEmployeeId).setPostName(postName)
                .setPostLevel(postLevel).setWorkAddress(workAddress);
    }
}
