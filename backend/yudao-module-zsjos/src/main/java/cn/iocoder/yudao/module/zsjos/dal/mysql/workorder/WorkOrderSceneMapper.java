package cn.iocoder.yudao.module.zsjos.dal.mysql.workorder;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder.WorkOrderSceneDO;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface WorkOrderSceneMapper extends BaseMapperX<WorkOrderSceneDO> {
    default WorkOrderSceneDO selectByCode(String code) { return selectOne(new LambdaQueryWrapperX<WorkOrderSceneDO>().eq(WorkOrderSceneDO::getCode, code)); }
}
