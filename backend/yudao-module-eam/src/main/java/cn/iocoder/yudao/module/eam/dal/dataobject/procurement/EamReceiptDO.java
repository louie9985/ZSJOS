package cn.iocoder.yudao.module.eam.dal.dataobject.procurement;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

@TableName(value = "eam_receipt", autoResultMap = true)
@KeySequence("eam_receipt_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EamReceiptDO extends BaseDO {
    @TableId
    private Long id;
    private String no;
    private Long purchaseId;
    private Integer type;
    private Long operatorUserId;
    private LocalDateTime operateTime;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> fileUrls;
    private String remark;
}
