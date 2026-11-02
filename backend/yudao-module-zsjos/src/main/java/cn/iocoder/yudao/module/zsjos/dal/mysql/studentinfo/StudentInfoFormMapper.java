package cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StudentInfoFormMapper extends BaseMapperX<StudentInfoFormDO> {
    /** One current customer archive; reuse copies must not promote old information. */
    default StudentInfoFormDO currentArchive(Long personId) {
        return selectOne(new LambdaQueryWrapperX<StudentInfoFormDO>()
                .eq(StudentInfoFormDO::getPersonId,personId).eq(StudentInfoFormDO::getStatus,"SUBMITTED")
                .and(q->q.isNull(StudentInfoFormDO::getSubmitSource).or().ne(StudentInfoFormDO::getSubmitSource,"reuse"))
                .orderByDesc(StudentInfoFormDO::getSubmittedAt).orderByDesc(StudentInfoFormDO::getId).last("LIMIT 1"));
    }
    default StudentInfoFormDO byCase(Long caseId) {
        return selectOne(new LambdaQueryWrapperX<StudentInfoFormDO>()
                .and(q -> q.eq(StudentInfoFormDO::getRegistrationCaseId, caseId).or().apply(
                        "EXISTS (SELECT 1 FROM zsjos_student_info_task t JOIN zsjos_registration_case c ON c.order_item_id=t.order_item_id AND c.tenant_id=t.tenant_id AND c.deleted=0 WHERE c.id={0} AND t.form_id=zsjos_student_info_form.id AND t.tenant_id=zsjos_student_info_form.tenant_id AND t.person_id=zsjos_student_info_form.person_id AND t.deleted=0)",caseId))
                .orderByDesc(StudentInfoFormDO::getId).last("LIMIT 1"));
    }
    default StudentInfoFormDO byToken(String hash) {
        return selectOne(new LambdaQueryWrapperX<StudentInfoFormDO>().eq(StudentInfoFormDO::getTokenHash, hash));
    }
    default StudentInfoFormDO lock(Long id) {
        return selectOne(new LambdaQueryWrapperX<StudentInfoFormDO>().eq(StudentInfoFormDO::getId, id).last("FOR UPDATE"));
    }
}
