package io.akka.sim.domain;

import java.time.Instant;

/** One block's execution record — the run-monitoring grain, SPEC-001 §2, rule 7. */
public record BlockLog(
    String blockId,
    Status status,
    Instant startedAt,
    Instant endedAt,
    long durationMs,
    BlockOutput output,
    String error) {

  public enum Status {
    COMPLETED,
    FAILED
  }
}
