package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

public final class AccountDiagnosisBatchVO {
    private AccountDiagnosisBatchVO() {}

    public record Candidate(Long accountId, String accountNo, String accountName, String platformLabel,
                            Integer version, Long configVersionId, String currentStage, String accountStatus,
                            String nodeKey, boolean eligible, String reason) {}

    @Data public static class Item {
        @NotNull @Positive private Long accountId;
        @Valid private MediaAccountProfileVO.DiagnosisRequest initial;
        @Valid private WeeklyDiagnosisVO.Submit weekly;
    }

    @Data public static class Submit {
        @NotNull @Positive private Long accountId;
        @NotBlank private String templateType;
        private String nodeKey;
        @NotBlank @Size(max = 128) private String idempotencyKey;
        @NotEmpty @Size(max = 100) @Valid private List<@NotNull Item> accounts;
    }

    public record Result(Long accountId, Integer version) {}
}
