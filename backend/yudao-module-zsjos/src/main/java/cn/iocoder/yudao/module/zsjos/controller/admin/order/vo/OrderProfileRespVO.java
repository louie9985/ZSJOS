package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import lombok.Data;

@Data
public class OrderProfileRespVO {
    private Long personId;
    private String name;
    private String mobile;
    private String wechatId;
    private String fingerprint;
}
