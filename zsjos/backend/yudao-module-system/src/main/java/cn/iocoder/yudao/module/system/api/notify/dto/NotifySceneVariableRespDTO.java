package cn.iocoder.yudao.module.system.api.notify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotifySceneVariableRespDTO {

    private String key;
    private String label;
    private Boolean sensitive;
}
