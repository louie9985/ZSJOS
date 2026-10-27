package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentServiceAnnotationDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StudentServiceAnnotationMapper extends BaseMapperX<StudentServiceAnnotationDO> {
    default StudentServiceAnnotationDO byRelation(Long relationId) {
        return selectOne(StudentServiceAnnotationDO::getServiceRelationId, relationId);
    }

    default int replace(StudentServiceAnnotationDO row, int expectedVersion) {
        return update(null, new LambdaUpdateWrapper<StudentServiceAnnotationDO>()
                .eq(StudentServiceAnnotationDO::getId, row.getId())
                .eq(StudentServiceAnnotationDO::getVersion, expectedVersion)
                .set(StudentServiceAnnotationDO::getProxyStudent, row.getProxyStudent())
                .set(StudentServiceAnnotationDO::getProxyStudentLabel, row.getProxyStudentLabel())
                .set(StudentServiceAnnotationDO::getAgentName, row.getAgentName())
                .set(StudentServiceAnnotationDO::getServiceNeeded, row.getServiceNeeded())
                .set(StudentServiceAnnotationDO::getServiceNeededLabel, row.getServiceNeededLabel())
                .set(StudentServiceAnnotationDO::getVersion, row.getVersion())
                .set(StudentServiceAnnotationDO::getUpdater, row.getUpdater())
                .set(StudentServiceAnnotationDO::getUpdateTime, row.getUpdateTime()));
    }
}
