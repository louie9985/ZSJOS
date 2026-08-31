package cn.iocoder.yudao.module.zsjos.dal.dataobject.production;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_production_ticket_item")
@KeySequence("zsjos_production_ticket_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductionTicketItemDO extends TenantBaseDO {
    @TableId private Long id;
    private Long ticketId;
    private Long contentId;
    private LocalDateTime deliveredAt;
    private String itemStatus;
}
