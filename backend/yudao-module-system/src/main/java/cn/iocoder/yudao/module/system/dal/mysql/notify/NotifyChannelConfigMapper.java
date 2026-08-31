package cn.iocoder.yudao.module.system.dal.mysql.notify;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyChannelConfigDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotifyChannelConfigMapper extends BaseMapperX<NotifyChannelConfigDO> {

    default NotifyChannelConfigDO selectByChannelCode(String channelCode) {
        return selectOne(NotifyChannelConfigDO::getChannelCode, channelCode);
    }
}
