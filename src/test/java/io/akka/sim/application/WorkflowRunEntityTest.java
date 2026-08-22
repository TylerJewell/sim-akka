package io.akka.sim.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.akka.sim.domain.BlockConfig;
import io.akka.sim.domain.BlockType;
import io.akka.sim.domain.DagEdge;
import io.akka.sim.domain.RunEvent;
import io.akka.sim.domain.RunState;
import io.akka.sim.domain.WorkflowDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What the domain tests cannot check: that starting a run through the entity persists rule 7's
 * per-block event sequence, and that a run is protected against being started twice.
 */
public class WorkflowRunEntityTest {

  private static EventSourcedTestKit<RunState, RunEvent, WorkflowRunEntity> run() {
    return EventSourcedTestKit.of("run-1", WorkflowRunEntity::new);
  }

  private static WorkflowDefinition twoBlockWorkflow() {
    return new WorkflowDefinition(
        "t",
        List.of(
            new WorkflowDefinition.BlockSpec("start", "Start", BlockType.START, new BlockConfig.Start()),
            new WorkflowDefinition.BlockSpec("done", "Done", BlockType.RESPONSE, new BlockConfig.Response())),
        List.of(DagEdge.of("start", "done")));
  }

  @Test
  public void startingARunPersistsOneEventPerBlockPlusStartAndOutcome() {
    var kit = run();
    var result =
        kit.method(WorkflowRunEntity::startRun).invoke(new WorkflowRunEntity.StartRun(twoBlockWorkflow()));

    assertThat(result.getAllEvents()).hasSize(4); // RunStarted, 2x BlockRecorded, RunSucceeded
    assertThat(result.getReply().status()).isEqualTo(RunState.Status.COMPLETED);
    assertThat(result.getReply().blockLogs()).hasSize(2);
  }

  @Test
  public void startingAnAlreadyStartedRunIsRefused() {
    var kit = run();
    kit.method(WorkflowRunEntity::startRun).invoke(new WorkflowRunEntity.StartRun(twoBlockWorkflow()));

    var second =
        kit.method(WorkflowRunEntity::startRun).invoke(new WorkflowRunEntity.StartRun(twoBlockWorkflow()));

    assertThat(second.isError()).isTrue();
    // The first run's state is untouched — no duplicate events appended.
    assertThat(kit.method(WorkflowRunEntity::get).invoke().getReply().blockLogs()).hasSize(2);
  }
}
