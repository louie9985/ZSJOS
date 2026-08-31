package cn.iocoder.yudao.module.zsjos.dal.mysql.order;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderSupervisorConfirmationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;
import java.time.LocalDateTime;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderSupervisorPageReqVO;

@Mapper
public interface SalesOrderSupervisorConfirmationMapper extends BaseMapperX<SalesOrderSupervisorConfirmationDO> {
    default SalesOrderSupervisorConfirmationDO selectBySupervisorTaskId(String taskId) {
        return selectOne(SalesOrderSupervisorConfirmationDO::getSupervisorTaskId, taskId);
    }
    default SalesOrderSupervisorConfirmationDO selectLatestByOrderId(Long orderId) {
        return selectOne(new LambdaQueryWrapperX<SalesOrderSupervisorConfirmationDO>()
                .eq(SalesOrderSupervisorConfirmationDO::getOrderId, orderId)
                .orderByDesc(SalesOrderSupervisorConfirmationDO::getRequestedAt)
                .orderByDesc(SalesOrderSupervisorConfirmationDO::getId)
                .last("LIMIT 1"));
    }
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
                .orderByDesc(SalesOrderSupervisorConfirmationDO::getUpdateTime)
                .orderByDesc(SalesOrderSupervisorConfirmationDO::getId));
    }

    default PageResult<SalesOrderSupervisorConfirmationDO> selectPageBySupervisor(
            Long userId, SalesOrderSupervisorPageReqVO reqVO, List<Long> orderIds) {
        if (orderIds != null && orderIds.isEmpty()) return PageResult.empty();
        return new PageResult<>(selectSupervisorPageRows(reqVO, userId, orderIds),
                selectSupervisorCount(reqVO, userId, orderIds));
    }
    default List<SalesOrderSupervisorConfirmationDO> selectCursorBySupervisor(Long userId, Boolean handled,
                                                                                List<Long> orderIds, LocalDateTime cursorTime,
                                                                                Long cursorId, int limit) {
        if (orderIds != null && orderIds.isEmpty()) return List.of();
        return selectSupervisorCursorRows(userId, handled, orderIds, cursorTime, cursorId, limit);
    }

    @SelectProvider(type = SqlProvider.class, method = "supervisorPageSql")
    List<SalesOrderSupervisorConfirmationDO> selectSupervisorPageRows(SalesOrderSupervisorPageReqVO request,
                                                                      @Param("userId") Long userId,
                                                                      @Param("orderIds") List<Long> orderIds);

    @SelectProvider(type = SqlProvider.class, method = "supervisorCountSql")
    Long selectSupervisorCount(SalesOrderSupervisorPageReqVO request, @Param("userId") Long userId,
                               @Param("orderIds") List<Long> orderIds);

    @SelectProvider(type = SqlProvider.class, method = "supervisorCursorSql")
    List<SalesOrderSupervisorConfirmationDO> selectSupervisorCursorRows(@Param("userId") Long userId,
                                                                        @Param("handled") Boolean handled,
                                                                        @Param("orderIds") List<Long> orderIds,
                                                                        @Param("cursorTime") LocalDateTime cursorTime,
                                                                        @Param("cursorId") Long cursorId,
                                                                        @Param("limit") int limit);

    @Select("SELECT * FROM zsjos_order_supervisor_confirmation WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    SalesOrderSupervisorConfirmationDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    final class SqlProvider {
        public static String supervisorPageSql() {
            return "<script>" + supervisorSelectSql("request.handled")
                    + "ORDER BY c.update_time DESC, c.id DESC "
                    + "LIMIT #{request.pageSize} OFFSET #{request.pageSize} * (#{request.pageNo} - 1)</script>";
        }

        public static String supervisorCountSql() {
            return "<script>SELECT COUNT(*) " + supervisorFromWhereSql("request.handled") + "</script>";
        }

        public static String supervisorCursorSql() {
            return "<script>" + supervisorSelectSql("handled")
                    + "<if test='cursorTime != null and cursorId != null'>"
                    + "AND (c.update_time &lt; #{cursorTime} OR (c.update_time = #{cursorTime} AND c.id &lt; #{cursorId})) "
                    + "</if>"
                    + "ORDER BY c.update_time DESC, c.id DESC LIMIT #{limit}</script>";
        }

        private static String supervisorSelectSql(String handledPath) {
            return "SELECT c.* " + supervisorFromWhereSql(handledPath);
        }

        private static String supervisorFromWhereSql(String handledPath) {
            return "FROM zsjos_order_supervisor_confirmation c "
                    + "WHERE c.deleted = b'0' AND c.supervisor_user_id = #{userId} "
                    + "<if test='" + handledPath + " != null and " + handledPath + " == false'>"
                    + "AND c.status = 'pending' "
                    + "AND EXISTS (SELECT 1 FROM zsjos_order o "
                    + "JOIN zsjos_order_approval_round r ON r.id = c.approval_round_id AND r.deleted = b'0' "
                    + "WHERE o.id = c.order_id AND o.deleted = b'0' "
                    + "AND o.tenant_id = c.tenant_id AND r.tenant_id = c.tenant_id "
                    + "AND o.status = 'pending_approval' AND r.status = 'pending' "
                    + "AND o.current_approval_round_id = c.approval_round_id) "
                    + "</if>"
                    + "<if test='" + handledPath + " != null and " + handledPath + " == true'>AND c.status != 'pending' </if>"
                    + "<if test='orderIds != null'>AND c.order_id IN "
                    + "<foreach collection='orderIds' item='orderId' open='(' separator=',' close=')'>#{orderId}</foreach> "
                    + "</if>";
        }
    }
}
