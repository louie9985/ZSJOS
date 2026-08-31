package cn.iocoder.yudao.module.zsjos.dal.mysql.forcedform;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.forcedform.ForcedFormSubmissionFileDO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ForcedFormSubmissionFileMapper extends BaseMapperX<ForcedFormSubmissionFileDO> {

    default List<ForcedFormSubmissionFileDO> selectExpiredTemporaryFiles(LocalDateTime expireAt) {
        return selectList(Wrappers.<ForcedFormSubmissionFileDO>lambdaQuery()
                .eq(ForcedFormSubmissionFileDO::getStatus, "TEMPORARY")
                .lt(ForcedFormSubmissionFileDO::getCreateTime, expireAt));
    }

}
