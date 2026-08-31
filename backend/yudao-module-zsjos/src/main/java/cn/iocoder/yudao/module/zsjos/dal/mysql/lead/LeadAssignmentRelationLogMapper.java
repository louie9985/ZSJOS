package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentLogPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentRelationLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface LeadAssignmentRelationLogMapper extends BaseMapperX<LeadAssignmentRelationLogDO> {

    default PageResult<LeadAssignmentRelationLogDO> selectPage(LeadAssignmentLogPageReqVO reqVO) {
        return selectPage(reqVO, buildQuery(reqVO));
    }

    default List<LeadAssignmentRelationLogDO> selectList(LeadAssignmentLogPageReqVO reqVO) {
        return selectList(buildQuery(reqVO));
    }

    private static LambdaQueryWrapperX<LeadAssignmentRelationLogDO> buildQuery(
            LeadAssignmentLogPageReqVO reqVO) {
        return new LambdaQueryWrapperX<LeadAssignmentRelationLogDO>()
                .eq(LeadAssignmentRelationLogDO::getScene, reqVO.getScene())
                .eqIfPresent(LeadAssignmentRelationLogDO::getActionType, reqVO.getActionType())
                .orderByDesc(LeadAssignmentRelationLogDO::getId);
    }

    default Long selectCountByScene(String scene) {
        return selectCount(LeadAssignmentRelationLogDO::getScene, scene);
    }

}
