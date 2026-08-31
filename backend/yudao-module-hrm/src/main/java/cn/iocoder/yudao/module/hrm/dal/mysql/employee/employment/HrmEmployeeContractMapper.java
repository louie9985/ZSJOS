package cn.iocoder.yudao.module.hrm.dal.mysql.employee.employment;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.MPJLambdaWrapperX;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.employment.HrmEmployeeContractDO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.yudao.module.hrm.enums.employee.employment.HrmEmployeeContractStatusEnum;
import cn.iocoder.yudao.module.hrm.enums.employee.info.HrmEmployeeEntryStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface HrmEmployeeContractMapper extends BaseMapperX<HrmEmployeeContractDO> {

    default List<Map<String, Object>> selectReminderCandidates(java.util.Collection<Integer> entryStatuses,
                                                                Integer contractStatus,
                                                                java.time.LocalDate targetDate) {
        return selectMaps(new MPJLambdaWrapperX<HrmEmployeeContractDO>()
                .selectAs(HrmEmployeeContractDO::getId, "contractId")
                .selectAs(HrmEmployeeContractDO::getEmployeeId, "employeeId")
                .selectAs(HrmEmployeeDO::getName, "employeeName")
                .selectAs(HrmEmployeeDO::getDeptId, "deptId")
                .innerJoin(HrmEmployeeDO.class, HrmEmployeeDO::getId, HrmEmployeeContractDO::getEmployeeId)
                .eq(HrmEmployeeContractDO::getExpireRemind, true)
                .eq(HrmEmployeeContractDO::getStatus, contractStatus)
                .in(HrmEmployeeDO::getEntryStatus, entryStatuses)
                .isNotNull(HrmEmployeeContractDO::getEndTime)
                .apply("DATE(end_time) = {0}", targetDate)
                .notExists("SELECT 1 FROM hrm_employee_contract newer WHERE newer.employee_id = hrm_employee_contract.employee_id AND newer.deleted = 0 AND newer.end_time > hrm_employee_contract.end_time")
                .orderByAsc(HrmEmployeeDO::getName).orderByAsc(HrmEmployeeContractDO::getId));
    }

    default List<HrmEmployeeContractDO> selectListByEmployeeId(Long employeeId) {
        return selectList(new LambdaQueryWrapperX<HrmEmployeeContractDO>()
                .eq(HrmEmployeeContractDO::getEmployeeId, employeeId)
                .orderByAsc(HrmEmployeeContractDO::getSort)
                .orderByDesc(HrmEmployeeContractDO::getId));
    }

    default Long selectCountByEndTimeBetween(LocalDateTime[] endTimes) {
        MPJLambdaWrapperX<HrmEmployeeContractDO> query = new MPJLambdaWrapperX<>();
        query.innerJoin(HrmEmployeeDO.class, HrmEmployeeDO::getId,
                HrmEmployeeContractDO::getEmployeeId);
        query.eq(HrmEmployeeContractDO::getExpireRemind, true)
                .eq(HrmEmployeeContractDO::getStatus,
                        HrmEmployeeContractStatusEnum.IN_PROGRESS.getStatus())
                .in(HrmEmployeeDO::getEntryStatus, HrmEmployeeEntryStatusEnum.ACTIVE_STATUSES)
                .betweenIfPresent(HrmEmployeeContractDO::getEndTime, endTimes);
        query.selectFunc("COUNT(DISTINCT %s)",
                arg -> arg.accept(HrmEmployeeContractDO::getEmployeeId), "count");
        List<Map<String, Object>> result = selectMaps(query);
        return MapUtil.getLong(CollUtil.getFirst(result), "count");
    }

    @SuppressWarnings("UnusedReturnValue")
    default int updateTermById(Long id, Integer term) {
        return update(new LambdaUpdateWrapper<HrmEmployeeContractDO>()
                .eq(HrmEmployeeContractDO::getId, id)
                .set(HrmEmployeeContractDO::getTerm, term));
    }

}
