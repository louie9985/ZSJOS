package cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo;
import java.util.List;
public record PositioningServiceOverviewRespVO(Long serviceRelationId, Long masterCardId, boolean canSelectMaster,
        boolean canCreate, boolean canSubmit, List<PositioningCardRespVO> candidates,
        PositioningCardRespVO current, PositioningCardRespVO effective, List<PositioningCardRespVO> history) {}
