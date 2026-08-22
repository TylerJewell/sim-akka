package io.akka.sim.domain;

import java.util.List;

/** The outcome of running a workflow definition once, end to end — SPEC-001 §2 (`RunState`). */
public record ExecutionResult(
    boolean success, BlockOutput finalOutput, List<BlockLog> blockLogs, String error) {}
