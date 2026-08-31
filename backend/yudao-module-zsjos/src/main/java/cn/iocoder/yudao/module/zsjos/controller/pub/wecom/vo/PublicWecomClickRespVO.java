package cn.iocoder.yudao.module.zsjos.controller.pub.wecom.vo;

import lombok.Data;

@Data
public class PublicWecomClickRespVO {
    private String audience;
    private String actionType;
    private String targetPath;
    private String fallbackPath;
}
