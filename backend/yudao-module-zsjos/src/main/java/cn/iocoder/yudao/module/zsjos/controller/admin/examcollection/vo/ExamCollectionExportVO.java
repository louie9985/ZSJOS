package cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.*;

public class ExamCollectionExportVO {
    @Data public static class Settings {
        @Min(0) private int revision;
        @NotNull private Layout layout = Layout.MATERIAL;
        private boolean groupByClass;
        @NotEmpty @Size(max=12) private List<@NotBlank @Size(max=80) String> naming = List.of("name", "material", "side", "sequence");
        @NotNull @Size(max=100) private Map<String, @NotEmpty @Size(max=12) List<@NotBlank @Size(max=80) String>> overrides = new LinkedHashMap<>();
    }
    public enum Layout { MATERIAL, STUDENT, FLAT }
    public enum Mode { ARCHIVE, SUBMISSIONS }
    @Data public static class Request {
        @NotNull @Valid private Settings settings = new Settings();
        @NotNull private Mode mode = Mode.ARCHIVE;
        @NotNull @Size(max=100) private List<@Positive Long> classIds = List.of();
        @NotNull @Size(max=1000) private List<@Positive Long> serviceIds = List.of();
        @Size(max=100) private String keyword;
        private Boolean filled;
        private boolean includeAdditional;
        @NotNull @Size(max=100) private List<@NotBlank String> fieldKeys = List.of();
        @Size(max=64) private String fingerprint;
    }
    public record Preview(int people, int files, long bytes, List<String> paths, List<String> warnings, String fingerprint) {}
}
