package cn.iocoder.yudao.module.zsjos.dal.mysql.workorder;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder.WorkOrderAttachmentDO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WorkOrderAttachmentMapper extends BaseMapperX<WorkOrderAttachmentDO> {
    default List<WorkOrderAttachmentDO> selectListByOrderIdAndPhase(Long workOrderId, String phase) {
        return selectList(new LambdaQueryWrapperX<WorkOrderAttachmentDO>()
                .eq(WorkOrderAttachmentDO::getWorkOrderId, workOrderId)
                .eq(WorkOrderAttachmentDO::getPhase, phase)
                .orderByAsc(WorkOrderAttachmentDO::getSort, WorkOrderAttachmentDO::getId));
    }
}
