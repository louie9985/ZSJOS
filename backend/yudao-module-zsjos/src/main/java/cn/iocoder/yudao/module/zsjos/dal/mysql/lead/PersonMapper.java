package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Mapper
public interface PersonMapper extends BaseMapperX<PersonDO> {
    @Select("SELECT * FROM zsjos_person WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    PersonDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
    @Select("<script>SELECT * FROM zsjos_person WHERE deleted=b'0' AND ("
            + "<if test='mobile != null'>(BINARY mobile=BINARY #{mobile} OR BINARY wechat_id=BINARY #{mobile})</if>"
            + "<if test='mobile != null and wechatId != null'> OR </if>"
            + "<if test='wechatId != null'>(BINARY wechat_id=BINARY #{wechatId} OR BINARY mobile=BINARY #{wechatId})</if>"
            + ")</script>")
    List<PersonDO> selectDuplicateCandidates(@Param("mobile") String mobile, @Param("wechatId") String wechatId);
}
