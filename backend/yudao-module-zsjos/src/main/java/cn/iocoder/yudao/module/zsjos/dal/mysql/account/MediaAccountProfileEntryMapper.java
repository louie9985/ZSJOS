package cn.iocoder.yudao.module.zsjos.dal.mysql.account;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountProfileEntryDO;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface MediaAccountProfileEntryMapper extends BaseMapperX<MediaAccountProfileEntryDO> {
    default MediaAccountProfileEntryDO replay(Long accountId, Long userId, String key) {
        return selectOne(new LambdaQueryWrapperX<MediaAccountProfileEntryDO>()
            .eq(MediaAccountProfileEntryDO::getAccountId,accountId).eq(MediaAccountProfileEntryDO::getOperatedByUserId,userId)
            .eq(MediaAccountProfileEntryDO::getIdempotencyKey,key));
    }
    default PageResult<MediaAccountProfileEntryDO> page(Long accountId, PageParam page) {
        return selectPage(page,new LambdaQueryWrapperX<MediaAccountProfileEntryDO>().eq(MediaAccountProfileEntryDO::getAccountId,accountId)
            .orderByDesc(MediaAccountProfileEntryDO::getId));
    }
    default PageResult<MediaAccountProfileEntryDO> positioningPage(Long accountId, PageParam page) {
        return selectPage(page, new LambdaQueryWrapperX<MediaAccountProfileEntryDO>()
                .eq(MediaAccountProfileEntryDO::getAccountId, accountId)
                .eq(MediaAccountProfileEntryDO::getKind, "POSITIONING")
                .orderByDesc(MediaAccountProfileEntryDO::getId));
    }
}
