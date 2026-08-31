package cn.iocoder.yudao.module.zsjos.dal.mysql.account;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountMaintenanceRevisionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MediaAccountMaintenanceRevisionMapper extends BaseMapperX<MediaAccountMaintenanceRevisionDO> {
    default PageResult<MediaAccountMaintenanceRevisionDO> selectPageByAccountId(PageParam page, Long accountId) {
        return selectPage(page, new LambdaQueryWrapperX<MediaAccountMaintenanceRevisionDO>()
                .eq(MediaAccountMaintenanceRevisionDO::getAccountId, accountId)
                .orderByDesc(MediaAccountMaintenanceRevisionDO::getRevisionNo));
    }

    default Integer selectMaxRevisionNo(Long accountId) {
        MediaAccountMaintenanceRevisionDO row = selectOne(new LambdaQueryWrapperX<MediaAccountMaintenanceRevisionDO>()
                .eq(MediaAccountMaintenanceRevisionDO::getAccountId, accountId)
                .orderByDesc(MediaAccountMaintenanceRevisionDO::getRevisionNo).last("LIMIT 1"));
        return row == null ? 0 : row.getRevisionNo();
    }
}
