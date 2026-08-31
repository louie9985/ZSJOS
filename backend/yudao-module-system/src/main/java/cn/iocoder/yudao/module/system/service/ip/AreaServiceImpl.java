package cn.iocoder.yudao.module.system.service.ip;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.ip.core.Area;
import cn.iocoder.yudao.framework.ip.core.enums.AreaTypeEnum;
import cn.iocoder.yudao.framework.ip.core.utils.AreaUtils;
import cn.iocoder.yudao.module.system.controller.admin.ip.vo.AreaListReqVO;
import cn.iocoder.yudao.module.system.controller.admin.ip.vo.AreaNodeRespVO;
import cn.iocoder.yudao.module.system.controller.admin.ip.vo.AreaSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.ip.AreaDO;
import cn.iocoder.yudao.module.system.dal.mysql.ip.AreaMapper;
import cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants;
import jakarta.annotation.Resource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

@Service
@Validated
public class AreaServiceImpl implements AreaService {

    private static final String OTHER_SELECTION_CODE = "OTHER";
    private static final Comparator<AreaDO> AREA_SIBLING_COMPARATOR = Comparator
            .comparing((AreaDO area) -> OTHER_SELECTION_CODE.equals(area.getSelectionCode()) ? 1 : 0)
            .thenComparing(AreaDO::getSort)
            .thenComparing(AreaDO::getId);

    @Resource
    private AreaMapper areaMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisKeyConstants.AREA_LIST, allEntries = true),
            @CacheEvict(cacheNames = RedisKeyConstants.AREA_TREE, allEntries = true),
            @CacheEvict(cacheNames = RedisKeyConstants.AREA_ITEM, allEntries = true)
    })
    public Integer createArea(AreaSaveReqVO reqVO) {
        if (areaMapper.selectById(reqVO.getId()) != null) {
            throw exception(AREA_CODE_DUPLICATE);
        }
        Integer type = validateParent(null, reqVO.getParentId());
        validateNameUnique(null, reqVO.getParentId(), reqVO.getName());
        AreaDO area = BeanUtils.toBean(reqVO, AreaDO.class);
        area.setSelectionCode(String.valueOf(reqVO.getId()));
        area.setType(type);
        validateLeafSelectable(type, reqVO.getLeafSelectable());
        areaMapper.insert(area);
        AreaUtils.clearCache();
        return area.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisKeyConstants.AREA_LIST, allEntries = true),
            @CacheEvict(cacheNames = RedisKeyConstants.AREA_TREE, allEntries = true),
            @CacheEvict(cacheNames = RedisKeyConstants.AREA_ITEM, allEntries = true)
    })
    public void updateArea(AreaSaveReqVO reqVO) {
        AreaDO existing = validateExists(reqVO.getId());
        Integer type = validateParent(reqVO.getId(), reqVO.getParentId());
        validateNameUnique(reqVO.getId(), reqVO.getParentId(), reqVO.getName());
        validateLeafSelectable(type, reqVO.getLeafSelectable());
        List<AreaDO> descendants = getDescendants(reqVO.getId());
        int typeOffset = type - existing.getType();
        if (descendants.stream().anyMatch(area -> area.getType() + typeOffset > AreaTypeEnum.DISTRICT.getType())) {
            throw exception(AREA_LEVEL_EXCEEDED);
        }
        AreaDO area = BeanUtils.toBean(reqVO, AreaDO.class);
        area.setType(type);
        areaMapper.updateById(area);
        if (typeOffset != 0) {
            descendants.forEach(descendant -> {
                AreaDO update = new AreaDO();
                update.setId(descendant.getId());
                update.setType(descendant.getType() + typeOffset);
                areaMapper.updateById(update);
            });
        }
        AreaUtils.clearCache();
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisKeyConstants.AREA_LIST, allEntries = true),
            @CacheEvict(cacheNames = RedisKeyConstants.AREA_TREE, allEntries = true),
            @CacheEvict(cacheNames = RedisKeyConstants.AREA_ITEM, allEntries = true)
    })
    public void updateAreaStatus(Integer id, Integer status) {
        validateExists(id);
        AreaDO update = new AreaDO();
        update.setId(id);
        update.setStatus(status);
        areaMapper.updateById(update);
        AreaUtils.clearCache();
    }

    private AreaDO validateExists(Integer id) {
        AreaDO area = areaMapper.selectById(id);
        if (area == null) {
            throw exception(AREA_NOT_EXISTS);
        }
        return area;
    }

    private List<AreaDO> getDescendants(Integer id) {
        List<AreaDO> allAreas = areaMapper.selectAll();
        Map<Integer, List<AreaDO>> childrenByParent = new HashMap<>();
        allAreas.forEach(area -> childrenByParent.computeIfAbsent(area.getParentId(), key -> new ArrayList<>()).add(area));
        List<AreaDO> descendants = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        collectDescendants(id, childrenByParent, descendants, visited);
        return descendants;
    }

    private void collectDescendants(Integer parentId, Map<Integer, List<AreaDO>> childrenByParent,
                                    List<AreaDO> descendants, Set<Integer> visited) {
        for (AreaDO child : childrenByParent.getOrDefault(parentId, List.of())) {
            if (!visited.add(child.getId())) {
                throw exception(AREA_PARENT_INVALID);
            }
            descendants.add(child);
            collectDescendants(child.getId(), childrenByParent, descendants, visited);
        }
    }

    private Integer validateParent(Integer id, Integer parentId) {
        if (Objects.equals(id, parentId)) {
            throw exception(AREA_PARENT_INVALID);
        }
        if (Objects.equals(parentId, Area.ID_GLOBAL)) {
            return AreaTypeEnum.COUNTRY.getType();
        }
        AreaDO parent = areaMapper.selectById(parentId);
        if (parent == null) {
            throw exception(AREA_PARENT_NOT_EXISTS);
        }
        if (parent.getType() >= AreaTypeEnum.DISTRICT.getType()) {
            throw exception(AREA_LEVEL_EXCEEDED);
        }
        Integer cursor = parentId;
        for (int i = 0; i <= AreaTypeEnum.values().length; i++) {
            if (Objects.equals(cursor, id)) {
                throw exception(AREA_PARENT_INVALID);
            }
            AreaDO current = areaMapper.selectById(cursor);
            if (current == null || Objects.equals(current.getParentId(), Area.ID_GLOBAL)) {
                break;
            }
            cursor = current.getParentId();
        }
        return parent.getType() + 1;
    }

    private void validateNameUnique(Integer id, Integer parentId, String name) {
        AreaDO existing = areaMapper.selectByParentIdAndName(parentId, name);
        if (existing != null && !Objects.equals(existing.getId(), id)) {
            throw exception(AREA_NAME_DUPLICATE);
        }
    }

    private void validateLeafSelectable(Integer type, Boolean leafSelectable) {
        if (Boolean.TRUE.equals(leafSelectable) && !AreaTypeEnum.PROVINCE.getType().equals(type)) {
            throw exception(AREA_LEAF_SELECTABLE_INVALID);
        }
    }

    @Override
    @Cacheable(cacheNames = RedisKeyConstants.AREA_ITEM, key = "#id", unless = "#result == null")
    public AreaDO getArea(Integer id) {
        return areaMapper.selectById(id);
    }

    @Override
    public AreaDO getAreaByParentIdAndSelectionCode(Integer parentId, String selectionCode) {
        return areaMapper.selectByParentIdAndSelectionCode(parentId, selectionCode);
    }

    @Override
    @Cacheable(cacheNames = RedisKeyConstants.AREA_LIST, key = "'all'")
    public List<AreaDO> getAreaList() {
        return sortAreasForDisplay(areaMapper.selectAll());
    }

    @Override
    public List<AreaDO> getAreaList(AreaListReqVO reqVO) {
        return sortAreasForDisplay(areaMapper.selectList(reqVO));
    }

    private List<AreaDO> sortAreasForDisplay(List<AreaDO> areas) {
        areas.sort(Comparator.comparing(AreaDO::getType)
                .thenComparing(AreaDO::getParentId)
                .thenComparing(AREA_SIBLING_COMPARATOR));
        return areas;
    }

    @Override
    @Cacheable(cacheNames = RedisKeyConstants.AREA_TREE, key = "'enabled-china'")
    public List<AreaNodeRespVO> getEnabledChinaTree() {
        List<AreaDO> enabled = areaMapper.selectAll().stream()
                .filter(area -> CommonStatusEnum.ENABLE.getStatus().equals(area.getStatus()))
                .toList();
        Map<Integer, AreaNodeRespVO> nodes = new HashMap<>();
        for (AreaDO area : enabled) {
            AreaNodeRespVO node = BeanUtils.toBean(area, AreaNodeRespVO.class);
            node.setChildren(new ArrayList<>());
            nodes.put(area.getId(), node);
        }
        for (AreaDO area : enabled) {
            AreaNodeRespVO parent = nodes.get(area.getParentId());
            AreaNodeRespVO node = nodes.get(area.getId());
            if (parent != null && node != null) {
                parent.getChildren().add(node);
            }
        }
        nodes.values().forEach(node -> node.getChildren().sort(Comparator
                .comparing((AreaNodeRespVO child) -> OTHER_SELECTION_CODE.equals(child.getSelectionCode()) ? 1 : 0)));
        AreaNodeRespVO china = nodes.get(Area.ID_CHINA);
        return china == null ? List.of() : china.getChildren();
    }

    @Override
    public String format(Integer id) {
        Map<Integer, AreaDO> areas = new HashMap<>();
        for (AreaDO area : areaMapper.selectAll()) {
            areas.put(area.getId(), area);
        }
        AreaDO area = areas.get(id);
        if (area == null) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i <= AreaTypeEnum.values().length; i++) {
            names.add(0, area.getName());
            if (Objects.equals(area.getParentId(), Area.ID_GLOBAL) || Objects.equals(area.getParentId(), Area.ID_CHINA)) {
                break;
            }
            area = areas.get(area.getParentId());
            if (area == null) {
                break;
            }
        }
        return String.join(" ", names);
    }

}
