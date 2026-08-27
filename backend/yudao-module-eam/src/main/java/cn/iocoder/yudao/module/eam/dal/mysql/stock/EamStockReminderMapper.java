package cn.iocoder.yudao.module.eam.dal.mysql.stock;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockReminderDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EamStockReminderMapper extends BaseMapperX<EamStockReminderDO> {
    default EamStockReminderDO selectByKey(String scene, String businessType, Long businessId,
                                           java.time.LocalDate reminderDate) {
        return selectOne(new LambdaQueryWrapperX<EamStockReminderDO>()
                .eq(EamStockReminderDO::getScene, scene)
                .eq(EamStockReminderDO::getBusinessType, businessType)
                .eq(EamStockReminderDO::getBusinessId, businessId)
                .eq(EamStockReminderDO::getReminderDate, reminderDate));
    }
}
