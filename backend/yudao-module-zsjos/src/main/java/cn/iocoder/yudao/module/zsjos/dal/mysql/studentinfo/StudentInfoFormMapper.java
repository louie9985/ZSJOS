package cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StudentInfoFormMapper extends BaseMapperX<StudentInfoFormDO> {
    default StudentInfoFormDO byLead(Long leadId) {
        return selectOne(new LambdaQueryWrapperX<StudentInfoFormDO>()
                .eq(StudentInfoFormDO::getLeadId, leadId).orderByDesc(StudentInfoFormDO::getId).last("LIMIT 1"));
    }
    default StudentInfoFormDO submitted(Long leadId) {
        return selectOne(new LambdaQueryWrapperX<StudentInfoFormDO>().eq(StudentInfoFormDO::getLeadId, leadId)
                .eq(StudentInfoFormDO::getStatus, "SUBMITTED").orderByDesc(StudentInfoFormDO::getId).last("LIMIT 1"));
    }
    default StudentInfoFormDO byToken(String hash) {
        return selectOne(new LambdaQueryWrapperX<StudentInfoFormDO>().eq(StudentInfoFormDO::getTokenHash, hash));
    }
    default StudentInfoFormDO lock(Long id) {
        return selectOne(new LambdaQueryWrapperX<StudentInfoFormDO>().eq(StudentInfoFormDO::getId, id).last("FOR UPDATE"));
    }
}
