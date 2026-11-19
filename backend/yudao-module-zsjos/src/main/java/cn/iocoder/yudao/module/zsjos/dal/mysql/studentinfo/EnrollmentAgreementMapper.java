package cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.EnrollmentAgreementDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EnrollmentAgreementMapper extends BaseMapperX<EnrollmentAgreementDO> {
    @org.apache.ibatis.annotations.Select("SELECT draft_body FROM zsjos_enrollment_agreement WHERE id=1 AND deleted=0")
    String starterBody();
}
