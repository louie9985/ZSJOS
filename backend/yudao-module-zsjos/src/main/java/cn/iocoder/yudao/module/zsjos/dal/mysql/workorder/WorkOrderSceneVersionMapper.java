package cn.iocoder.yudao.module.zsjos.dal.mysql.workorder;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder.WorkOrderSceneVersionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WorkOrderSceneVersionMapper extends BaseMapperX<WorkOrderSceneVersionDO> {
    default WorkOrderSceneVersionDO selectLatestBySceneId(Long sceneId) {
        return selectOne(new LambdaQueryWrapperX<WorkOrderSceneVersionDO>().eq(WorkOrderSceneVersionDO::getSceneId, sceneId)
                .orderByDesc(WorkOrderSceneVersionDO::getVersionNo).last("LIMIT 1"));
    }
    default List<WorkOrderSceneVersionDO> selectListBySceneId(Long sceneId) {
        return selectList(new LambdaQueryWrapperX<WorkOrderSceneVersionDO>()
                .eq(WorkOrderSceneVersionDO::getSceneId, sceneId)
                .orderByDesc(WorkOrderSceneVersionDO::getVersionNo));
    }
}
