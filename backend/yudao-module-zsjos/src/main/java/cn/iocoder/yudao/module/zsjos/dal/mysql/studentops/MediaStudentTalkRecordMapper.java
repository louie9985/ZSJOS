package cn.iocoder.yudao.module.zsjos.dal.mysql.studentops;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentops.MediaStudentTalkRecordDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface MediaStudentTalkRecordMapper extends BaseMapperX<MediaStudentTalkRecordDO> {
    default List<MediaStudentTalkRecordDO> selectByStudent(Long personId) {
        return selectList(new LambdaQueryWrapperX<MediaStudentTalkRecordDO>()
                .eq(MediaStudentTalkRecordDO::getStudentPersonId, personId)
                .orderByDesc(MediaStudentTalkRecordDO::getUpdateTime)
                .orderByDesc(MediaStudentTalkRecordDO::getId));
    }
}
