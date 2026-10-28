package cn.iocoder.yudao.module.zsjos.controller.admin.navigation.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class NavigationPreferenceVO {
    @NotNull @Size(max = 300) @Valid
    private List<Group> groups = new ArrayList<>();

    @Data
    public static class Group {
        @NotBlank @Size(max = 300)
        private String parentKey;
        @NotNull @Size(max = 500)
        private List<@NotBlank @Size(max = 160) String> keys;
        @NotNull @Size(max = 500)
        private List<@NotBlank @Size(max = 160) String> hiddenKeys = new ArrayList<>();
    }
}
