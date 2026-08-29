package cn.iocoder.yudao.module.zsjos.controller.admin.forcedform.vo;

import lombok.Data;

import java.util.List;

@Data
public class ForcedFormRecipientPreviewRespVO {

    private Integer recipientCount;
    private Integer skippedCompletedCount;
    private Integer filteredCount;
    private List<RecipientVO> recipients;

    @Data
    public static class RecipientVO {
        private Long userId;
        private String nickname;
        private String deptName;
        private String postNames;
    }
}
