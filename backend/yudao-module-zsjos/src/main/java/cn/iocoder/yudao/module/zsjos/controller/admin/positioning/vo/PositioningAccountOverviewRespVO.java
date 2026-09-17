package cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo;

import java.util.List;

/** Draft progress and immutable submissions are separate projections of the same card. */
public record PositioningAccountOverviewRespVO(PositioningCardRespVO current,
        PositioningCardRespVO effective, List<PositioningCardRespVO> history) {}
