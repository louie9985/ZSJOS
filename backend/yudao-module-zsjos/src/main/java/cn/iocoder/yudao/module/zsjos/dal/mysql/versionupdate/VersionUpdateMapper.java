package cn.iocoder.yudao.module.zsjos.dal.mysql.versionupdate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.QueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.versionupdate.vo.VersionUpdateVO.Query;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.versionupdate.VersionUpdateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VersionUpdateMapper extends BaseMapperX<VersionUpdateDO> {
    @Select("SELECT * FROM zsjos_version_update WHERE id=#{id} AND deleted=0 FOR UPDATE")
    VersionUpdateDO lock(Long id);

    default PageResult<VersionUpdateDO> page(Query request, Long recipientId) {
        var query = new QueryWrapperX<VersionUpdateDO>()
                .likeIfPresent("title", request.getKeyword());
        if (recipientId == null) {
            query.eqIfPresent("status", request.getStatus());
        } else {
            query.eq("status", "PUBLISHED");
            query.apply("EXISTS (SELECT 1 FROM zsjos_version_update_recipient r WHERE r.update_id=zsjos_version_update.id AND r.tenant_id=zsjos_version_update.tenant_id AND r.deleted=0 AND r.user_id={0}"
                    + (Boolean.TRUE.equals(request.getPending()) ? " AND r.acknowledged_time IS NULL" : "") + ")", recipientId);
        }
        return selectPage(request, query.orderByDesc("publish_time", "id"));
    }
}
