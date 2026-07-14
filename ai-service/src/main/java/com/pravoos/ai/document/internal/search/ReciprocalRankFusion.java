package com.pravoos.ai.document.internal.search;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReciprocalRankFusion {

    private ReciprocalRankFusion() {}

    public static List<ChunkCandidate> fuse(List<RankedSource> sources, double rrfK, double legislationBoost) {
        if (rrfK <= 0) {
            throw new IllegalArgumentException("RRF constant k must be positive, got " + rrfK);
        }
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }

        Map<UUID, ChunkCandidate> candidatesById = new LinkedHashMap<>();
        Map<UUID, Double> fusedScores = new LinkedHashMap<>();

        for (RankedSource source : sources) {
            if (source.weight() == 0) {
                continue;
            }
            List<ChunkCandidate> candidates = source.candidates();
            for (int position = 0; position < candidates.size(); position++) {
                ChunkCandidate candidate = candidates.get(position);
                candidatesById.putIfAbsent(candidate.chunkId(), candidate);
                fusedScores.merge(
                        candidate.chunkId(),
                        source.weight() / (rrfK + position + 1),
                        Double::sum);
            }
        }

        return fusedScores.entrySet().stream()
                .map(entry -> {
                    ChunkCandidate candidate = candidatesById.get(entry.getKey());
                    double boost = candidate.legislation() ? legislationBoost : 0.0;
                    return candidate.withScore(entry.getValue() + boost);
                })
                .sorted(Comparator.comparingDouble(ChunkCandidate::score).reversed()
                        .thenComparing(ChunkCandidate::chunkId))
                .toList();
    }
}
