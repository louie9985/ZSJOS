package cn.iocoder.yudao.module.zsjos.service.examcollection;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure decisions shared by initial submission, repeated submission and manual resolution. */
public final class ExamCollectionPolicy {
    private ExamCollectionPolicy() {}

    public enum MatchStatus { MATCHED, UNMATCHED, AMBIGUOUS, INVALID_BINDING }
    public record Candidate(long serviceRelationId, String identityCard) {}
    public record Match(MatchStatus status, Long serviceRelationId) {}

    public static String normalizeIdentity(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Callers must supply only live, tenant-owned services from the frozen roster.
     * An explicitly confirmed binding may be outside that roster, but must still be live.
     * Identity format/checksum validation belongs to the existing collection validator.
     */
    public static Match match(String identityCard, Collection<Candidate> roster,
                              Long confirmedServiceId, boolean confirmedServiceAvailable) {
        String identity = normalizeIdentity(identityCard);
        if (identity.isEmpty()) return new Match(MatchStatus.UNMATCHED, null);
        if (confirmedServiceId != null) {
            return confirmedServiceAvailable
                    ? new Match(MatchStatus.MATCHED, confirmedServiceId)
                    : new Match(MatchStatus.INVALID_BINDING, null);
        }
        Set<Long> services = new LinkedHashSet<>();
        for (Candidate candidate : roster) {
            if (identity.equals(normalizeIdentity(candidate.identityCard()))) services.add(candidate.serviceRelationId());
        }
        if (services.isEmpty()) return new Match(MatchStatus.UNMATCHED, null);
        if (services.size() > 1) return new Match(MatchStatus.AMBIGUOUS, null);
        return new Match(MatchStatus.MATCHED, services.iterator().next());
    }

    /** Submission sequence is assigned under the task lock, never from browser timestamps. */
    public static boolean shouldApply(long submittedSequence, Long lastAppliedSequence) {
        if (submittedSequence <= 0) throw new IllegalArgumentException("Submission sequence must be positive");
        return lastAppliedSequence == null || submittedSequence > lastAppliedSequence;
    }

    /**
     * Count frozen service membership, not submissions or identities. Additional manually
     * linked services do not enlarge the denominator; duplicate submissions do not inflate it.
     */
    public static long collected(Collection<Long> frozenServiceIds, Collection<Long> archivedServiceIds) {
        Set<Long> archived = new LinkedHashSet<>(archivedServiceIds);
        return new LinkedHashSet<>(frozenServiceIds).stream().filter(archived::contains).count();
    }

    public static boolean plannerComplete(Collection<Long> frozenServiceIds, Collection<Long> archivedServiceIds) {
        return collected(frozenServiceIds, archivedServiceIds) == new LinkedHashSet<>(frozenServiceIds).size();
    }

    /** Only selected fields replace current values; omitted fields are preserved. */
    public static <T> Map<String, T> merge(Map<String, T> current, Map<String, T> submitted,
                                          Set<String> collectedKeys) {
        Objects.requireNonNull(current);
        Objects.requireNonNull(submitted);
        Objects.requireNonNull(collectedKeys);
        if (!collectedKeys.containsAll(submitted.keySet()))
            throw new IllegalArgumentException("Submission contains fields outside this task");
        if (!submitted.keySet().containsAll(collectedKeys))
            throw new IllegalArgumentException("Submission omits a required task field");
        var result = new java.util.LinkedHashMap<>(current);
        submitted.forEach((key, value) -> result.put(key, Objects.requireNonNull(value)));
        return java.util.Collections.unmodifiableMap(result);
    }
}
