package cn.iocoder.yudao.module.eam.service.coderule;

import cn.iocoder.yudao.module.eam.controller.admin.coderule.vo.EamCodeRuleSaveReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.coderule.EamCodeRuleDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * EAM 资产编号规则 Service 接口
 */
public interface EamCodeRuleService {

    Long createCodeRule(@Valid EamCodeRuleSaveReqVO reqVO);

    void updateCodeRule(@Valid EamCodeRuleSaveReqVO reqVO);

    void deleteCodeRule(Long id);

    List<EamCodeRuleDO> getCodeRuleList();

    EamCodeRuleDO getCodeRule(Long id);

    /**
     * 为指定分类生成下一个资产编号
     *
     * 必须在调用方的事务内执行：内部通过 SELECT ... FOR UPDATE 持有规则行锁，
     * 保证并发创建资产时流水号不重复。
     *
     * @param categoryId 分类编号
     * @return 资产编号，如 IT-2026-0001
     */
    String generateAssetCode(Long categoryId);

}
