package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LeadInboxFilterConfigVO {

    @Valid
    @NotEmpty(message = "至少配置一个一级分组")
    @Size(max = 20, message = "一级分组不能超过 20 个")
    private List<GroupVO> groups = new ArrayList<>();

    @Data
    public static class GroupVO {
        @NotBlank(message = "分组编码不能为空")
        @Pattern(regexp = "[a-z][a-z0-9_]{1,63}", message = "分组编码格式不正确")
        private String key;
        @NotBlank(message = "分组名称不能为空")
        @Size(max = 20, message = "分组名称不能超过 20 个字符")
        private String label;
        @NotNull(message = "分组排序不能为空")
        private Integer sort;
        @NotNull(message = "分组显隐不能为空")
        private Boolean enabled;
        @Size(max = 20, message = "二级标题不能超过 20 个字符")
        private String sectionLabel;
        @Valid
        @Size(max = 2, message = "每个分组最多配置两个条件")
        private List<ConditionVO> conditions = new ArrayList<>();
        @Valid
        @Size(max = 20, message = "每个分组的二级筛选项不能超过 20 个")
        private List<OptionVO> options = new ArrayList<>();
    }

    @Data
    public static class OptionVO {
        @NotBlank(message = "筛选项编码不能为空")
        @Pattern(regexp = "[a-z][a-z0-9_]{1,63}", message = "筛选项编码格式不正确")
        private String key;
        @NotBlank(message = "筛选项名称不能为空")
        @Size(max = 20, message = "筛选项名称不能超过 20 个字符")
        private String label;
        @NotNull(message = "筛选项排序不能为空")
        private Integer sort;
        @NotNull(message = "筛选项显隐不能为空")
        private Boolean enabled;
        @Valid
        @Size(max = 2, message = "每个筛选项最多配置两个条件")
        private List<ConditionVO> conditions = new ArrayList<>();
    }

    @Data
    public static class ConditionVO {
        @NotBlank(message = "条件字段不能为空")
        private String field;
        @NotEmpty(message = "条件值不能为空")
        @Size(max = 20, message = "单个条件值不能超过 20 个")
        private List<@NotBlank String> values = new ArrayList<>();
    }
}
