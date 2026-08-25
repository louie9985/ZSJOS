package cn.iocoder.yudao.module.zsjos.controller.pub.mediascreen.vo;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MediaScreenRespVO {
    private Long tenantId;
    private LocalDateTime generatedAt;
    private long totalLeads;
    private List<RankItem> departmentRanking;
    private List<RankItem> memberRanking;
    private RankItem todayStar;
    private PartTimer partTimer;
    private List<TrendItem> trend;
    private HistorySnapshot historySnapshot;
    private boolean available = true;
    private LocalDate snapshotDate;
    private String source;
    private LocalDateTime snapshotCreatedAt;

    @Data public static class RankItem { private String name; private long leadCount; private int rank; }
    @Data public static class PartTimer { private boolean enabled; private List<RankItem> items; }
    @Data public static class TrendItem { private LocalDate date; private long leadCount; }
    @Data public static class HistorySnapshot { private boolean available; private LocalDate snapshotDate; private long totalLeads; }
}
