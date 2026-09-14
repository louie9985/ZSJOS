package cn.iocoder.yudao.module.zsjos.service.gift;
import cn.iocoder.yudao.module.zsjos.controller.admin.gift.vo.GiftConfigSaveReqVO; import cn.iocoder.yudao.module.zsjos.dal.dataobject.gift.GiftConfigDO; import java.util.*;
public interface GiftConfigService { Long create(GiftConfigSaveReqVO r); void update(GiftConfigSaveReqVO r); void delete(Long id); List<GiftConfigDO> list(Integer status); }
