package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_person")
@KeySequence("zsjos_person_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class PersonDO extends TenantBaseDO {
    @TableId private Long id;
    private String personNo;
    /** Manual student-list grouping; independent of course service lifecycle. Database defaults new people to true. */
    private Boolean inServicePeriod;
    private String name;
    private String mobile;
    private String wechatId;
    private String identityStatus;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private Integer version;
}
