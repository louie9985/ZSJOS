package cn.iocoder.yudao.framework.common.biz.system.area;

import cn.iocoder.yudao.framework.common.biz.system.area.dto.AreaCommonRespDTO;

import java.util.List;

/**
 * System-owned area data exposed to framework utilities.
 */
public interface AreaCommonApi {

    List<AreaCommonRespDTO> getAreaList();

}
