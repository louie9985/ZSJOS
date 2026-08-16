package cn.iocoder.yudao.module.zsjos.dal.mysql.order;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderMyPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

import static cn.hutool.core.util.StrUtil.isNotBlank;

@Mapper
public interface SalesOrderMapper extends BaseMapperX<SalesOrderDO> {
    default SalesOrderDO selectActiveByLeadId(Long leadId, Collection<String> statuses) {
        return selectOne(new LambdaQueryWrapperX<SalesOrderDO>().eq(SalesOrderDO::getLeadId, leadId)
                .in(SalesOrderDO::getStatus, statuses).orderByDesc(SalesOrderDO::getId).last("LIMIT 1"));
    }
    default SalesOrderDO selectActiveRepurchaseByPersonId(Long personId, Collection<String> statuses) {
        return selectOne(new LambdaQueryWrapperX<SalesOrderDO>().eq(SalesOrderDO::getPersonId, personId)
                .eq(SalesOrderDO::getOrderType, "repurchase").in(SalesOrderDO::getStatus, statuses)
                .orderByDesc(SalesOrderDO::getId).last("LIMIT 1"));
    }
    default SalesOrderDO selectOtherActiveByLeadId(Long leadId, Long excludedOrderId,
                                                    Collection<String> statuses) {
        return selectOne(new LambdaQueryWrapperX<SalesOrderDO>().eq(SalesOrderDO::getLeadId, leadId)
                .ne(SalesOrderDO::getId, excludedOrderId).in(SalesOrderDO::getStatus, statuses)
                .orderByDesc(SalesOrderDO::getId).last("LIMIT 1"));
    }
    default SalesOrderDO selectOtherActiveByPersonId(Long personId, Long excludedOrderId,
                                                      Collection<String> statuses) {
        return selectOne(new LambdaQueryWrapperX<SalesOrderDO>().eq(SalesOrderDO::getPersonId, personId)
                .ne(SalesOrderDO::getId, excludedOrderId).in(SalesOrderDO::getStatus, statuses)
                .orderByDesc(SalesOrderDO::getId).last("LIMIT 1"));
    }
    default boolean hasEffectiveOrder(Long personId) {
        return selectCount(new LambdaQueryWrapperX<SalesOrderDO>().eq(SalesOrderDO::getPersonId, personId)
                .eq(SalesOrderDO::getStatus, "effective")) > 0;
    }
    default List<SalesOrderDO> selectByPersonId(Long personId) {
        return selectList(new LambdaQueryWrapperX<SalesOrderDO>().eq(SalesOrderDO::getPersonId, personId)
                .orderByDesc(SalesOrderDO::getSubmittedAt).orderByDesc(SalesOrderDO::getId));
    }
    default SalesOrderDO selectByIdempotencyKey(String key) {
        return selectOne(SalesOrderDO::getSubmissionIdempotencyKey, key);
    }
    default SalesOrderDO selectBySupersedesOrderId(Long orderId) {
        return selectOne(SalesOrderDO::getSupersedesOrderId, orderId);
    }
    default PageResult<SalesOrderDO> selectMyPage(Long userId, SalesOrderMyPageReqVO reqVO, List<Long> matchedOrderIds) {
        LambdaQueryWrapperX<SalesOrderDO> query = new LambdaQueryWrapperX<SalesOrderDO>()
                .eq(SalesOrderDO::getSubmitterUserId, userId)
                .eqIfPresent(SalesOrderDO::getStatus, reqVO.getStatus());
        if (isNotBlank(reqVO.getKeyword())) {
            String keyword = reqVO.getKeyword().trim();
            query.and(wrapper -> wrapper.like(SalesOrderDO::getOrderNo, keyword)
                    .or().like(SalesOrderDO::getStudentName, keyword)
                    .or().like(SalesOrderDO::getStudentMobile, keyword));
        }
        if (matchedOrderIds != null) {
            if (matchedOrderIds.isEmpty()) query.eq(SalesOrderDO::getId, -1L); else query.in(SalesOrderDO::getId, matchedOrderIds);
        }
        query.orderByDesc(SalesOrderDO::getSubmittedAt).orderByDesc(SalesOrderDO::getId);
        return selectPage(reqVO, query);
    }
    default PageResult<SalesOrderDO> selectMyPage(Long userId, SalesOrderMyPageReqVO reqVO) {
        return selectMyPage(userId, reqVO, null);
    }
    default long selectMyCount(Long userId, String status) {
        return selectCount(new LambdaQueryWrapperX<SalesOrderDO>()
                .eq(SalesOrderDO::getSubmitterUserId, userId)
                .eqIfPresent(SalesOrderDO::getStatus, status));
    }
    default List<Long> selectIdsByKeyword(String keyword) {
        if (!isNotBlank(keyword)) return null;
        String value = keyword.trim();
        return selectList(new LambdaQueryWrapperX<SalesOrderDO>()
                .select(SalesOrderDO::getId)
                .and(wrapper -> wrapper.like(SalesOrderDO::getOrderNo, value)
                        .or().like(SalesOrderDO::getStudentName, value)
                        .or().like(SalesOrderDO::getStudentMobile, value)))
                .stream().map(SalesOrderDO::getId).toList();
    }
    @Select("SELECT * FROM zsjos_order WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = b'0' FOR UPDATE")
    SalesOrderDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
    default List<SalesOrderDO> selectEffectiveBySubmitterIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<SalesOrderDO>()
                .in(SalesOrderDO::getSubmitterUserId, userIds).eq(SalesOrderDO::getStatus, "effective"));
    }
}
