package cn.iocoder.yudao.module.zsjos.dal.mysql.workorder;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder.WorkOrderSceneDO;
import org.apache.ibatis.annotations.Mapper;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
@Mapper public interface WorkOrderSceneMapper extends BaseMapperX<WorkOrderSceneDO> {
    default WorkOrderSceneDO selectByCode(String code) { return selectOne(new LambdaQueryWrapperX<WorkOrderSceneDO>().eq(WorkOrderSceneDO::getCode, code)); }
    default PageResult<WorkOrderSceneDO> selectPublishedPage(PageParam page) { return selectPage(page,
            new LambdaQueryWrapperX<WorkOrderSceneDO>().isNotNull(WorkOrderSceneDO::getPublishedVersionId)
                    .eq(WorkOrderSceneDO::getStatus, 1).orderByAsc(WorkOrderSceneDO::getSort).orderByAsc(WorkOrderSceneDO::getId)); }
    default java.util.List<WorkOrderSceneDO> selectPublishedList() { return selectList(
            new LambdaQueryWrapperX<WorkOrderSceneDO>().isNotNull(WorkOrderSceneDO::getPublishedVersionId)
                    .eq(WorkOrderSceneDO::getStatus, 1).orderByAsc(WorkOrderSceneDO::getSort).orderByAsc(WorkOrderSceneDO::getId)); }
}
