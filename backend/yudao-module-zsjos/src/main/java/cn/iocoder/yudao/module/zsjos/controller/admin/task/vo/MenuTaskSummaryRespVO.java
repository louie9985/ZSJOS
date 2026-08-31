package cn.iocoder.yudao.module.zsjos.controller.admin.task.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MenuTaskSummaryRespVO {
    private long generatedAt;
    private long total;
    private List<Item> items;

    @Data
    @AllArgsConstructor
    public static class Item {
        private String menuPath;
        private long count;
        private String severity;
        private List<String> sourceTypes;
        private Target target;
    }

    @Data
    @AllArgsConstructor
    public static class Target {
        private String path;
        private String query;
    }
}
