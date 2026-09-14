package cn.iocoder.yudao.module.zsjos.dal.dataobject.positioninginterview;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@Data @EqualsAndHashCode(callSuper = true)
@TableName("zsjos_student_positioning_interview_item")
public class PositioningInterviewItemDO extends TenantBaseDO {
 @TableId private Long id;
 private Long interviewId; private String fieldKey; private String titleSnapshot;
 private String interviewNoteSnapshot; private String confirmationStatus; private String statusLabelSnapshot;
 private String remark; private String fieldValue; private Integer sort; private Boolean systemField;
}

