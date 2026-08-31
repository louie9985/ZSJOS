package cn.iocoder.yudao.module.system.api.ip;

import cn.iocoder.yudao.framework.common.biz.system.area.AreaCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.area.dto.AreaCommonRespDTO;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.ip.core.utils.AreaUtils;
import cn.iocoder.yudao.module.system.api.ip.dto.AreaRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.ip.AreaDO;
import cn.iocoder.yudao.module.system.service.ip.AreaService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AreaApiImpl implements AreaApi, AreaCommonApi {

    @Resource
    private AreaService areaService;

    @PostConstruct
    public void registerFrameworkProvider() {
        AreaUtils.init(this);
    }

    @Override
    public AreaRespDTO getArea(Integer id) {
        return BeanUtils.toBean(areaService.getArea(id), AreaRespDTO.class);
    }

    @Override
    public AreaRespDTO getAreaByParentIdAndSelectionCode(Integer parentId, String selectionCode) {
        return BeanUtils.toBean(areaService.getAreaByParentIdAndSelectionCode(parentId, selectionCode), AreaRespDTO.class);
    }

    @Override
    public List<AreaCommonRespDTO> getAreaList() {
        return BeanUtils.toBean(areaService.getAreaList(), AreaCommonRespDTO.class);
    }

}
