package cn.iocoder.yudao.module.zsjos.dal.mysql.order;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderSupervisorConfirmationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderSupervisorPageReqVO;

@Mapper
public interface SalesOrderSupervisorConfirmationMapper extends BaseMapperX<SalesOrderSupervisorConfirmationDO> {
    default SalesOrderSupervisorConfirmationDO selectByRoundAndTaskKey(Long roundId, String taskKey) {
        return selectOne(new LambdaQueryWrapperX<SalesOrderSupervisorConfirmationDO>()
                .eq(SalesOrderSupervisorConfirmationDO::getApprovalRoundId, roundId)
                .eq(SalesOrderSupervisorConfirmationDO::getTaskDefinitionKey, taskKey));
    }

    default List<SalesOrderSupervisorConfirmationDO> selectByRoundId(Long roundId) {
        return selectList(SalesOrderSupervisorConfirmationDO::getApprovalRoundId, roundId);
    }

    default List<SalesOrderSupervisorConfirmationDO> selectBySupervisor(Long userId, String status) {
        return selectList(new LambdaQueryWrapperX<SalesOrderSupervisorConfirmationDO>()
                .eq(SalesOrderSupervisorConfirmationDO::getSupervisorUserId, userId)
                .eqIfPresent(SalesOrderSupervisorConfirmationDO::getStatus, status)
                .orderByDesc(SalesOrderSupervisorConfirmationDO::getRequestedAt));
    }

    default PageResult<SalesOrderSupervisorConfirmationDO> selectPageBySupervisor(
            Long userId, SalesOrderSupervisorPageReqVO reqVO, List<Long> orderIds) {
        LambdaQueryWrapperX<SalesOrderSupervisorConfirmationDO> query = new LambdaQueryWrapperX<>();
        query.eq(SalesOrderSupervisorConfirmationDO::getSupervisorUserId, userId)
                .eq(Boolean.FALSE.equals(reqVO.getHandled()), SalesOrderSupervisorConfirmationDO::getStatus, "pending")
                .ne(Boolean.TRUE.equals(reqVO.getHandled()), SalesOrderSupervisorConfirmationDO::getStatus, "pending");
        if (orderIds != null) {
            if (orderIds.isEmpty()) query.eq(SalesOrderSupervisorConfirmationDO::getOrderId, -1L);
            else query.in(SalesOrderSupervisorConfirmationDO::getOrderId, orderIds);
        }
        query.orderByDesc(SalesOrderSupervisorConfirmationDO::getRequestedAt);
        return selectPage(reqVO, query);
    }

    @Select("SELECT * FROM zsjos_order_supervisor_confirmation WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    SalesOrderSupervisorConfirmationDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
