package cn.iocoder.yudao.module.bpm.api.task.dto;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BpmStartSubjectDTO {
    private Integer userType;
    private Long userId;

    public String toFlowableId() {
        return userType + ":" + userId;
    }

    /**
     * Parses both the historical ADMIN numeric identifier and the typed external identifier.
     */
    public static BpmStartSubjectDTO fromFlowableId(String flowableId) {
        if (flowableId == null || flowableId.isBlank()) {
            return null;
        }
        int separator = flowableId.indexOf(':');
        try {
            if (separator < 0) {
                return new BpmStartSubjectDTO(UserTypeEnum.ADMIN.getValue(), Long.valueOf(flowableId));
            }
            return new BpmStartSubjectDTO(Integer.valueOf(flowableId.substring(0, separator)),
                    Long.valueOf(flowableId.substring(separator + 1)));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static Long getAdminUserId(String flowableId) {
        BpmStartSubjectDTO subject = fromFlowableId(flowableId);
        return subject != null && UserTypeEnum.ADMIN.getValue().equals(subject.getUserType())
                ? subject.getUserId() : null;
    }
}
