package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;
import cn.iocoder.yudao.framework.common.pojo.PageParam; import lombok.Data; import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) public class MediaAccountPageReqVO extends PageParam { private String keyword; private String sStage; }
