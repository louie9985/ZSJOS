package cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdvancedFilterGroupReqVO {
    @Pattern(regexp = "AND|OR", message = "筛选逻辑只能是 AND 或 OR")
    private String logic = "AND";
    @Valid @Size(max = 20) private List<AdvancedFilterConditionReqVO> conditions = new ArrayList<>();
    @Valid @Size(max = 5) private List<AdvancedFilterGroupReqVO> groups = new ArrayList<>();
}
