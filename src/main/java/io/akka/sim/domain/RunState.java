package io.akka.sim.domain;

import java.util.ArrayList;
import java.util.List;

/** A run's durable state, folded from its {@link RunEvent} stream — SPEC-001 §2. */
public record RunState(
    String workflowName, Status status, List<BlockLog> blockLogs, BlockOutput finalOutput, String error) {

  public enum Status {
    NOT_STARTED,
    RUNNING,
    COMPLETED,
    FAILED
  }

  public static RunState empty() {
    return new RunState(null, Status.NOT_STARTED, List.of(), null, null);
  }

  public RunState apply(RunEvent event) {
    return switch (event) {
      case RunEvent.RunStarted started -> new RunState(started.workflowName(), Status.RUNNING, List.of(), null, null);
      case RunEvent.BlockRecorded recorded -> {
        var logs = new ArrayList<>(blockLogs);
        logs.add(recorded.log());
        yield new RunState(workflowName, status, List.copyOf(logs), finalOutput, error);
      }
      case RunEvent.RunSucceeded succeeded ->
          new RunState(workflowName, Status.COMPLETED, blockLogs, succeeded.finalOutput(), null);
      case RunEvent.RunFailed failed -> new RunState(workflowName, Status.FAILED, blockLogs, null, failed.error());
    };
  }
}
