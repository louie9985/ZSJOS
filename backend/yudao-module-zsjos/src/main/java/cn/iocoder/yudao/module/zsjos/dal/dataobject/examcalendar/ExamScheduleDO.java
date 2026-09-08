package cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("zsjos_exam_schedule")
@KeySequence("zsjos_exam_schedule_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ExamScheduleDO extends TenantBaseDO {
    @TableId private Long id;
    private String scheduleType;
    private LocalDate exactDate;
    private LocalDate roughStartDate;
    private LocalDate roughEndDate;
    private Long categoryId;
    private Long productId;
    private String productNameSnapshot;
    private String selectedAttrsJson;
    private String selectedSpecsJson;
    private String frozenSkusJson;
    private String categoryNameSnapshot;
    private String categoryPathSnapshot;
    private String recordStatus;
    private String remark;
    private LocalDateTime publishedAt;
}
