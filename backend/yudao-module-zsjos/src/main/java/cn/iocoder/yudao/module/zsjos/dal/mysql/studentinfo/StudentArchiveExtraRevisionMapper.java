package cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentArchiveExtraRevisionDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
@Mapper
public interface StudentArchiveExtraRevisionMapper extends BaseMapperX<StudentArchiveExtraRevisionDO> {
    default StudentArchiveExtraRevisionDO byKey(String key) { return selectOne(StudentArchiveExtraRevisionDO::getIdempotencyKey,key); }
    default StudentArchiveExtraRevisionDO latest(Long id) {
        return selectOne(new LambdaQueryWrapperX<StudentArchiveExtraRevisionDO>()
            .eq(StudentArchiveExtraRevisionDO::getServiceRelationId,id).orderByDesc(StudentArchiveExtraRevisionDO::getRevision).last("LIMIT 1"));
    }
    default List<StudentArchiveExtraRevisionDO> history(Long id) {
        return selectList(new LambdaQueryWrapperX<StudentArchiveExtraRevisionDO>()
            .eq(StudentArchiveExtraRevisionDO::getServiceRelationId,id).orderByDesc(StudentArchiveExtraRevisionDO::getRevision));
    }
}
