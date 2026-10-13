package cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo;
import java.util.List;
public record CalendarPaletteRespVO(List<Color> presets, List<Color> colors) {
    public record Color(String id, String name, String color) {}
}
