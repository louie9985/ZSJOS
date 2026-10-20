package cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoImageDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface StudentInfoImageMapper extends BaseMapperX<StudentInfoImageDO> {
    @org.apache.ibatis.annotations.Select("SELECT DISTINCT i.form_id FROM zsjos_student_info_image i JOIN zsjos_student_info_form f ON f.id=i.form_id AND f.tenant_id=i.tenant_id WHERE i.deleted=0 AND f.deleted=0 AND f.status<>'SUBMITTED' AND f.expires_at<DATE_SUB(NOW(),INTERVAL 7 DAY) ORDER BY i.form_id LIMIT 100")
    List<Long> expiredFormIds();
    default List<StudentInfoImageDO> byForm(Long formId) {
        return allByForm(formId).stream().filter(i -> !Boolean.TRUE.equals(i.getArchiveOnly())).toList();
    }
    default List<StudentInfoImageDO> allByForm(Long formId) {
        return selectList(new LambdaQueryWrapperX<StudentInfoImageDO>().eq(StudentInfoImageDO::getFormId,formId)
                .orderByAsc(StudentInfoImageDO::getId));
    }
}
