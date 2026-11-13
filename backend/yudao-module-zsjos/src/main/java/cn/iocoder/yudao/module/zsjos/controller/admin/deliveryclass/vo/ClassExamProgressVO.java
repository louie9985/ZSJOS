package cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo;

import java.util.List;
import jakarta.validation.constraints.*;

public final class ClassExamProgressVO {
    private ClassExamProgressVO() {}
    public record Command(@NotBlank String action, @NotNull Integer version) {}
    public record Outcome(@NotNull Long serviceRelationId, String value, @NotNull Integer version) {}
    public record Student(Long serviceRelationId, String name, String stage, String stageLabel, String outcome, String outcomeLabel, boolean supervisionReady) {}
    public record Option(String value, String label) {}
    public record View(String stage, String stageLabel, Integer version, List<String> actions, List<Student> students, List<Option> outcomes) {}
    public record Result(boolean changed, String message, View view, List<Student> blockers) {}
}
