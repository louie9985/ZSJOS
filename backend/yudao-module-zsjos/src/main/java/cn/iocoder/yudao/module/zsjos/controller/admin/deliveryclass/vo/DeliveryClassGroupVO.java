package cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Commands distinguish group membership from class editing and student transfer. */
public final class DeliveryClassGroupVO {
    private DeliveryClassGroupVO() {}
    @Data public static class Save {
        @NotBlank @Size(max=100) private String name;
        private LocalDate referenceDate;
        private Integer version;
    }
    @Data @EqualsAndHashCode(callSuper=true) public static class CreateClasses extends DeliveryClassBatchCreateReqVO {
        @NotBlank @Pattern(regexp="NONE|EXISTING|NEW") private String groupMode;
        private Long groupId;
        @Valid private Save newGroup;
    }
    public record Created(Long groupId, List<Long> classIds) {}
    @Data public static class Member {
        @NotNull private Long id;
        @NotNull private Integer version;
    }
    @Data public static class Move {
        @NotEmpty @Size(max=200) @Valid private List<Member> classes;
        private Long targetGroupId;
    }
    @Data public static class Group {
        private Long id;
        private String name;
        private Long deptId;
        private String deptNameSnapshot;
        private LocalDate referenceDate;
        private Integer version;
        private boolean canCreate;
        private boolean canUpdate;
        private boolean canDelete;
    }
    @Data public static class Node {
        private String key;
        private String type;
        private String name;
        private Group group;
        private DeliveryClassRespVO single;
        private long classCount;
        private long homeroomCount;
        private long serviceCount;
        private long servingCount;
        private long completedCount;
        private String examDate;
        private List<DeliveryClassProjectRespVO> projects = List.of();
        private boolean autoExpand;
        private LocalDateTime createTime;
        private Long sortId;
    }
    @Data public static class Page {
        private List<Node> list;
        private long total;
        private long classTotal;
        public Page(List<Node> list, long total, long classTotal) {
            this.list = list; this.total = total; this.classTotal = classTotal;
        }
    }
}
