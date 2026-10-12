package cn.iocoder.yudao.module.hrm.service.employee.info;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.changerecord.HrmEmployeePlanCancelReqVO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.employee.HrmEmployeeSaveReqVO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.employment.HrmEmployeeChangeRecordDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.employment.HrmEmployeeChangeRecordMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.info.HrmEmployeeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
public class HrmEmployeePlanService {
    @Resource private HrmEmployeeMapper employees;
    @Resource private HrmEmployeeChangeRecordMapper records;
    @Resource private HrmEmployeeManualLifecycle lifecycle;

    @Transactional(rollbackFor = Exception.class)
    public void cancel(HrmEmployeePlanCancelReqVO command) {
        var employee = employees.selectByIdForUpdate(command.getEmployeeId());
        if (employee == null) throw exception(HrmEmployeeManualLifecycle.INVALID, "员工不存在");
        var request = BeanUtils.toBean(employee, HrmEmployeeSaveReqVO.class)
                .setChangeMode("CORRECTION").setChangeReason(command.getReason())
                .setOperationKey(command.getOperationKey()).setVersion(command.getEmployeeVersion())
                .setCommandRequestHash(JsonUtils.toJsonString(command));
        if (lifecycle.replay(request)) return;
        var plan = lifecycle.plan(command.getId(), employee.getId(), command.getVersion());
        var after = BeanUtils.toBean(employee, cn.iocoder.yudao.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO.class);
        if (plan.getType() == 4) after.setPlannedRegularTime(null);
        if (plan.getType() == 11) after.setPlannedLeaveTime(null);
        lifecycle.write(request, employee, after);
        records.updateById(new HrmEmployeeChangeRecordDO().setId(plan.getId()).setState("CANCELLED").setVersion(plan.getVersion() + 1));
    }
}
