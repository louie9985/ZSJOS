package cn.iocoder.yudao.module.eam.service.scrap;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.eam.controller.admin.scrap.vo.EamScrapCreateReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.scrap.vo.EamScrapPageReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.scrap.EamScrapDO;
import cn.iocoder.yudao.module.eam.dal.mysql.scrap.EamScrapMapper;
import cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum;
import cn.iocoder.yudao.module.eam.enums.asset.EamChangeTypeEnum;
import cn.iocoder.yudao.module.eam.framework.approval.EamApprovalService;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.SCRAP_NOT_EXISTS;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.SCRAP_STATUS_INVALID;

/**
 * EAM 报废 Service 实现类
 */
@Service
@Validated
public class EamScrapServiceImpl implements EamScrapService {

    private static final String PROCESS_DEFINITION_KEY = "eam-scrap";

    private static final Integer STATUS_APPROVING = 0;
    private static final Integer STATUS_SCRAPPED = 1;
    private static final Integer STATUS_REJECTED = 2;

    @Resource
    private EamScrapMapper scrapMapper;
    @Resource
    private EamAssetService assetService;
    @Resource
    private EamApprovalService approvalService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createScrap(EamScrapCreateReqVO reqVO) {
        EamAssetDO asset = assetService.validateAssetExists(reqVO.getAssetId());
        assetService.validateStatusTransition(asset, EamAssetStatusEnum.ALLOW_SCRAP_APPLY);

        EamScrapDO scrap = BeanUtils.toBean(reqVO, EamScrapDO.class);
        scrap.setNo(StrUtil.format("SC-{}", System.currentTimeMillis()));
        scrap.setScrapDate(reqVO.getScrapDate() != null ? reqVO.getScrapDate() : LocalDate.now());
        scrap.setApplyUserId(SecurityFrameworkUtils.getLoginUserId());
        scrap.setApplyTime(LocalDateTime.now());

        boolean needApproval = approvalService.approvalRequired();
        scrap.setStatus(needApproval ? STATUS_APPROVING : STATUS_SCRAPPED);
        scrapMapper.insert(scrap);

        if (needApproval) {
            // 先置待报废：占住资产，避免审批期间被领用或调拨
            assetService.applyChange(reqVO.getAssetId(), EamAssetStatusEnum.PENDING_SCRAP.getStatus(),
                    null, null, EamChangeTypeEnum.SCRAP_APPLY.getType(), scrap.getId(),
                    StrUtil.format("申请报废（单据 {}）", scrap.getNo()));
            String processInstanceId = approvalService.start(PROCESS_DEFINITION_KEY,
                    String.valueOf(scrap.getId()),
                    StrUtil.format("报废：{}", asset.getName()));
            scrapMapper.updateById(new EamScrapDO()
                    .setId(scrap.getId())
                    .setProcessInstanceId(processInstanceId));
        } else {
            assetService.applyChange(reqVO.getAssetId(), EamAssetStatusEnum.SCRAPPED.getStatus(),
                    null, null, EamChangeTypeEnum.SCRAP_APPROVE.getType(), scrap.getId(),
                    StrUtil.format("报废（单据 {}）", scrap.getNo()));
        }
        return scrap.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveScrap(Long id) {
        EamScrapDO scrap = validateScrapExists(id);
        if (!Objects.equals(scrap.getStatus(), STATUS_APPROVING)) {
            throw exception(SCRAP_STATUS_INVALID);
        }
        EamAssetDO asset = assetService.validateAssetExists(scrap.getAssetId());
        assetService.validateStatusTransition(asset, EamAssetStatusEnum.ALLOW_SCRAP_APPROVE);

        scrapMapper.updateById(new EamScrapDO().setId(id).setStatus(STATUS_SCRAPPED));
        assetService.applyChange(scrap.getAssetId(), EamAssetStatusEnum.SCRAPPED.getStatus(),
                null, null, EamChangeTypeEnum.SCRAP_APPROVE.getType(), id,
                StrUtil.format("报废通过（单据 {}）", scrap.getNo()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectScrap(Long id, String reason) {
        EamScrapDO scrap = validateScrapExists(id);
        if (!Objects.equals(scrap.getStatus(), STATUS_APPROVING)) {
            throw exception(SCRAP_STATUS_INVALID);
        }
        EamAssetDO asset = assetService.validateAssetExists(scrap.getAssetId());

        scrapMapper.updateById(new EamScrapDO().setId(id).setStatus(STATUS_REJECTED));
        // 恢复申请前状态；缺失记录时退回闲置，避免资产卡在待报废
        Integer restored = asset.getPreviousStatus() != null
                ? asset.getPreviousStatus() : EamAssetStatusEnum.IDLE.getStatus();
        assetService.applyChange(scrap.getAssetId(), restored, null, null,
                EamChangeTypeEnum.SCRAP_REJECT.getType(), id,
                StrUtil.format("报废驳回：{}", StrUtil.blankToDefault(reason, "不同意报废")));
    }

    @Override
    public EamScrapDO getScrap(Long id) {
        return scrapMapper.selectById(id);
    }

    @Override
    public PageResult<EamScrapDO> getScrapPage(EamScrapPageReqVO reqVO) {
        return scrapMapper.selectPage(reqVO);
    }

    private EamScrapDO validateScrapExists(Long id) {
        EamScrapDO scrap = scrapMapper.selectById(id);
        if (scrap == null) {
            throw exception(SCRAP_NOT_EXISTS);
        }
        return scrap;
    }

}
