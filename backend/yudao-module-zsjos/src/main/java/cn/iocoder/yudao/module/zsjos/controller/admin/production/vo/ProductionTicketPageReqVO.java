package cn.iocoder.yudao.module.zsjos.controller.admin.production.vo;
import cn.iocoder.yudao.framework.common.pojo.PageParam; import lombok.Data; import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) public class ProductionTicketPageReqVO extends PageParam { private String status; private String keyword; }
