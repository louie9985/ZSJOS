package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import java.util.Objects;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

/** Interprets recorded source facts without guessing historical identities from current roles or equal IDs. */
public final class LeadSourceInterpretation {
    private LeadSourceInterpretation() {}
    public enum State { LINKED, UNLINKED, NOT_APPLICABLE, UNVERIFIED }
    public enum Reason { NONE, NOT_RECORDED, PROVIDER_MISMATCH, SOURCE_MISMATCH }
    public record Result(State state, Reason reason) {
        public boolean needsReview() { return state == State.UNVERIFIED; }
        public boolean unlinked() { return state == State.UNLINKED; }
    }
    public static Result resolve(LeadDO lead) {
        return resolve(lead.getSourceType(), lead.getSubmissionSourceType(), lead.getSourceProviderRecorded(),
                lead.getSourceProviderUserId(), lead.getProviderOwnerType(), lead.getProviderOwnerId());
    }
    /** Write boundary only: old incomplete records retain their existing read/review semantics. */
    public static boolean hasConsistentWriteAttribution(LeadDO lead) {
        Result result = resolve(lead);
        if (result.needsReview()) return false;
        if (result.state() != State.LINKED) return true;
        Long provider = lead.getSourceProviderUserId();
        return SOURCE_INTERNAL_NEW_MEDIA.equals(lead.getSourceType())
                && Objects.equals(provider, lead.getSourceUserId())
                && Objects.equals(provider, lead.getContributionUserIdSnapshot());
    }
    public static Result resolve(String source, String entry, Boolean recorded, Long selected,
                                 String providerType, Long providerId) {
        boolean self = isSelfSourced(source), assistedEntry = isSelfSourced(entry);
        // Legacy native media can lack an entry snapshot; an explicit association still requires consistency.
        boolean explicitLink = Boolean.TRUE.equals(recorded) && selected != null;
        if (!self && !assistedEntry && !explicitLink) return new Result(State.NOT_APPLICABLE, Reason.NONE);
        if (!self && !SOURCE_INTERNAL_NEW_MEDIA.equals(source)) return review(Reason.SOURCE_MISMATCH);
        if (!Boolean.TRUE.equals(recorded)) return review(Reason.NOT_RECORDED);
        if (selected == null) {
            if (providerType != null || providerId != null) return review(Reason.PROVIDER_MISMATCH);
            return self ? new Result(State.UNLINKED, Reason.NONE) : review(Reason.SOURCE_MISMATCH);
        }
        if (!PROVIDER_OWNER_SYSTEM_USER.equals(providerType) || !Objects.equals(selected, providerId)) {
            return review(Reason.PROVIDER_MISMATCH);
        }
        return new Result(State.LINKED, Reason.NONE);
    }
    private static Result review(Reason reason) { return new Result(State.UNVERIFIED, reason); }
}
