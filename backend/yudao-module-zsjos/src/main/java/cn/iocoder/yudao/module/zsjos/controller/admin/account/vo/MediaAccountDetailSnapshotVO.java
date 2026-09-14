package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import lombok.Data;

@Data
public class MediaAccountDetailSnapshotVO {
    private String key;
    private String label;
    private String type;
    private Object value;
    private String displayValue;
    private String dictType;
    private String ownerType;
    private String group;
    private java.util.List<cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialVersionRespVO> materialVersions;
}
