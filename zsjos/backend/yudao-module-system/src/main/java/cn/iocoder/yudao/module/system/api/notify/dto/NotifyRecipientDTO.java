package cn.iocoder.yudao.module.system.api.notify.dto;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotifyRecipientDTO {
    private Integer userType;
    private Long userId;

    public static NotifyRecipientDTO admin(Long userId) {
        return new NotifyRecipientDTO(UserTypeEnum.ADMIN.getValue(), userId);
    }

    public static NotifyRecipientDTO partner(Long accountId) {
        return new NotifyRecipientDTO(UserTypeEnum.PARTNER.getValue(), accountId);
    }
}
