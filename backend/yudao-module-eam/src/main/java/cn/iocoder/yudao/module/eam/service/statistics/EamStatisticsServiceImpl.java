package cn.iocoder.yudao.module.eam.service.statistics;

import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetPageReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.statistics.vo.EamStatisticsRespVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryDO;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetMapper;
import cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

/**
 * EAM 统计 Service 实现类
 */
@Service
@Validated
public class EamStatisticsServiceImpl implements EamStatisticsService {

    @Resource
    private EamAssetMapper assetMapper;
    @Resource
    private EamCategoryService categoryService;
    @Resource
    private DeptApi deptApi;

    @Override
    public EamStatisticsRespVO getStatistics() {
        EamAssetPageReqVO query = new EamAssetPageReqVO();
        query.setPageNo(1);
        query.setPageSize(Integer.MAX_VALUE);
        List<EamAssetDO> assets = assetMapper.selectPage(query).getList();

        EamStatisticsRespVO resp = new EamStatisticsRespVO();
        resp.setTotalCount((long) assets.size());
        resp.setStatusStats(buildStatusStats(assets));
        resp.setCategoryStats(buildCategoryStats(assets));
        resp.setDeptStats(buildDeptStats(assets));
        return resp;
    }

    private List<EamStatisticsRespVO.Item> buildStatusStats(List<EamAssetDO> assets) {
        Map<Integer, Long> counts = assets.stream()
                .filter(a -> a.getStatus() != null)
                .collect(Collectors.groupingBy(EamAssetDO::getStatus, Collectors.counting()));
        List<EamStatisticsRespVO.Item> items = new ArrayList<>();
        // 按枚举顺序输出，缺失的状态补 0，前端图表维度稳定
        for (EamAssetStatusEnum status : EamAssetStatusEnum.values()) {
            items.add(buildItem(String.valueOf(status.getStatus()), status.getName(),
                    counts.getOrDefault(status.getStatus(), 0L)));
        }
        return items;
    }

    private List<EamStatisticsRespVO.Item> buildCategoryStats(List<EamAssetDO> assets) {
        Map<Long, String> categoryNames = categoryService.getCategoryList().stream()
                .collect(Collectors.toMap(EamCategoryDO::getId, EamCategoryDO::getName, (a, b) -> a));
        Map<Long, Long> counts = assets.stream()
                .filter(a -> a.getCategoryId() != null)
                .collect(Collectors.groupingBy(EamAssetDO::getCategoryId,
                        LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .map(e -> buildItem(String.valueOf(e.getKey()),
                        categoryNames.getOrDefault(e.getKey(), "未分类"), e.getValue()))
                .toList();
    }

    private List<EamStatisticsRespVO.Item> buildDeptStats(List<EamAssetDO> assets) {
        Map<Long, DeptRespDTO> deptMap =
                deptApi.getDeptMap(convertSet(assets, EamAssetDO::getUseDeptId));
        Map<Long, Long> counts = assets.stream()
                .filter(a -> a.getUseDeptId() != null && a.getUseDeptId() > 0)
                .collect(Collectors.groupingBy(EamAssetDO::getUseDeptId,
                        LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .map(e -> {
                    DeptRespDTO dept = deptMap.get(e.getKey());
                    return buildItem(String.valueOf(e.getKey()),
                            dept != null ? dept.getName() : "未知部门", e.getValue());
                })
                .toList();
    }

    private EamStatisticsRespVO.Item buildItem(String key, String name, Long count) {
        EamStatisticsRespVO.Item item = new EamStatisticsRespVO.Item();
        item.setKey(key);
        item.setName(name);
        item.setCount(count);
        return item;
    }

}
