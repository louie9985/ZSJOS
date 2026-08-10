package cn.iocoder.yudao.module.system.api.notify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotifySceneRespDTO {

    private String code;
    private String name;
    private List<NotifySceneVariableRespDTO> variables;
    private List<NotifySceneRoleRespDTO> recipientRoles;
    private List<String> allowedActions;
}
