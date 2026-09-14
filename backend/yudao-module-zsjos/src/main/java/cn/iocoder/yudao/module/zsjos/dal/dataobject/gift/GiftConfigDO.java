package cn.iocoder.yudao.module.zsjos.dal.dataobject.gift;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO; import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import lombok.EqualsAndHashCode;
@TableName("zsjos_gift_config") @KeySequence("zsjos_gift_config_seq") @Data @EqualsAndHashCode(callSuper=true)
public class GiftConfigDO extends TenantBaseDO { @TableId private Long id; private Long parentId; private String name; private String code; private Integer status; private Integer sort; }
