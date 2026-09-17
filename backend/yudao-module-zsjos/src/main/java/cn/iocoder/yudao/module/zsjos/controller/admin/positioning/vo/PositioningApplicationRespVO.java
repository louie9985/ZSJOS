package cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo;
import java.util.List;
public record PositioningApplicationRespVO(Integer version, Long submissionId, boolean canApply, boolean newerAvailable,
        List<PositioningCardRespVO> candidates) {}
