package cn.iocoder.yudao.module.zsjos.dal.mysql.production;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.production.ProductionTicketItemDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ProductionTicketItemMapper extends BaseMapperX<ProductionTicketItemDO> {
    default List<ProductionTicketItemDO> selectByTicketId(Long ticketId) {
        return selectList(new LambdaQueryWrapper<ProductionTicketItemDO>().eq(ProductionTicketItemDO::getTicketId, ticketId).orderByAsc(ProductionTicketItemDO::getId));
    }
    default ProductionTicketItemDO selectByTicketAndContent(Long ticketId, Long contentId) {
        return selectOne(new LambdaQueryWrapper<ProductionTicketItemDO>().eq(ProductionTicketItemDO::getTicketId, ticketId).eq(ProductionTicketItemDO::getContentId, contentId));
    }
}
