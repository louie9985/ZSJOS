package cn.iocoder.yudao.module.zsjos.dal.mysql.versionupdate;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.versionupdate.VersionUpdateRecipientDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VersionUpdateRecipientMapper extends BaseMapperX<VersionUpdateRecipientDO> {
    default VersionUpdateRecipientDO find(Long updateId, Long userId) {
        return selectOne(new LambdaQueryWrapper<VersionUpdateRecipientDO>()
                .eq(VersionUpdateRecipientDO::getUpdateId, updateId).eq(VersionUpdateRecipientDO::getUserId, userId));
    }
}
