package cn.iocoder.yudao.module.zsjos.dal.mysql.account;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountFieldConfigDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface MediaAccountFieldConfigMapper extends BaseMapperX<MediaAccountFieldConfigDO> {
    default MediaAccountFieldConfigDO selectPublished() {
        return selectOne(new LambdaQueryWrapperX<MediaAccountFieldConfigDO>()
                .eq(MediaAccountFieldConfigDO::getStatus, "published")
                .orderByDesc(MediaAccountFieldConfigDO::getVersionNo).last("LIMIT 1"));
    }

    default MediaAccountFieldConfigDO selectDraft() {
        return selectOne(new LambdaQueryWrapperX<MediaAccountFieldConfigDO>()
                .eq(MediaAccountFieldConfigDO::getStatus, "draft")
                .orderByDesc(MediaAccountFieldConfigDO::getVersionNo).last("LIMIT 1"));
    }

    default int updateDraft(Long id, Integer version, String fieldsJson) {
        return update(null, new LambdaUpdateWrapper<MediaAccountFieldConfigDO>()
                .eq(MediaAccountFieldConfigDO::getId, id)
                .eq(MediaAccountFieldConfigDO::getStatus, "draft")
                .eq(MediaAccountFieldConfigDO::getVersion, version)
                .set(MediaAccountFieldConfigDO::getFieldsJson, fieldsJson)
                .set(MediaAccountFieldConfigDO::getVersion, version + 1));
    }

    default int publish(Long id, Integer version, LocalDateTime publishedAt) {
        return update(null, new LambdaUpdateWrapper<MediaAccountFieldConfigDO>()
                .eq(MediaAccountFieldConfigDO::getId, id)
                .eq(MediaAccountFieldConfigDO::getStatus, "draft")
                .eq(MediaAccountFieldConfigDO::getVersion, version)
                .set(MediaAccountFieldConfigDO::getStatus, "published")
                .set(MediaAccountFieldConfigDO::getPublishedAt, publishedAt)
                .set(MediaAccountFieldConfigDO::getVersion, version + 1));
    }
}
