package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactRecordDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;

@Mapper
public interface StudentContactRecordMapper extends BaseMapperX<StudentContactRecordDO> {
    default StudentContactRecordDO selectByIdempotencyKey(String key) {
        return selectOne(StudentContactRecordDO::getIdempotencyKey, key);
    }
    default List<StudentContactRecordDO> selectByRelationId(Long relationId) {
        return selectList(new LambdaQueryWrapperX<StudentContactRecordDO>()
                .eq(StudentContactRecordDO::getServiceRelationId, relationId)
                .orderByDesc(StudentContactRecordDO::getSubmittedAt)
                .orderByDesc(StudentContactRecordDO::getId));
    }
    default PageResult<StudentContactRecordDO> selectPageByRelationId(PageParam page, Long relationId) {
        return selectPage(page, new LambdaQueryWrapperX<StudentContactRecordDO>()
                .eq(StudentContactRecordDO::getServiceRelationId, relationId)
                .orderByDesc(StudentContactRecordDO::getSubmittedAt)
                .orderByDesc(StudentContactRecordDO::getId));
    }
}
