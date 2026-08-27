package cn.iocoder.yudao.module.eam.dal.mysql.employee;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.employee.EamEmployeeAssetTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EamEmployeeAssetTaskMapper extends BaseMapperX<EamEmployeeAssetTaskDO> {
    default EamEmployeeAssetTaskDO selectByEventKey(String eventKey) {
        return selectOne(new LambdaQueryWrapperX<EamEmployeeAssetTaskDO>()
                .and(wrapper -> wrapper.eq(EamEmployeeAssetTaskDO::getEventKey, eventKey)
                        .or().eq(EamEmployeeAssetTaskDO::getLatestEventKey, eventKey)));
    }
    default List<EamEmployeeAssetTaskDO> selectListByEmployeeId(Long employeeId) {
        return selectList(new LambdaQueryWrapperX<EamEmployeeAssetTaskDO>()
                .eq(EamEmployeeAssetTaskDO::getEmployeeId, employeeId).orderByDesc(EamEmployeeAssetTaskDO::getId));
    }
    default EamEmployeeAssetTaskDO selectOpenByEmployeeIdAndType(Long employeeId, Integer type) {
        return selectOne(new LambdaQueryWrapperX<EamEmployeeAssetTaskDO>()
                .eq(EamEmployeeAssetTaskDO::getEmployeeId, employeeId)
                .eq(EamEmployeeAssetTaskDO::getType, type)
                .in(EamEmployeeAssetTaskDO::getStatus, 0, 1, 2, 5)
                .orderByDesc(EamEmployeeAssetTaskDO::getId).last("LIMIT 1"));
    }
    default EamEmployeeAssetTaskDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<EamEmployeeAssetTaskDO>()
                .eq(EamEmployeeAssetTaskDO::getId, id).last("FOR UPDATE"));
    }
}
