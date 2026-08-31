package cn.iocoder.yudao.module.system.api.ip;

import cn.iocoder.yudao.module.system.api.ip.dto.AreaRespDTO;

/**
 * 地区公共 API。
 */
public interface AreaApi {

    AreaRespDTO getArea(Integer id);

    AreaRespDTO getAreaByParentIdAndSelectionCode(Integer parentId, String selectionCode);

}
