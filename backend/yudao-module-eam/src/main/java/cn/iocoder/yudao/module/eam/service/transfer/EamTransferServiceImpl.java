package cn.iocoder.yudao.module.eam.service.transfer;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.eam.controller.admin.transfer.vo.EamTransferCreateReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.transfer.vo.EamTransferPageReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.transfer.vo.EamTransferInspectReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.transfer.EamTransferDO;
import cn.iocoder.yudao.module.eam.dal.mysql.transfer.EamTransferMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetMapper;
import cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum;
import cn.iocoder.yudao.module.eam.enums.asset.EamChangeTypeEnum;
import cn.iocoder.yudao.module.eam.enums.transfer.EamTransferStatusEnum;
import cn.iocoder.yudao.module.eam.enums.transfer.EamTransferTypeEnum;
import cn.iocoder.yudao.module.eam.framework.approval.EamApprovalService;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetService;
import cn.iocoder.yudao.module.eam.service.common.EamDataScopeService;
import cn.iocoder.yudao.module.hrm.api.employee.HrmEmployeeApi;
import cn.iocoder.yudao.module.hrm.api.employee.dto.HrmEmployeeRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.TRANSFER_NOT_EXISTS;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.TRANSFER_STATUS_INVALID;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.TRANSFER_TYPE_INVALID;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.TRANSFER_RECEIVER_INVALID;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.TRANSFER_CANDIDATE_EMPTY;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.TRANSFER_CANCEL_FORBIDDEN;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.TRANSFER_INSPECTION_RESULT_INVALID;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.TRANSFER_PROCESS_UNAVAILABLE;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.TRANSFER_INSPECTION_FORBIDDEN;

/**
 * EAM 资产流转 Service 实现类
 */
@Service
@Validated
public class EamTransferServiceImpl implements EamTransferService {

    public static final String PROCESS_DEFINITION_KEY = "eam_asset_transfer";
    private static final String INSPECT_PERMISSION = "eam:transfer:inspect";

    @Resource
    private EamTransferMapper transferMapper;
    @Resource
    private EamAssetService assetService;
    @Resource
    private EamAssetMapper assetMapper;
    @Resource
    private EamApprovalService approvalService;
    @Resource private HrmEmployeeApi employeeApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private PermissionApi permissionApi;
    @Resource private EamDataScopeService dataScopeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTransfer(EamTransferCreateReqVO reqVO) {
        EamAssetDO asset = assetMapper.selectByIdForUpdate(reqVO.getAssetId());
        if (asset == null) throw exception(cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.ASSET_NOT_EXISTS);
        // 1. 按流转类型校验资产当前状态是否允许
        assetService.validateStatusTransition(asset, resolveAllowedStatuses(reqVO.getType()));

        // 2. 组装单据，转出方取资产当前归属
        Long applyUserId = SecurityFrameworkUtils.getLoginUserId();
        AdminUserRespDTO applicant = adminUserApi.getUser(applyUserId);
        if (applicant == null) throw exception(TRANSFER_RECEIVER_INVALID);
        HrmEmployeeRespDTO receiver = validateReceiver(reqVO);
        DeptRespDTO fromDept = asset.getUseDeptId() == null ? null : deptApi.getDept(asset.getUseDeptId());
        DeptRespDTO toDept = reqVO.getToDeptId() == null ? null : deptApi.getDept(reqVO.getToDeptId());
        DeptRespDTO applyDept = applicant.getDeptId() == null ? null : deptApi.getDept(applicant.getDeptId());
        HrmEmployeeRespDTO fromEmployee = asset.getUseEmployeeId() == null ? null : employeeApi.getEmployee(asset.getUseEmployeeId());
        EamTransferDO transfer = BeanUtils.toBean(reqVO, EamTransferDO.class);
        transfer.setNo(generateNo(reqVO.getType()));
        transfer.setFromEmployeeId(asset.getUseEmployeeId());
        transfer.setFromDeptId(asset.getUseDeptId());
        transfer.setAssetCodeSnapshot(asset.getAssetCode());
        transfer.setAssetNameSnapshot(asset.getName());
        transfer.setTypeLabelSnapshot(resolveTypeName(reqVO.getType()));
        transfer.setFromEmployeeNameSnapshot(fromEmployee == null ? null : fromEmployee.getName());
        transfer.setFromDeptNameSnapshot(fromDept == null ? null : fromDept.getName());
        transfer.setToEmployeeNameSnapshot(receiver == null ? null : receiver.getName());
        transfer.setToDeptNameSnapshot(toDept == null ? null : toDept.getName());
        transfer.setApplyUserId(applyUserId);
        transfer.setApplyUserNameSnapshot(applicant.getNickname());
        transfer.setApplyDeptId(applicant.getDeptId());
        transfer.setApplyDeptNameSnapshot(applyDept == null ? null : applyDept.getName());
        transfer.setApplyTime(LocalDateTime.now());
        transfer.setRoundNo(1);
        transfer.setVersion(0);

        // 3. 领用/借用/调拨需审批；退还/归还直接生效
        boolean needApproval = EamTransferTypeEnum.NEED_APPROVAL.contains(reqVO.getType());
        transfer.setStatus(needApproval ? EamTransferStatusEnum.APPROVING.getStatus()
                : EamTransferStatusEnum.PENDING_INSPECTION.getStatus());
        transferMapper.insert(transfer);

        if (needApproval) {
            Map<String, Object> variables = buildProcessVariables(transfer, applicant, receiver);
            String processInstanceId = approvalService.start(PROCESS_DEFINITION_KEY,
                    StrUtil.format("asset-transfer:{}:round:{}", transfer.getId(), transfer.getRoundNo()),
                    StrUtil.format("{}：{}", resolveTypeName(reqVO.getType()), asset.getName()), variables);
            if (StrUtil.isBlank(processInstanceId)) throw exception(TRANSFER_PROCESS_UNAVAILABLE);
            transferMapper.updateById(new EamTransferDO()
                    .setId(transfer.getId())
                    .setProcessInstanceId(processInstanceId));
        }
        return transfer.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleProcessResult(Long id, Integer bpmStatus, String reason) {
        EamTransferDO transfer = transferMapper.selectByIdForUpdate(id);
        if (transfer == null) throw exception(TRANSFER_NOT_EXISTS);
        if (!Objects.equals(transfer.getStatus(), EamTransferStatusEnum.APPROVING.getStatus())) return;
        if (cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(bpmStatus)) {
            transferMapper.updateById(new EamTransferDO().setId(id)
                    .setStatus(EamTransferStatusEnum.REJECTED.getStatus()).setReason(reason));
            return;
        }
        if (cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.CANCEL.getStatus().equals(bpmStatus)) {
            transferMapper.updateById(new EamTransferDO().setId(id)
                    .setStatus(EamTransferStatusEnum.CANCELLED.getStatus()));
            return;
        }
        if (!cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(bpmStatus)) return;
        if (!Objects.equals(transfer.getStatus(), EamTransferStatusEnum.APPROVING.getStatus())) {
            throw exception(TRANSFER_STATUS_INVALID);
        }
        EamAssetDO asset = assetMapper.selectByIdForUpdate(transfer.getAssetId());
        if (asset == null) throw exception(cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.ASSET_NOT_EXISTS);
        // 审批期间资产可能已被其他单据改动，生效前重新校验状态
        assetService.validateStatusTransition(asset, resolveAllowedStatuses(transfer.getType()));

        transferMapper.updateById(new EamTransferDO()
                .setId(id)
                .setStatus(EamTransferStatusEnum.APPROVED.getStatus()));
        applyTransfer(transfer, asset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTransfer(Long id, Long userId) {
        EamTransferDO transfer = validateTransferExists(id);
        if (!Objects.equals(transfer.getStatus(), EamTransferStatusEnum.APPROVING.getStatus())) {
            throw exception(TRANSFER_STATUS_INVALID);
        }
        if (!Objects.equals(transfer.getApplyUserId(), userId)) throw exception(TRANSFER_CANCEL_FORBIDDEN);
        approvalService.terminate(transfer.getProcessInstanceId(), "eam.asset-transfer.cancel", "申请人取消资产流转");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inspectTransfer(Long id, EamTransferInspectReqVO reqVO, Long inspectorUserId) {
        EamTransferDO transfer = transferMapper.selectByIdForUpdate(id);
        if (transfer == null) throw exception(TRANSFER_NOT_EXISTS);
        if (!Objects.equals(transfer.getStatus(), EamTransferStatusEnum.PENDING_INSPECTION.getStatus())) {
            throw exception(TRANSFER_STATUS_INVALID);
        }
        validateInspectionPermission(transfer, inspectorUserId);
        if (!Set.of(1, 2, 3, 4).contains(reqVO.getResult())) throw exception(TRANSFER_INSPECTION_RESULT_INVALID);
        if (reqVO.getResult() == 4) {
            transferMapper.updateById(new EamTransferDO().setId(id).setInspectionResult(4)
                    .setInspectionRemark(reqVO.getRemark()).setInspectionFileUrls(reqVO.getFileUrls())
                    .setInspectedByUserId(inspectorUserId).setInspectedAt(LocalDateTime.now()));
            return;
        }
        EamAssetDO asset = assetMapper.selectByIdForUpdate(transfer.getAssetId());
        if (asset == null) throw exception(cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.ASSET_NOT_EXISTS);
        assetService.validateStatusTransition(asset, resolveAllowedStatuses(transfer.getType()));
        if (reqVO.getResult() == 3) {
            assetService.applyChange(asset.getId(), EamAssetStatusEnum.LOST.getStatus(), 0L, 0L,
                    EamChangeTypeEnum.LOST.getType(), id, "资产归还验收：缺件或遗失");
        } else {
            applyTransfer(transfer, asset);
            if (reqVO.getResult() == 2) assetService.freeze(asset.getId(), "资产归还验收：损坏");
        }
        transferMapper.updateById(new EamTransferDO().setId(id)
                .setStatus(reqVO.getResult() == 1 ? EamTransferStatusEnum.COMPLETED.getStatus()
                        : EamTransferStatusEnum.EXCEPTION.getStatus())
                .setActualReturnDate(LocalDate.now())
                .setInspectionResult(reqVO.getResult()).setInspectionRemark(reqVO.getRemark())
                .setInspectionFileUrls(reqVO.getFileUrls()).setInspectedByUserId(inspectorUserId)
                .setInspectedAt(LocalDateTime.now()));
    }

    @Override
    public EamTransferDO getTransfer(Long id) {
        return transferMapper.selectById(id);
    }

    @Override
    public EamTransferDO getTransfer(Long id, Long userId) {
        EamTransferDO transfer = transferMapper.selectById(id);
        if (transfer == null) return null;
        var scope = dataScopeService.resolve(userId, EamDataScopeService.TRANSFER_QUERY_SELF,
                EamDataScopeService.TRANSFER_QUERY_DEPT);
        if (scope.all() || (scope.self() && (userId.equals(transfer.getApplyUserId())
                || userId.equals(transfer.getToEmployeeId())))
                || (scope.deptIds().contains(transfer.getFromDeptId())
                || scope.deptIds().contains(transfer.getToDeptId()))) return transfer;
        return null;
    }

    @Override
    public PageResult<EamTransferDO> getTransferPage(EamTransferPageReqVO reqVO) {
        return transferMapper.selectPage(reqVO);
    }

    @Override
    public PageResult<EamTransferDO> getTransferPage(EamTransferPageReqVO reqVO, Long userId) {
        return transferMapper.selectPage(reqVO, dataScopeService.resolve(userId,
                EamDataScopeService.TRANSFER_QUERY_SELF, EamDataScopeService.TRANSFER_QUERY_DEPT));
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

    @Override
    public List<EamTransferDO> getMyTransfers(Long userId) {
        HrmEmployeeRespDTO employee = employeeApi.getEmployeeByUserId(userId);
        return transferMapper.selectListByApplicantOrReceiver(userId, employee == null ? null : employee.getId());
    }

    private HrmEmployeeRespDTO validateReceiver(EamTransferCreateReqVO reqVO) {
        if (!EamTransferTypeEnum.NEED_APPROVAL.contains(reqVO.getType())) return null;
        if (reqVO.getToEmployeeId() == null || reqVO.getToDeptId() == null) throw exception(TRANSFER_RECEIVER_INVALID);
        HrmEmployeeRespDTO receiver = employeeApi.getEmployee(reqVO.getToEmployeeId());
        if (receiver == null || receiver.getUserId() == null || !Objects.equals(receiver.getDeptId(), reqVO.getToDeptId())) {
            throw exception(TRANSFER_RECEIVER_INVALID);
        }
        deptApi.validateDeptList(List.of(reqVO.getToDeptId()));
        if (Objects.equals(reqVO.getType(), EamTransferTypeEnum.BORROW.getType())
                && (reqVO.getExpectedReturnDate() == null || reqVO.getExpectedReturnDate().isBefore(LocalDate.now()))) {
            throw exception(TRANSFER_RECEIVER_INVALID);
        }
        return receiver;
    }

    private Map<String, Object> buildProcessVariables(EamTransferDO transfer, AdminUserRespDTO applicant,
                                                       HrmEmployeeRespDTO receiver) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("transferId", transfer.getId());
        variables.put("transferType", transfer.getType());
        variables.put("assetId", transfer.getAssetId());
        variables.put("assetCode", transfer.getAssetCodeSnapshot());
        variables.put("fromEmployeeId", transfer.getFromEmployeeId());
        variables.put("fromDeptId", transfer.getFromDeptId());
        variables.put("toEmployeeId", transfer.getToEmployeeId());
        variables.put("toDeptId", transfer.getToDeptId());
        variables.put("expectedReturnDate", transfer.getExpectedReturnDate() == null ? "" : transfer.getExpectedReturnDate().toString());
        variables.put("isAllocate", Objects.equals(transfer.getType(), EamTransferTypeEnum.ALLOCATE.getType()));
        DeptRespDTO applyDept = applicant.getDeptId() == null ? null : deptApi.getDept(applicant.getDeptId());
        Long departmentLeader = applyDept == null ? null : applyDept.getLeaderUserId();
        if (departmentLeader == null) throw exception(TRANSFER_CANDIDATE_EMPTY);
        variables.put("departmentLeaderUsers", List.of(departmentLeader));
        if (Objects.equals(transfer.getType(), EamTransferTypeEnum.ALLOCATE.getType())) {
            DeptRespDTO source = deptApi.getDept(transfer.getFromDeptId());
            DeptRespDTO target = deptApi.getDept(transfer.getToDeptId());
            if (source == null || target == null || source.getLeaderUserId() == null || target.getLeaderUserId() == null) {
                throw exception(TRANSFER_CANDIDATE_EMPTY);
            }
            variables.put("sourceDepartmentUsers", List.of(source.getLeaderUserId()));
            variables.put("targetDepartmentUsers", List.of(target.getLeaderUserId()));
            variables.put("sameDepartment", Objects.equals(source.getId(), target.getId()));
        } else {
            variables.put("sourceDepartmentUsers", List.of());
            variables.put("targetDepartmentUsers", List.of());
            variables.put("sameDepartment", false);
        }
        Set<Long> administrators = permissionApi.getEnabledUserIdsByPermission(INSPECT_PERMISSION);
        if (administrators.isEmpty() || receiver == null || receiver.getUserId() == null) throw exception(TRANSFER_CANDIDATE_EMPTY);
        variables.put("assetAdministratorUsers", administrators.stream().toList());
        variables.put("receiverUsers", List.of(receiver.getUserId()));
        return variables;
    }

    private EamTransferDO validateTransferExists(Long id) {
        EamTransferDO transfer = transferMapper.selectById(id);
        if (transfer == null) {
            throw exception(TRANSFER_NOT_EXISTS);
        }
        return transfer;
    }

    private void validateInspectionPermission(EamTransferDO transfer, Long inspectorUserId) {
        Set<Long> inspectors = permissionApi.getEnabledUserIdsByPermission(INSPECT_PERMISSION);
        if (inspectors == null || !inspectors.contains(inspectorUserId)) {
            throw exception(TRANSFER_INSPECTION_FORBIDDEN);
        }
        DeptDataPermissionRespDTO scope = permissionApi.getDeptDataPermission(inspectorUserId);
        boolean objectAllowed = scope != null && (Boolean.TRUE.equals(scope.getAll())
                || scope.getDeptIds() != null && (scope.getDeptIds().contains(transfer.getFromDeptId())
                || scope.getDeptIds().contains(transfer.getToDeptId())
                || scope.getDeptIds().contains(transfer.getApplyDeptId()))
                || Boolean.TRUE.equals(scope.getSelf()) && Objects.equals(transfer.getApplyUserId(), inspectorUserId));
        if (!objectAllowed) {
            throw exception(TRANSFER_INSPECTION_FORBIDDEN);
        }
    }

}
