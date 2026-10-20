package cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentArchiveRevisionDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface StudentArchiveRevisionMapper extends BaseMapperX<StudentArchiveRevisionDO> {
    default StudentArchiveRevisionDO byKey(String key) {
        return selectOne(StudentArchiveRevisionDO::getIdempotencyKey, key);
    }
    default StudentArchiveRevisionDO latest(Long formId) {
        return selectOne(new LambdaQueryWrapperX<StudentArchiveRevisionDO>()
                .eq(StudentArchiveRevisionDO::getFormId, formId).orderByDesc(StudentArchiveRevisionDO::getRevision).last("LIMIT 1"));
    }
    default List<StudentArchiveRevisionDO> history(Long formId) {
        return selectList(new LambdaQueryWrapperX<StudentArchiveRevisionDO>()
                .eq(StudentArchiveRevisionDO::getFormId, formId).orderByDesc(StudentArchiveRevisionDO::getRevision));
    }
}
