package cn.iocoder.yudao.module.zsjos.dal.mysql.account;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountStudentLinkDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MediaAccountStudentLinkMapper extends BaseMapperX<MediaAccountStudentLinkDO> {
    default MediaAccountStudentLinkDO selectActiveByAccountId(Long accountId) {
        return selectOne(new LambdaQueryWrapperX<MediaAccountStudentLinkDO>()
                .eq(MediaAccountStudentLinkDO::getAccountId, accountId)
                .eq(MediaAccountStudentLinkDO::getStatus, "active").orderByDesc(MediaAccountStudentLinkDO::getId)
                .last("LIMIT 1"));
    }
}
