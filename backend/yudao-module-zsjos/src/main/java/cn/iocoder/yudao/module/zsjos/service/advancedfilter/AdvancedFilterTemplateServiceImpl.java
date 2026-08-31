package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterTemplateRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterTemplateSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.advancedfilter.AdvancedFilterTemplateDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.advancedfilter.AdvancedFilterTemplateMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.ADVANCED_FILTER_TEMPLATE_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.ADVANCED_FILTER_TEMPLATE_NOT_EXISTS;

@Service
public class AdvancedFilterTemplateServiceImpl implements AdvancedFilterTemplateService {
    private static final String SCOPE_PERSONAL = "personal";
    private static final String SCOPE_SYSTEM = "system";

    @Resource private AdvancedFilterTemplateMapper mapper;
    @Resource private AdvancedFilterService advancedFilterService;

    @Override
    public List<AdvancedFilterTemplateRespVO> visibleList(String scene, String pageKey, Long userId) {
        validateScenePage(scene, pageKey);
        return mapper.selectVisibleList(scene, pageKey, userId).stream().map(this::toResp).toList();
    }

    @Override
    public List<AdvancedFilterTemplateRespVO> systemList(String scene, String pageKey) {
        validateScenePage(scene, pageKey);
        return mapper.selectSystemList(scene, pageKey).stream().map(this::toResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPersonal(AdvancedFilterTemplateSaveReqVO reqVO, Long userId) {
        return create(reqVO, SCOPE_PERSONAL, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePersonal(AdvancedFilterTemplateSaveReqVO reqVO, Long userId) {
        update(reqVO, SCOPE_PERSONAL, userId);
    }

    @Override
    public void deletePersonal(Long id, Long userId) {
        AdvancedFilterTemplateDO template = require(id, SCOPE_PERSONAL, userId);
        mapper.deleteById(template.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSystem(AdvancedFilterTemplateSaveReqVO reqVO) {
        return create(reqVO, SCOPE_SYSTEM, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSystem(AdvancedFilterTemplateSaveReqVO reqVO) {
        update(reqVO, SCOPE_SYSTEM, null);
    }

    @Override
    public void deleteSystem(Long id) {
        AdvancedFilterTemplateDO template = require(id, SCOPE_SYSTEM, null);
        mapper.deleteById(template.getId());
    }

    private Long create(AdvancedFilterTemplateSaveReqVO reqVO, String scope, Long ownerUserId) {
        validate(reqVO);
        AdvancedFilterTemplateDO template = new AdvancedFilterTemplateDO();
        fill(template, reqVO, scope, ownerUserId);
        template.setVersion(0);
        mapper.insert(template);
        clearDefaultIfNeeded(template);
        return template.getId();
    }

    private void update(AdvancedFilterTemplateSaveReqVO reqVO, String scope, Long ownerUserId) {
        if (reqVO.getId() == null) throw exception(ADVANCED_FILTER_TEMPLATE_INVALID);
        AdvancedFilterTemplateDO existing = require(reqVO.getId(), scope, ownerUserId);
        if (!existing.getScene().equals(reqVO.getScene()) || !existing.getPageKey().equals(reqVO.getPageKey())) {
            throw exception(ADVANCED_FILTER_TEMPLATE_INVALID);
        }
        if (reqVO.getVersion() != null && !reqVO.getVersion().equals(existing.getVersion())) {
            throw exception(ADVANCED_FILTER_TEMPLATE_INVALID);
        }
        validate(reqVO);
        AdvancedFilterTemplateDO update = new AdvancedFilterTemplateDO();
        update.setId(existing.getId());
        fill(update, reqVO, scope, ownerUserId);
        update.setVersion(existing.getVersion() == null ? 1 : existing.getVersion() + 1);
        mapper.updateById(update);
        clearDefaultIfNeeded(update);
    }

    private void validate(AdvancedFilterTemplateSaveReqVO reqVO) {
        validateScenePage(reqVO.getScene(), reqVO.getPageKey());
        if (reqVO.getSort() == null || reqVO.getSort() < 0 || reqVO.getSort() > 9999
                || reqVO.getName() == null || reqVO.getName().isBlank()
                || reqVO.getEnabled() == null || reqVO.getDefaultTemplate() == null) {
            throw exception(ADVANCED_FILTER_TEMPLATE_INVALID);
        }
        advancedFilterService.validate(reqVO.getScene(), reqVO.getFilter());
    }

    private void validateScenePage(String scene, String pageKey) {
        if (scene == null || pageKey == null || scene.isBlank() || pageKey.isBlank()) {
            throw exception(ADVANCED_FILTER_TEMPLATE_INVALID);
        }
        if (!advancedFilterService.supportsScene(scene)) {
            throw exception(ADVANCED_FILTER_TEMPLATE_INVALID);
        }
    }

    private void fill(AdvancedFilterTemplateDO template, AdvancedFilterTemplateSaveReqVO reqVO,
                      String scope, Long ownerUserId) {
        template.setScene(reqVO.getScene());
        template.setPageKey(reqVO.getPageKey());
        template.setScope(scope);
        template.setOwnerUserId(ownerUserId);
        template.setName(reqVO.getName().trim());
        template.setFilterJson(JsonUtils.toJsonString(reqVO.getFilter()));
        template.setSort(reqVO.getSort());
        template.setEnabled(reqVO.getEnabled());
        template.setDefaultTemplate(reqVO.getDefaultTemplate());
    }

    private AdvancedFilterTemplateDO require(Long id, String scope, Long ownerUserId) {
        AdvancedFilterTemplateDO template = mapper.selectById(id);
        if (template == null || !scope.equals(template.getScope())
                || ownerUserId != null && !ownerUserId.equals(template.getOwnerUserId())) {
            throw exception(ADVANCED_FILTER_TEMPLATE_NOT_EXISTS);
        }
        return template;
    }

    private void clearDefaultIfNeeded(AdvancedFilterTemplateDO template) {
        if (Boolean.TRUE.equals(template.getDefaultTemplate())) {
            mapper.clearDefault(template.getScene(), template.getPageKey(), template.getScope(),
                    template.getOwnerUserId(), template.getId());
        }
    }

    private AdvancedFilterTemplateRespVO toResp(AdvancedFilterTemplateDO item) {
        AdvancedFilterTemplateRespVO resp = new AdvancedFilterTemplateRespVO();
        resp.setId(item.getId());
        resp.setScene(item.getScene());
        resp.setPageKey(item.getPageKey());
        resp.setScope(item.getScope());
        resp.setName(item.getName());
        resp.setFilter(JsonUtils.parseObject(item.getFilterJson(), AdvancedFilterGroupReqVO.class));
        resp.setSort(item.getSort());
        resp.setEnabled(item.getEnabled());
        resp.setDefaultTemplate(item.getDefaultTemplate());
        resp.setVersion(item.getVersion());
        resp.setCreateTime(item.getCreateTime());
        resp.setUpdateTime(item.getUpdateTime());
        return resp;
    }
}
