package cn.iocoder.yudao.module.zsjos.controller.admin.content.vo;
import cn.iocoder.yudao.framework.common.pojo.PageParam; import lombok.Data; import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) public class ContentPageReqVO extends PageParam { private String status; private String keyword; }
