package io.akka.sim.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import io.akka.sim.domain.BlockConfig;
import io.akka.sim.domain.BlockType;
import io.akka.sim.domain.DagEdge;
import io.akka.sim.domain.RunState;
import io.akka.sim.domain.WorkflowDefinition;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The capability driven the way a caller outside a test would drive it: over HTTP, against a
 * started runtime. The domain tests (SPEC-001 §3) check the rules; this checks that something
 * outside a test can reach them at all, and that a run is readable after it settles (rule 7).
 */
public class RuntimeBackedWorkflowRunTest extends TestKitSupport {

  @Test
  public void aBranchingWorkflowIsRunAndMonitoredOverHttp() {
    String runId = "run-" + UUID.randomUUID();
    var definition =
        new WorkflowDefinition(
            "branching",
            List.of(
                new WorkflowDefinition.BlockSpec("start", "Start", BlockType.START, new BlockConfig.Start()),
                new WorkflowDefinition.BlockSpec(
                    "cond",
                    "Cond",
                    BlockType.CONDITION,
                    new BlockConfig.Condition(
                        List.of(
                            new BlockConfig.Condition.Branch(
                                "yes", "1", BlockConfig.Condition.Operator.EQUALS, "1")))),
                new WorkflowDefinition.BlockSpec(
                    "onYes", "OnYes", BlockType.AGENT, new BlockConfig.Agent("picked <Cond.matched>")),
                new WorkflowDefinition.BlockSpec("done", "Done", BlockType.RESPONSE, new BlockConfig.Response())),
            List.of(
                DagEdge.of("start", "cond"),
                DagEdge.handled("cond", "onYes", "condition-yes"),
                DagEdge.of("onYes", "done")));

    var started =
        httpClient
            .POST("/runs/" + runId)
            .withRequestBody(definition)
            .responseBodyAs(WorkflowRunView.class)
            .invoke()
            .body();

    assertThat(started.status()).isEqualTo(RunState.Status.COMPLETED);
    assertThat(started.blockLogs().stream().map(l -> l.blockId()).toList())
        .containsExactly("start", "cond", "onYes", "done");
    assertThat(started.blockLogs().get(2).output().fields().get("response")).isEqualTo("picked yes");

    var reread = httpClient.GET("/runs/" + runId).responseBodyAs(WorkflowRunView.class).invoke().body();
    assertThat(reread.status()).isEqualTo(RunState.Status.COMPLETED);
    assertThat(reread.blockLogs()).hasSize(4);
  }

  @Test
  public void aRunNeverStartedReportsNotStarted() {
    var view =
        httpClient
            .GET("/runs/never-started-" + UUID.randomUUID())
            .responseBodyAs(WorkflowRunView.class)
            .invoke()
            .body();
    assertThat(view.status()).isEqualTo(RunState.Status.NOT_STARTED);
  }
}
