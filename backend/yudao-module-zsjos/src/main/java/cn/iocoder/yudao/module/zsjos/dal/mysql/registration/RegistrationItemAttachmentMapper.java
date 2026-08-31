package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationItemAttachmentDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RegistrationItemAttachmentMapper extends BaseMapperX<RegistrationItemAttachmentDO> {
    default List<RegistrationItemAttachmentDO> selectByItemId(Long itemId) {
        return selectList(new LambdaQueryWrapperX<RegistrationItemAttachmentDO>()
                .eq(RegistrationItemAttachmentDO::getChecklistItemId, itemId)
                .orderByAsc(RegistrationItemAttachmentDO::getUploadedAt).orderByAsc(RegistrationItemAttachmentDO::getId));
    }

    default List<RegistrationItemAttachmentDO> selectByItemIds(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<RegistrationItemAttachmentDO>()
                .in(RegistrationItemAttachmentDO::getChecklistItemId, itemIds)
                .orderByAsc(RegistrationItemAttachmentDO::getUploadedAt).orderByAsc(RegistrationItemAttachmentDO::getId));
    }
}
