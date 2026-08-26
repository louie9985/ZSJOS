package cn.iocoder.yudao.module.zsjos.controller.pub.mediascreen.vo;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MediaScreenRespVO {
    private Long tenantId;
    private Long updatedAt;
    private Integer refreshIntervalSeconds;
    private boolean partTimeIncluded;
    private Metrics summary;
    private List<Department> departments;
    private Department partTimeCompanionDepartment;
    private Star todayStar;
    private Champion yesterdayChampion;
    private Trend trend;
    private Series series;

    // Legacy fields retained additively for existing consumers.
    private LocalDateTime generatedAt;
    private long totalLeads;
    private List<RankItem> departmentRanking;
    private List<RankItem> memberRanking;
    private PartTimer partTimer;
    private HistorySnapshot historySnapshot;
    private boolean available = true;
    private LocalDate snapshotDate;
    private String source;
    private LocalDateTime snapshotCreatedAt;

    @Data public static class Metrics {
        private long today;
        private long week;
        private long monthTotal;
        private long monthEffective;
    }
    @Data public static class Department {
        private String name;
        private String subtitle;
        private Metrics metrics;
        private List<Member> members;
    }
    @Data public static class Member {
        private String name;
        private long today;
        private long week;
        private long monthTotal;
        private long monthEffective;
    }
    @Data public static class Star {
        private String name;
        private String deptName;
        private long today;
        private long leadCount;
        private long yesterday;
        private int rankToday;
        private int rank;
        private int rankYesterday;
    }
    @Data public static class Champion {
        private String name;
        private String deptName;
        private long count;
    }
    @Data public static class Trend {
        private List<Long> today;
        private List<Long> yesterday;
        private int stepMinutes;
    }
    @Data public static class Series {
        private List<Long> submitted;
        private List<Long> valid;
    }

    @Data public static class RankItem { private String name; private long leadCount; private int rank; }
    @Data public static class PartTimer { private boolean enabled; private List<RankItem> items; }
    @Data public static class HistorySnapshot { private boolean available; private LocalDate snapshotDate; private long totalLeads; }
}
