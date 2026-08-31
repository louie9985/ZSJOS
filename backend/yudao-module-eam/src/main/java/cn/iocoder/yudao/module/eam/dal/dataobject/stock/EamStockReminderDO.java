package cn.iocoder.yudao.module.eam.dal.dataobject.stock;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@TableName("eam_stock_reminder")
@KeySequence("eam_stock_reminder_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EamStockReminderDO extends BaseDO {
    @TableId
    private Long id;
    private String scene;
    private String businessType;
    private Long businessId;
    private LocalDate dueDate;
    private LocalDate reminderDate;
    private Integer status;
    private String content;
}
