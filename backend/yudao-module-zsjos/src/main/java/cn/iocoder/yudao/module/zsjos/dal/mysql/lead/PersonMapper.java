package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PersonMapper extends BaseMapperX<PersonDO> {
    default PersonDO selectByMobile(String mobile) {
        return selectOne(new LambdaQueryWrapperX<PersonDO>().eqIfPresent(PersonDO::getMobile, mobile));
    }
    default PersonDO selectByWechatId(String wechatId) {
        return selectOne(new LambdaQueryWrapperX<PersonDO>().eqIfPresent(PersonDO::getWechatId, wechatId));
    }
}
