package cn.iocoder.yudao.module.zsjos.dal.mysql.order;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderApprovalRoundDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SalesOrderApprovalRoundMapper extends BaseMapperX<SalesOrderApprovalRoundDO> {
    default SalesOrderApprovalRoundDO selectLatestByOrderId(Long orderId) {
        return selectOne(new LambdaQueryWrapperX<SalesOrderApprovalRoundDO>()
                .eq(SalesOrderApprovalRoundDO::getOrderId, orderId)
                .orderByDesc(SalesOrderApprovalRoundDO::getRoundNo).last("LIMIT 1"));
    }
    default SalesOrderApprovalRoundDO selectByProcessInstanceId(String processInstanceId) {
        return selectOne(SalesOrderApprovalRoundDO::getProcessInstanceId, processInstanceId);
    }
    default SalesOrderApprovalRoundDO selectByIdempotencyKey(String key) {
        return selectOne(SalesOrderApprovalRoundDO::getSubmissionIdempotencyKey, key);
    }
    @Select("SELECT * FROM zsjos_order_approval_round WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = b'0' FOR UPDATE")
    SalesOrderApprovalRoundDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("SELECT DISTINCT r.process_instance_id FROM zsjos_order o "
            + "JOIN zsjos_order_approval_round r ON r.order_id = o.id AND r.deleted = b'0' "
            + "WHERE o.tenant_id = #{tenantId} AND o.deleted = b'0' "
            + "AND (o.order_no LIKE CONCAT('%', #{keyword}, '%') "
            + "OR o.student_name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR o.student_mobile LIKE CONCAT('%', #{keyword}, '%'))")
    List<String> selectProcessInstanceIdsByKeyword(@Param("tenantId") Long tenantId,
                                                    @Param("keyword") String keyword);

    default List<String> selectProcessInstanceIdsByOrderIdsAndKeyword(Long tenantId, List<Long> orderIds, String keyword) {
        if (orderIds == null || orderIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<SalesOrderApprovalRoundDO>()
                .select(SalesOrderApprovalRoundDO::getProcessInstanceId)
                .eq(SalesOrderApprovalRoundDO::getTenantId, tenantId)
                .in(SalesOrderApprovalRoundDO::getOrderId, orderIds)
                .in(keyword != null, SalesOrderApprovalRoundDO::getOrderId,
                        keyword == null ? List.of() : selectOrderIdsByKeyword(tenantId, keyword))
                .isNotNull(SalesOrderApprovalRoundDO::getProcessInstanceId))
                .stream().map(SalesOrderApprovalRoundDO::getProcessInstanceId).distinct().toList();
    }

    @Select("SELECT id FROM zsjos_order WHERE tenant_id = #{tenantId} AND deleted = b'0' "
            + "AND (order_no LIKE CONCAT('%', #{keyword}, '%') "
            + "OR student_name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR student_mobile LIKE CONCAT('%', #{keyword}, '%'))")
    List<Long> selectOrderIdsByKeyword(@Param("tenantId") Long tenantId, @Param("keyword") String keyword);
}
