package cn.iocoder.yudao.module.system.service.ip;

import cn.iocoder.yudao.module.system.controller.admin.ip.vo.AreaListReqVO;
import cn.iocoder.yudao.module.system.controller.admin.ip.vo.AreaNodeRespVO;
import cn.iocoder.yudao.module.system.controller.admin.ip.vo.AreaSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.ip.AreaDO;

import java.util.List;

public interface AreaService {

    Integer createArea(AreaSaveReqVO reqVO);

    void updateArea(AreaSaveReqVO reqVO);

    void updateAreaStatus(Integer id, Integer status);

    AreaDO getArea(Integer id);

    AreaDO getAreaByParentIdAndSelectionCode(Integer parentId, String selectionCode);

    List<AreaDO> getAreaList();

    List<AreaDO> getAreaList(AreaListReqVO reqVO);

    List<AreaNodeRespVO> getEnabledChinaTree();

    String format(Integer id);

}
