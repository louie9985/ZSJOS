package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MyStudentPageReqVO;
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
            + "<if test='mobile != null'>(CAST(mobile AS BINARY)=CAST(#{mobile} AS BINARY) "
            + "OR CAST(wechat_id AS BINARY)=CAST(#{mobile} AS BINARY))</if>"
            + "<if test='mobile != null and wechatId != null'> OR </if>"
            + "<if test='wechatId != null'>(CAST(wechat_id AS BINARY)=CAST(#{wechatId} AS BINARY) "
            + "OR CAST(mobile AS BINARY)=CAST(#{wechatId} AS BINARY))</if>"
            + ")</script>")
    List<PersonDO> selectDuplicateCandidates(@Param("mobile") String mobile, @Param("wechatId") String wechatId);

    default PageResult<PersonDO> selectStudentPage(MyStudentPageReqVO reqVO, java.util.Collection<Long> visibleIds,
                                                   java.util.Collection<Long> matchedIds) {
        LambdaQueryWrapperX<PersonDO> query = new LambdaQueryWrapperX<>();
        if (visibleIds == null || visibleIds.isEmpty()) query.eq(PersonDO::getId, -1L);
        else query.in(PersonDO::getId, visibleIds);
        if (matchedIds != null) {
            if (matchedIds.isEmpty()) query.eq(PersonDO::getId, -1L);
            else query.in(PersonDO::getId, matchedIds);
        }
        if (reqVO.getKeyword() != null && !reqVO.getKeyword().isBlank()) {
            String keyword = reqVO.getKeyword().trim();
            query.and(value -> value.like(PersonDO::getName, keyword).or().like(PersonDO::getMobile, keyword)
                    .or().like(PersonDO::getWechatId, keyword)
                    .or().apply("EXISTS (SELECT 1 FROM zsjos_lead sl WHERE sl.person_id=zsjos_person.id "
                            + "AND sl.deleted=b'0' AND sl.lead_no LIKE CONCAT('%',{0},'%'))", keyword));
        }
        return selectPage(reqVO, query.orderByDesc(PersonDO::getId));
    }
}
