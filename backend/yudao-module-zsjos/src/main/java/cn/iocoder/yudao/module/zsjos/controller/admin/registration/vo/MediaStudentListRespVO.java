package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class MediaStudentListRespVO extends MyStudentRespVO {
    private List<AccountVO> accounts = List.of();

    @Data
    public static class AccountVO {
        private Long id;
        private String accountNo;
        private String nickname;
        private String platformValue;
        private String platformLabel;
    }
}
