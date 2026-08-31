package cn.iocoder.yudao.module.zsjos.dal.mysql.personnel;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PartnerAccountMapper extends BaseMapperX<PartnerAccountDO> {

    default PartnerAccountDO selectByPartnerId(Long partnerId) {
        return selectOne(PartnerAccountDO::getPartnerId, partnerId);
    }

    default PartnerAccountDO selectByMobile(String mobile) {
        return selectOne(PartnerAccountDO::getMobile, mobile);
    }
}
