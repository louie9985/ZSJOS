package cn.iocoder.yudao.module.zsjos.dal.mysql.order;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;

@Mapper
public interface SalesOrderMapper extends BaseMapperX<SalesOrderDO> {
    default SalesOrderDO selectActiveByLeadId(Long leadId, Collection<String> statuses) {
        return selectOne(new LambdaQueryWrapperX<SalesOrderDO>().eq(SalesOrderDO::getLeadId, leadId)
                .in(SalesOrderDO::getStatus, statuses).orderByDesc(SalesOrderDO::getId).last("LIMIT 1"));
    }
    default SalesOrderDO selectByIdempotencyKey(String key) {
        return selectOne(SalesOrderDO::getSubmissionIdempotencyKey, key);
    }
    @Select("SELECT * FROM zsjos_order WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = b'0' FOR UPDATE")
    SalesOrderDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
