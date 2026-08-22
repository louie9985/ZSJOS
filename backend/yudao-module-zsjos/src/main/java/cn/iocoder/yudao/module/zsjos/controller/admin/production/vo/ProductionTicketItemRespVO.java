package cn.iocoder.yudao.module.zsjos.controller.admin.production.vo;
import lombok.Data;
import java.time.LocalDateTime;
@Data public class ProductionTicketItemRespVO { private Long id; private Long ticketId; private Long contentId; private LocalDateTime deliveredAt; private String itemStatus; }
