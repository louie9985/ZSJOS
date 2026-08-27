package cn.iocoder.yudao.module.eam.dal.mysql.employee;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.employee.EamEmployeeAssetTaskItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EamEmployeeAssetTaskItemMapper extends BaseMapperX<EamEmployeeAssetTaskItemDO> {
    default List<EamEmployeeAssetTaskItemDO> selectListByTaskId(Long taskId) {
        return selectList(new LambdaQueryWrapperX<EamEmployeeAssetTaskItemDO>()
                .eq(EamEmployeeAssetTaskItemDO::getTaskId, taskId));
    }

    default List<EamEmployeeAssetTaskItemDO> selectListByHoldingId(Long holdingId) {
        return selectList(new LambdaQueryWrapperX<EamEmployeeAssetTaskItemDO>()
                .eq(EamEmployeeAssetTaskItemDO::getHoldingId, holdingId));
    }
}
