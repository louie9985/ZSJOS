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
    default SalesOrderDO selectByIdempotencyKey(String key) {
        return selectOne(SalesOrderDO::getSubmissionIdempotencyKey, key);
    }
    default SalesOrderDO selectBySupersedesOrderId(Long orderId) {
        return selectOne(SalesOrderDO::getSupersedesOrderId, orderId);
    }
    default PageResult<SalesOrderDO> selectMyPage(Long userId, SalesOrderMyPageReqVO reqVO) {
        LambdaQueryWrapperX<SalesOrderDO> query = new LambdaQueryWrapperX<SalesOrderDO>()
                .eq(SalesOrderDO::getSubmitterUserId, userId)
                .eqIfPresent(SalesOrderDO::getStatus, reqVO.getStatus());
        if (isNotBlank(reqVO.getKeyword())) {
            String keyword = reqVO.getKeyword().trim();
            query.and(wrapper -> wrapper.like(SalesOrderDO::getOrderNo, keyword)
                    .or().like(SalesOrderDO::getStudentName, keyword)
                    .or().like(SalesOrderDO::getStudentMobile, keyword));
        }
        query.orderByDesc(SalesOrderDO::getSubmittedAt).orderByDesc(SalesOrderDO::getId);
        return selectPage(reqVO, query);
    }
    default long selectMyCount(Long userId, String status) {
        return selectCount(new LambdaQueryWrapperX<SalesOrderDO>()
                .eq(SalesOrderDO::getSubmitterUserId, userId)
                .eqIfPresent(SalesOrderDO::getStatus, status));
    }
    @Select("SELECT * FROM zsjos_order WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = b'0' FOR UPDATE")
    SalesOrderDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
    default List<SalesOrderDO> selectEffectiveBySubmitterIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<SalesOrderDO>()
                .in(SalesOrderDO::getSubmitterUserId, userIds).eq(SalesOrderDO::getStatus, "effective"));
    }
}
