package cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data
public class CalendarColorSaveReqVO {
    @NotBlank @Size(max=100) private String name;
    @NotBlank private String color;
}
