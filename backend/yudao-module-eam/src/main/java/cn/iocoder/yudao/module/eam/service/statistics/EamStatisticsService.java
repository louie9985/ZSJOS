package cn.iocoder.yudao.module.eam.service.statistics;

import cn.iocoder.yudao.module.eam.controller.admin.statistics.vo.EamStatisticsRespVO;

/**
 * EAM 统计 Service 接口
 */
public interface EamStatisticsService {

    /**
     * 获得资产统计概览：总数、原值合计、按状态/分类/部门的分布
     */
    EamStatisticsRespVO getStatistics();

}
