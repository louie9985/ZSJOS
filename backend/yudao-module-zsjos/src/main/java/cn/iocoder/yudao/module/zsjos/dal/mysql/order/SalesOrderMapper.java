package cn.iocoder.yudao.module.zsjos.dal.mysql.order;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderMyPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderTeamPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.FinanceOrderExportReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterQuery;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.ORDER_TYPE_FIRST_PURCHASE;

import static cn.hutool.core.util.StrUtil.isNotBlank;

@Mapper
public interface SalesOrderMapper extends BaseMapperX<SalesOrderDO> {
    default SalesOrderDO selectLatestFirstPurchaseByLeadId(Long leadId) {
        return selectOne(new LambdaQueryWrapperX<SalesOrderDO>().eq(SalesOrderDO::getLeadId, leadId)
                .eq(SalesOrderDO::getOrderType, ORDER_TYPE_FIRST_PURCHASE)
                .orderByDesc(SalesOrderDO::getSubmittedAt).orderByDesc(SalesOrderDO::getId)
                .last("LIMIT 1"));
    }

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
        query.orderByDesc(SalesOrderDO::getUpdateTime).orderByDesc(SalesOrderDO::getId);
        return selectPage(reqVO, query);
    }
    default PageResult<SalesOrderDO> selectMyPage(Long userId, SalesOrderMyPageReqVO reqVO) {
        return selectMyPage(userId, reqVO, null);
    }
    default PageResult<SalesOrderDO> selectTeamPage(Collection<Long> userIds, SalesOrderTeamPageReqVO reqVO,
                                                     List<Long> matchedOrderIds) {
        if (userIds == null || userIds.isEmpty()) return PageResult.empty();
        LambdaQueryWrapperX<SalesOrderDO> query = new LambdaQueryWrapperX<SalesOrderDO>()
                .in(SalesOrderDO::getSubmitterUserId, userIds)
                .eqIfPresent(SalesOrderDO::getStatus, reqVO.getStatus());
        if (isNotBlank(reqVO.getKeyword())) {
            String keyword = reqVO.getKeyword().trim();
            query.and(wrapper -> wrapper.like(SalesOrderDO::getOrderNo, keyword)
                    .or().like(SalesOrderDO::getStudentName, keyword)
                    .or().like(SalesOrderDO::getStudentMobile, keyword));
        }
        if (matchedOrderIds != null) {
            if (matchedOrderIds.isEmpty()) query.eq(SalesOrderDO::getId, -1L);
            else query.in(SalesOrderDO::getId, matchedOrderIds);
        }
        query.orderByDesc(SalesOrderDO::getUpdateTime).orderByDesc(SalesOrderDO::getId);
        return selectPage(reqVO, query);
    }
    default List<SalesOrderDO> selectTeamCursor(Collection<Long> userIds, String status, String keyword,
                                                 List<Long> matchedOrderIds, LocalDateTime cursorTime,
                                                 Long cursorId, int limit) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        LambdaQueryWrapperX<SalesOrderDO> query = new LambdaQueryWrapperX<SalesOrderDO>()
                .in(SalesOrderDO::getSubmitterUserId, userIds).eqIfPresent(SalesOrderDO::getStatus, status);
        if (isNotBlank(keyword)) {
            String value = keyword.trim();
            query.and(wrapper -> wrapper.like(SalesOrderDO::getOrderNo, value)
                    .or().like(SalesOrderDO::getStudentName, value)
                    .or().like(SalesOrderDO::getStudentMobile, value));
        }
        if (matchedOrderIds != null) {
            if (matchedOrderIds.isEmpty()) query.eq(SalesOrderDO::getId, -1L);
            else query.in(SalesOrderDO::getId, matchedOrderIds);
        }
        if (cursorTime != null && cursorId != null) {
            query.and(wrapper -> wrapper.lt(SalesOrderDO::getUpdateTime, cursorTime)
                    .or(nested -> nested.eq(SalesOrderDO::getUpdateTime, cursorTime)
                            .lt(SalesOrderDO::getId, cursorId)));
        }
        return selectList(query.orderByDesc(SalesOrderDO::getUpdateTime).orderByDesc(SalesOrderDO::getId)
                .last("LIMIT " + limit));
    }
    default long selectTeamCount(Collection<Long> userIds, String status) {
        if (userIds == null || userIds.isEmpty()) return 0;
        return selectCount(new LambdaQueryWrapperX<SalesOrderDO>()
                .in(SalesOrderDO::getSubmitterUserId, userIds).eqIfPresent(SalesOrderDO::getStatus, status));
    }
    default List<SalesOrderDO> selectByLeadId(Long leadId) {
        return selectList(new LambdaQueryWrapperX<SalesOrderDO>().eq(SalesOrderDO::getLeadId, leadId)
                .orderByDesc(SalesOrderDO::getSubmittedAt).orderByDesc(SalesOrderDO::getId));
    }
    default List<SalesOrderDO> selectMyCursor(Long userId, String status, String keyword, List<Long> matchedOrderIds,
                                               LocalDateTime cursorTime, Long cursorId, int limit) {
        LambdaQueryWrapperX<SalesOrderDO> query = new LambdaQueryWrapperX<SalesOrderDO>()
                .eq(SalesOrderDO::getSubmitterUserId, userId).eqIfPresent(SalesOrderDO::getStatus, status);
        if (isNotBlank(keyword)) {
            String value = keyword.trim();
            query.and(wrapper -> wrapper.like(SalesOrderDO::getOrderNo, value)
                    .or().like(SalesOrderDO::getStudentName, value)
                    .or().like(SalesOrderDO::getStudentMobile, value));
        }
        if (matchedOrderIds != null) {
            if (matchedOrderIds.isEmpty()) query.eq(SalesOrderDO::getId, -1L); else query.in(SalesOrderDO::getId, matchedOrderIds);
        }
        if (cursorTime != null && cursorId != null) {
            query.and(wrapper -> wrapper.lt(SalesOrderDO::getUpdateTime, cursorTime)
                    .or(nested -> nested.eq(SalesOrderDO::getUpdateTime, cursorTime).lt(SalesOrderDO::getId, cursorId)));
        }
        return selectList(query.orderByDesc(SalesOrderDO::getUpdateTime).orderByDesc(SalesOrderDO::getId)
                .last("LIMIT " + limit));
    }
    default PageResult<SalesOrderDO> selectFinanceExportPage(FinanceOrderExportReqVO reqVO,
                                                               AdvancedFilterQuery advancedFilter) {
        Page<SalesOrderDO> page = new Page<>(reqVO.getPageNo(), reqVO.getPageSize());
        List<SalesOrderDO> rows = selectFinanceExportRows(page, reqVO, advancedFilter,
                TenantContextHolder.getRequiredTenantId());
        return new PageResult<>(rows, page.getTotal());
    }

    @SelectProvider(type = SqlProvider.class, method = "financeExportSql")
    List<SalesOrderDO> selectFinanceExportRows(Page<?> page, @Param("request") FinanceOrderExportReqVO request,
                                                @Param("query") AdvancedFilterQuery query,
                                                @Param("tenantId") Long tenantId);

    final class SqlProvider {
        public static String financeExportSql() {
            return "<script>SELECT o.* FROM zsjos_order o WHERE o.deleted=b'0' "
                    + "AND o.tenant_id=#{tenantId} "
                    + "<if test='request.status != null and request.status != \"\"'>AND o.status=#{request.status} </if>"
                    + "<if test='request.keyword != null and request.keyword != \"\"'>"
                    + "AND (o.order_no LIKE CONCAT('%',#{request.keyword},'%') "
                    + "OR o.student_name LIKE CONCAT('%',#{request.keyword},'%') "
                    + "OR o.student_mobile LIKE CONCAT('%',#{request.keyword},'%')) </if>"
                    + "<if test='query != null'>AND (${query.whereSql}) </if>"
                    + "ORDER BY o.submitted_at DESC, o.id DESC</script>";
        }
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
