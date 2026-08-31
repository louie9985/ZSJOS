package cn.iocoder.yudao.module.zsjos.dal.mysql.personnel;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOwnershipLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PartnerOwnershipLogMapper extends BaseMapperX<PartnerOwnershipLogDO> {
    default PageResult<PartnerOwnershipLogDO> selectPageByPartnerId(Long partnerId, int pageNo, int pageSize) {
        var page = new cn.iocoder.yudao.framework.common.pojo.PageParam().setPageNo(pageNo).setPageSize(pageSize);
        return selectPage(page, new LambdaQueryWrapperX<PartnerOwnershipLogDO>()
                .eq(PartnerOwnershipLogDO::getPartnerId, partnerId)
                .orderByDesc(PartnerOwnershipLogDO::getOccurredAt).orderByDesc(PartnerOwnershipLogDO::getId));
    }
}
