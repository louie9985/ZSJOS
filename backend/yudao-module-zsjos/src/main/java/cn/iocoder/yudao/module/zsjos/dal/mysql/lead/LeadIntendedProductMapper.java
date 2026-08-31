package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadIntendedProductDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Collection;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;

@Mapper
public interface LeadIntendedProductMapper extends BaseMapperX<LeadIntendedProductDO> {
    default Long selectCountByProductRef(String productRef) {
        return selectCount(LeadIntendedProductDO::getProductRef, productRef);
    }
    default Long selectCountBySkuRef(String skuRef) {
        return selectCount(LeadIntendedProductDO::getSkuRef, skuRef);
    }

    default List<LeadIntendedProductDO> selectListByLeadId(Long leadId) {
        return selectList(LeadIntendedProductDO::getLeadId, leadId);
    }

    default LeadIntendedProductDO selectPrimaryByLeadId(Long leadId) {
        return selectOne(new LambdaQueryWrapperX<LeadIntendedProductDO>()
                .eq(LeadIntendedProductDO::getLeadId, leadId)
                .eq(LeadIntendedProductDO::getIsPrimary, true).last("LIMIT 1"));
    }

    default List<LeadIntendedProductDO> selectListByLeadIds(Collection<Long> leadIds) {
        return selectList(new LambdaQueryWrapperX<LeadIntendedProductDO>()
                .in(LeadIntendedProductDO::getLeadId, leadIds)
                .orderByAsc(LeadIntendedProductDO::getSort));
    }

    default void deleteByLeadId(Long leadId) {
        delete(LeadIntendedProductDO::getLeadId, leadId);
    }
}
