package cn.iocoder.yudao.module.eam.service.transfer;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.eam.controller.admin.transfer.vo.EamTransferCreateReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.transfer.vo.EamTransferPageReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.transfer.EamTransferDO;
import cn.iocoder.yudao.module.eam.dal.mysql.transfer.EamTransferMapper;
import cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum;
import cn.iocoder.yudao.module.eam.enums.asset.EamChangeTypeEnum;
import cn.iocoder.yudao.module.eam.enums.transfer.EamTransferStatusEnum;
import cn.iocoder.yudao.module.eam.enums.transfer.EamTransferTypeEnum;
import cn.iocoder.yudao.module.eam.framework.approval.EamApprovalService;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.TRANSFER_NOT_EXISTS;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.TRANSFER_STATUS_INVALID;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.TRANSFER_TYPE_INVALID;

/**
 * EAM 资产流转 Service 实现类
 */
@Service
@Validated
public class EamTransferServiceImpl implements EamTransferService {

    private static final String PROCESS_DEFINITION_KEY = "eam-transfer";

    @Resource
    private EamTransferMapper transferMapper;
    @Resource
    private EamAssetService assetService;
    @Resource
    private EamApprovalService approvalService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTransfer(EamTransferCreateReqVO reqVO) {
        EamAssetDO asset = assetService.validateAssetExists(reqVO.getAssetId());
        // 1. 按流转类型校验资产当前状态是否允许
        assetService.validateStatusTransition(asset, resolveAllowedStatuses(reqVO.getType()));

        // 2. 组装单据，转出方取资产当前归属
        EamTransferDO transfer = BeanUtils.toBean(reqVO, EamTransferDO.class);
        transfer.setNo(generateNo(reqVO.getType()));
        transfer.setFromEmployeeId(asset.getUseEmployeeId());
        transfer.setFromDeptId(asset.getUseDeptId());
        transfer.setApplyUserId(SecurityFrameworkUtils.getLoginUserId());
        transfer.setApplyTime(LocalDateTime.now());

        // 3. 领用/借用/调拨需审批；退还/归还直接生效
        boolean needApproval = EamTransferTypeEnum.NEED_APPROVAL.contains(reqVO.getType())
                && approvalService.approvalRequired();
        transfer.setStatus(needApproval
                ? EamTransferStatusEnum.APPROVING.getStatus()
                : EamTransferStatusEnum.APPROVED.getStatus());
        transferMapper.insert(transfer);

        if (needApproval) {
            String processInstanceId = approvalService.start(PROCESS_DEFINITION_KEY,
                    String.valueOf(transfer.getId()),
                    StrUtil.format("{}：{}", resolveTypeName(reqVO.getType()), asset.getName()));
            transferMapper.updateById(new EamTransferDO()
                    .setId(transfer.getId())
                    .setProcessInstanceId(processInstanceId));
        } else {
            applyTransfer(transfer, asset);
        }
        return transfer.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveTransfer(Long id) {
        EamTransferDO transfer = validateTransferExists(id);
        if (!Objects.equals(transfer.getStatus(), EamTransferStatusEnum.APPROVING.getStatus())) {
            throw exception(TRANSFER_STATUS_INVALID);
        }
        EamAssetDO asset = assetService.validateAssetExists(transfer.getAssetId());
        // 审批期间资产可能已被其他单据改动，生效前重新校验状态
        assetService.validateStatusTransition(asset, resolveAllowedStatuses(transfer.getType()));

        transferMapper.updateById(new EamTransferDO()
                .setId(id)
                .setStatus(EamTransferStatusEnum.APPROVED.getStatus()));
        applyTransfer(transfer, asset);
    }

    @Override
    public void rejectTransfer(Long id, String reason) {
        EamTransferDO transfer = validateTransferExists(id);
        if (!Objects.equals(transfer.getStatus(), EamTransferStatusEnum.APPROVING.getStatus())) {
            throw exception(TRANSFER_STATUS_INVALID);
        }
        // 驳回不改资产：审批期间资产状态未被占用，无需回滚
        transferMapper.updateById(new EamTransferDO()
                .setId(id)
                .setStatus(EamTransferStatusEnum.REJECTED.getStatus())
                .setReason(StrUtil.isBlank(reason) ? transfer.getReason() : reason));
    }

    @Override
    public void cancelTransfer(Long id) {
        EamTransferDO transfer = validateTransferExists(id);
        if (!Objects.equals(transfer.getStatus(), EamTransferStatusEnum.APPROVING.getStatus())) {
            throw exception(TRANSFER_STATUS_INVALID);
        }
        transferMapper.updateById(new EamTransferDO()
                .setId(id)
                .setStatus(EamTransferStatusEnum.CANCELLED.getStatus()));
    }

    @Override
    public EamTransferDO getTransfer(Long id) {
        return transferMapper.selectById(id);
    }

    @Override
    public PageResult<EamTransferDO> getTransferPage(EamTransferPageReqVO reqVO) {
        return transferMapper.selectPage(reqVO);
    }

    /**
     * 把流转单的效果落到资产台账上
     */
    private void applyTransfer(EamTransferDO transfer, EamAssetDO asset) {
        Integer type = transfer.getType();
        Integer newStatus;
        Long newEmployeeId;
        Long newDeptId;

        if (Objects.equals(type, EamTransferTypeEnum.RECEIVE.getType())) {
            newStatus = EamAssetStatusEnum.IN_USE.getStatus();
            newEmployeeId = transfer.getToEmployeeId();
            newDeptId = transfer.getToDeptId();
        } else if (Objects.equals(type, EamTransferTypeEnum.BORROW.getType())) {
            newStatus = EamAssetStatusEnum.LENT.getStatus();
            newEmployeeId = transfer.getToEmployeeId();
            newDeptId = transfer.getToDeptId();
        } else if (Objects.equals(type, EamTransferTypeEnum.ALLOCATE.getType())) {
            newStatus = EamAssetStatusEnum.IN_USE.getStatus();
            newEmployeeId = transfer.getToEmployeeId();
            newDeptId = transfer.getToDeptId();
        } else {
            // 退还 / 归还：回到闲置，清空归属由 applyChange 的 null 语义处理不了，
            // 这里显式写回 0 表示无归属
            newStatus = EamAssetStatusEnum.IDLE.getStatus();
            newEmployeeId = 0L;
            newDeptId = 0L;
        }

        assetService.applyChange(transfer.getAssetId(), newStatus, newEmployeeId, newDeptId,
                resolveChangeType(type), transfer.getId(),
                StrUtil.format("{}（单据 {}）", resolveTypeName(type), transfer.getNo()));
    }

    private Set<Integer> resolveAllowedStatuses(Integer type) {
        if (Objects.equals(type, EamTransferTypeEnum.RECEIVE.getType())) {
            return EamAssetStatusEnum.ALLOW_RECEIVE;
        }
        if (Objects.equals(type, EamTransferTypeEnum.RETURN.getType())) {
            return EamAssetStatusEnum.ALLOW_RETURN;
        }
        if (Objects.equals(type, EamTransferTypeEnum.BORROW.getType())) {
            return EamAssetStatusEnum.ALLOW_BORROW;
        }
        if (Objects.equals(type, EamTransferTypeEnum.GIVE_BACK.getType())) {
            return EamAssetStatusEnum.ALLOW_GIVE_BACK;
        }
        if (Objects.equals(type, EamTransferTypeEnum.ALLOCATE.getType())) {
            return EamAssetStatusEnum.ALLOW_TRANSFER;
        }
        throw exception(TRANSFER_TYPE_INVALID);
    }

    private Integer resolveChangeType(Integer type) {
        if (Objects.equals(type, EamTransferTypeEnum.RECEIVE.getType())) {
            return EamChangeTypeEnum.RECEIVE.getType();
        }
        if (Objects.equals(type, EamTransferTypeEnum.RETURN.getType())) {
            return EamChangeTypeEnum.RETURN.getType();
        }
        if (Objects.equals(type, EamTransferTypeEnum.BORROW.getType())) {
            return EamChangeTypeEnum.BORROW.getType();
        }
        if (Objects.equals(type, EamTransferTypeEnum.GIVE_BACK.getType())) {
            return EamChangeTypeEnum.GIVE_BACK.getType();
        }
        return EamChangeTypeEnum.TRANSFER.getType();
    }

    private String resolveTypeName(Integer type) {
        for (EamTransferTypeEnum item : EamTransferTypeEnum.values()) {
            if (Objects.equals(item.getType(), type)) {
                return item.getName();
            }
        }
        throw exception(TRANSFER_TYPE_INVALID);
    }

    private String generateNo(Integer type) {
        return StrUtil.format("TR{}-{}", type, System.currentTimeMillis());
    }

    private EamTransferDO validateTransferExists(Long id) {
        EamTransferDO transfer = transferMapper.selectById(id);
        if (transfer == null) {
            throw exception(TRANSFER_NOT_EXISTS);
        }
        return transfer;
    }

}
