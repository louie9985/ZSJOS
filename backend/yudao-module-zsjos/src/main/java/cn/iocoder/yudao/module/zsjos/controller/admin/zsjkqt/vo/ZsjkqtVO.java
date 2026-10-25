package cn.iocoder.yudao.module.zsjos.controller.admin.zsjkqt.vo;
import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;
import java.time.LocalDateTime;
/** The versioned snapshot preserves the original application's manual overrides and source facts. */
public interface ZsjkqtVO {
    record Save(@NotBlank @Size(max=64) String clientId, @NotBlank @Size(max=120) String name,
    @NotNull @Min(0) Integer version, @NotNull JsonNode snapshot) {
    }
    record Draft(Long id, String clientId, String name, int year, int month, String sourceFileName,
    String sourceFileHash, int employeeCount, int confirmedCount, int version,
    LocalDateTime updateTime, String updater, String updaterName, JsonNode snapshot) {
    }
    record Config(int version, JsonNode value) {
    }
    record ConfigSave(@NotNull @Min(0) Integer version, @NotNull JsonNode value) {
    }
    record Rename(@NotBlank @Size(max=120) String name, @NotNull @Min(0) Integer version) {
    }
    record Profile(@NotBlank @Size(max=200) String key, @Size(max=200) String name,
    @Size(max=200) String department, @Pattern(regexp="^$|\\d{4}-\\d{2}-\\d{2}") String entryDate, boolean enabled) {
    }
}
