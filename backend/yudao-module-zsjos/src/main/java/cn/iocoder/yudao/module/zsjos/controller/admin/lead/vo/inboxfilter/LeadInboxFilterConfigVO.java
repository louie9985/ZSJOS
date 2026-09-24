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
        /**
         * 单行时代的二级标题。保留用于读取既有已发布配置，写入时统一使用 {@link #sections}。
         */
        @Deprecated
        @Size(max = 20, message = "二级标题不能超过 20 个字符")
        private String sectionLabel;
        @Valid
        @Size(max = 2, message = "每个分组最多配置两个条件")
        private List<ConditionVO> conditions = new ArrayList<>();
        /**
         * 单行时代的二级筛选项。仅为反序列化既有已发布 JSON 保留，
         * 校验时归一化进 {@link #sections} 后清空；新配置一律写入 {@code sections}。
         */
        @Deprecated
        @Valid
        @Size(max = 20, message = "每个分组的二级筛选项不能超过 20 个")
        private List<OptionVO> options = new ArrayList<>();
        @Valid
        @Size(max = 3, message = "每个分组最多配置三行二级筛选")
        private List<SectionVO> sections = new ArrayList<>();
    }

    /** 二级筛选行，例如“当前环节”“快捷条件”。一行内多个筛选项互斥。 */
    @Data
    public static class SectionVO {
        @NotBlank(message = "二级行编码不能为空")
        @Pattern(regexp = "[a-z][a-z0-9_]{1,63}", message = "二级行编码格式不正确")
        private String key;
        @NotBlank(message = "二级行名称不能为空")
        @Size(max = 20, message = "二级行名称不能超过 20 个字符")
        private String label;
        @NotNull(message = "二级行排序不能为空")
        private Integer sort;
        @Valid
        @Size(max = 20, message = "每行二级筛选项不能超过 20 个")
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
