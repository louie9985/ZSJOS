package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PartnerLeaderboardRespVO {
    private String period;
    private String periodLabel;
    private String type;
    private String typeLabel;
    private String valueLabel;
    private String valueUnit;
    private String ruleText;
    private Long total;
    private Integer pageNo;
    private Integer pageSize;
    private List<Member> list;
    private List<Member> top3;
    private Member myRank;
    private Gap previousGap;
    private Gap top10Gap;
    private List<Member> nearbyRanks;

    @Data
    public static class Member {
        private Long partnerId;
        private String displayName;
        private Integer rank;
        private BigDecimal value;
        private Boolean isMe;
        private BigDecimal gapToPrevious;
    }

    @Data
    public static class Gap {
        private Integer targetRank;
        private BigDecimal value;
        private String displayValue;
        private Boolean targetReached;
    }
}
