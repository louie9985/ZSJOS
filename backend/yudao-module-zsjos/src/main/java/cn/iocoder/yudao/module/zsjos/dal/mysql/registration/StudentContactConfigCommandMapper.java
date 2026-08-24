package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactConfigCommandDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

@Mapper
public interface StudentContactConfigCommandMapper extends BaseMapperX<StudentContactConfigCommandDO> {
    default StudentContactConfigCommandDO selectByIdempotencyKey(String idempotencyKey) {
        return selectOne(StudentContactConfigCommandDO::getIdempotencyKey, idempotencyKey);
    }

    default int updateResult(Long id, Long resultConfigId) {
        return update(null, new LambdaUpdateWrapper<StudentContactConfigCommandDO>()
                .eq(StudentContactConfigCommandDO::getId, id)
                .set(StudentContactConfigCommandDO::getResultConfigId, resultConfigId));
    }
}
