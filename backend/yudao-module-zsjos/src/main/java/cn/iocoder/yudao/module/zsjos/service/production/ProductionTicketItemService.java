package cn.iocoder.yudao.module.zsjos.service.production;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.production.vo.ProductionTicketItemRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.production.ProductionTicketItemDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.production.ProductionTicketItemMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.production.ProductionTicketMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class ProductionTicketItemService {
    @Resource private ProductionTicketItemMapper itemMapper;
    @Resource private ProductionTicketMapper ticketMapper;
    @Resource private ContentMapper contentMapper;
    @Resource private ProductionTicketObjectPermissionProvider permissionProvider;
    public List<ProductionTicketItemRespVO> list(Long ticketId, Long userId) { requireTicket(ticketId, userId); return itemMapper.selectByTicketId(ticketId).stream().map(x -> BeanUtils.toBean(x, ProductionTicketItemRespVO.class)).toList(); }
    @ZsjosPermission(bizType = "production-ticket", bizId = "#ticketId", action = "edit") @Transactional(rollbackFor = Exception.class)
    public Long add(Long ticketId, Long contentId, Long userId) {
        requireTicket(ticketId, userId); if (contentMapper.selectById(contentId) == null) throw exception(CONTENT_NOT_EXISTS);
        ProductionTicketItemDO existing = itemMapper.selectByTicketAndContent(ticketId, contentId); if (existing != null) return existing.getId();
        ProductionTicketItemDO item = new ProductionTicketItemDO(); item.setTicketId(ticketId); item.setContentId(contentId); item.setItemStatus("pending"); itemMapper.insert(item); return item.getId();
    }
    @ZsjosPermission(bizType = "production-ticket", bizId = "#ticketId", action = "edit") @Transactional(rollbackFor = Exception.class)
    public void remove(Long ticketId, Long contentId, Long userId) { requireTicket(ticketId, userId); ProductionTicketItemDO item = itemMapper.selectByTicketAndContent(ticketId, contentId); if (item != null) itemMapper.deleteById(item.getId()); }
    private void requireTicket(Long ticketId, Long userId) { if (ticketMapper.selectById(ticketId) == null) throw exception(PRODUCTION_TICKET_NOT_EXISTS); if (!permissionProvider.hasPermission(ticketId, "read", userId)) throw exception(PRODUCTION_TICKET_PERMISSION_DENIED); }
}
