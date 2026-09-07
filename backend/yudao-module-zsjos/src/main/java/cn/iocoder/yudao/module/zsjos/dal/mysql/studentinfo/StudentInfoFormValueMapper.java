package cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormValueDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface StudentInfoFormValueMapper extends BaseMapperX<StudentInfoFormValueDO> {
    default List<StudentInfoFormValueDO> byForm(Long id) {
        return selectList(new LambdaQueryWrapperX<StudentInfoFormValueDO>()
                .eq(StudentInfoFormValueDO::getFormId, id).orderByAsc(StudentInfoFormValueDO::getId));
    }
}
