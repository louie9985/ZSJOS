package cn.iocoder.yudao.module.bpm.api.definition.dto;

import lombok.Data;
import java.util.List;

/** null scope means a legacy unrestricted starter-select node; an empty user list is not unrestricted. */
@Data
public class BpmStarterSelectionScopeRespDTO {
    private List<Long> roleIds;
    private List<Long> userIds;
}
