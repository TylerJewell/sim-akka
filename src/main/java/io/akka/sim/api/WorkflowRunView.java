package io.akka.sim.api;

import io.akka.sim.domain.BlockLog;
import io.akka.sim.domain.BlockOutput;
import io.akka.sim.domain.RunState;
import java.util.List;

/** The run's own surface — a run-monitoring read model, not an Akka View (SPEC-001 §4). */
public record WorkflowRunView(
    String runId,
    String workflowName,
    RunState.Status status,
    List<BlockLog> blockLogs,
    BlockOutput finalOutput,
    String error) {

  public static WorkflowRunView from(String runId, RunState state) {
    return new WorkflowRunView(
        runId, state.workflowName(), state.status(), state.blockLogs(), state.finalOutput(), state.error());
  }
}
