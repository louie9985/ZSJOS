package cn.iocoder.yudao.module.eam.dal.mysql.transfer;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.controller.admin.transfer.vo.EamTransferPageReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.transfer.EamTransferDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface EamTransferMapper extends BaseMapperX<EamTransferDO> {

    default PageResult<EamTransferDO> selectPage(EamTransferPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<EamTransferDO>()
                .eqIfPresent(EamTransferDO::getType, reqVO.getType())
                .eqIfPresent(EamTransferDO::getAssetId, reqVO.getAssetId())
                .eqIfPresent(EamTransferDO::getStatus, reqVO.getStatus())
                .likeIfPresent(EamTransferDO::getNo, reqVO.getNo())
                .orderByDesc(EamTransferDO::getId));
    }

    default PageResult<EamTransferDO> selectPage(EamTransferPageReqVO reqVO, cn.iocoder.yudao.module.eam.service.common.EamDataScopeService.Scope scope) {
        var wrapper = new LambdaQueryWrapperX<EamTransferDO>()
                .eqIfPresent(EamTransferDO::getType, reqVO.getType())
                .eqIfPresent(EamTransferDO::getAssetId, reqVO.getAssetId())
                .eqIfPresent(EamTransferDO::getStatus, reqVO.getStatus())
                .likeIfPresent(EamTransferDO::getNo, reqVO.getNo());
        if (!scope.all()) {
            wrapper.and(w -> w.eq(scope.self(), EamTransferDO::getApplyUserId, scope.userId())
                    .or(scope.self(), x -> x.eq(EamTransferDO::getToEmployeeId, scope.userId()))
                    .or(scope.deptIds() != null && !scope.deptIds().isEmpty(), x -> x.in(EamTransferDO::getFromDeptId, scope.deptIds()).or().in(EamTransferDO::getToDeptId, scope.deptIds())));
        }
        return selectPage(reqVO, wrapper.orderByDesc(EamTransferDO::getId));
    }

    default EamTransferDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<EamTransferDO>()
                .eq(EamTransferDO::getId, id).last("FOR UPDATE"));
    }

    default List<EamTransferDO> selectListByApplicantOrReceiver(Long userId, Long employeeId) {
        return selectList(new LambdaQueryWrapperX<EamTransferDO>()
                .and(wrapper -> wrapper.eq(EamTransferDO::getApplyUserId, userId)
                        .or(employeeId != null, nested -> nested.eq(EamTransferDO::getToEmployeeId, employeeId)))
                .orderByDesc(EamTransferDO::getId));
    }

}
