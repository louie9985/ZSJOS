package cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.ExamCollectionAttachmentDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExamCollectionAttachmentMapper extends BaseMapperX<ExamCollectionAttachmentDO> {
    default ExamCollectionAttachmentDO lock(Long id) {
        return selectOne(new LambdaQueryWrapperX<ExamCollectionAttachmentDO>().eq(ExamCollectionAttachmentDO::getId, id).last("FOR UPDATE"));
    }
}
