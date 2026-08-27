package cn.iocoder.yudao.module.eam.service.asset;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetChangeLogDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetChangeLogMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EAM 资产变更记录 Service 实现类
 */
@Service
@Validated
public class EamAssetChangeLogServiceImpl implements EamAssetChangeLogService {

    @Resource
    private EamAssetChangeLogMapper changeLogMapper;

    @Override
    public void record(EamAssetDO before, EamAssetDO after, Integer changeType, Long bizId, String content) {
        EamAssetChangeLogDO log = EamAssetChangeLogDO.builder()
                .assetId(after.getId())
                .changeType(changeType)
                .beforeStatus(before != null ? before.getStatus() : null)
                .afterStatus(after.getStatus())
                .beforeEmployeeId(before != null ? before.getUseEmployeeId() : null)
                .afterEmployeeId(after.getUseEmployeeId())
                .beforeDeptId(before != null ? before.getUseDeptId() : null)
                .afterDeptId(after.getUseDeptId())
                .bizId(bizId)
                .content(content)
                .operatorId(SecurityFrameworkUtils.getLoginUserId())
                .operateTime(LocalDateTime.now())
                .build();
        changeLogMapper.insert(log);
    }

    @Override
    public List<EamAssetChangeLogDO> getChangeLogListByAssetId(Long assetId) {
        return changeLogMapper.selectListByAssetId(assetId);
    }

}
