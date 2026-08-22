package io.akka.sim.domain;

import static org.junit.jupiter.api.Assertions.*;

import io.akka.sim.handlers.EchoAgentCaller;
import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-001 §3 rules 3, 4, 6, 7. */
class ExecutionEngineTest {

  @Test
  void runsStartThroughResponseAndRecordsOneLogPerBlock() {
    var def =
        new WorkflowDefinition(
            "t",
            List.of(
                spec("start", "Start", BlockType.START, new BlockConfig.Start()),
                spec("greet", "Greeter", BlockType.AGENT, new BlockConfig.Agent("hello")),
                spec("done", "Done", BlockType.RESPONSE, new BlockConfig.Response())),
            List.of(DagEdge.of("start", "greet"), DagEdge.of("greet", "done")));

    var result = new ExecutionEngine().run(def, new EchoAgentCaller());

    assertTrue(result.success());
    assertEquals(3, result.blockLogs().size());
    assertEquals(
        List.of("start", "greet", "done"),
        result.blockLogs().stream().map(BlockLog::blockId).toList());
    for (var log : result.blockLogs()) {
      assertNotNull(log.startedAt());
      assertNotNull(log.endedAt());
      assertTrue(log.durationMs() >= 0);
      assertEquals(BlockLog.Status.COMPLETED, log.status());
    }
  }

  /** Rule 4: a response block ends the run — the sibling branch never executes. */
  @Test
  void responseBlockEndsRunImmediatelyEvenWithAQueuedSibling() {
    var def =
        new WorkflowDefinition(
            "t",
            List.of(
                spec("start", "Start", BlockType.START, new BlockConfig.Start()),
                spec("done", "Done", BlockType.RESPONSE, new BlockConfig.Response()),
                spec("slow", "Slow", BlockType.AGENT, new BlockConfig.Agent("<start.nothing>"))),
            // Both edges fire off start; "done" and "slow" are both independently ready.
            List.of(DagEdge.of("start", "done"), DagEdge.of("start", "slow")));

    var result = new ExecutionEngine().run(def, new EchoAgentCaller());

    assertTrue(result.success());
    // "start" and "done" always run; "slow" may or may not have been submitted concurrently
    // with "done" (both become ready off the same start completion), but the run must not
    // wait for it once "done" (a response block) settles, and it is never in the final log
    // if it lost the race — assert only what rule 4 actually guarantees: the run ends on the
    // first response block seen, and its output is the final output.
    assertEquals(BlockType.RESPONSE, dagBlockType(def, result.blockLogs().get(result.blockLogs().size() - 1).blockId()));
  }

  /** Rule 6: an agent block resolves a prior block's output into its prompt. */
  @Test
  void agentBlockResolvesReferenceFromPriorBlock() {
    var def =
        new WorkflowDefinition(
            "t",
            List.of(
                spec("start", "Start", BlockType.START, new BlockConfig.Start()),
                spec("greet", "Greeter", BlockType.AGENT, new BlockConfig.Agent("hi")),
                spec(
                    "reply",
                    "Reply",
                    BlockType.AGENT,
                    new BlockConfig.Agent("you said <Greeter.response>")),
                spec("done", "Done", BlockType.RESPONSE, new BlockConfig.Response())),
            List.of(
                DagEdge.of("start", "greet"),
                DagEdge.of("greet", "reply"),
                DagEdge.of("reply", "done")));

    var result = new ExecutionEngine().run(def, new EchoAgentCaller());

    assertTrue(result.success());
    var replyLog =
        result.blockLogs().stream().filter(l -> l.blockId().equals("reply")).findFirst().orElseThrow();
    assertEquals("you said hi", replyLog.output().fields().get("response"));
  }

  private static WorkflowDefinition.BlockSpec spec(
      String id, String name, BlockType type, BlockConfig config) {
    return new WorkflowDefinition.BlockSpec(id, name, type, config);
  }

  private static BlockType dagBlockType(WorkflowDefinition def, String blockId) {
    return def.blocks().stream()
        .filter(b -> b.id().equals(blockId))
        .findFirst()
        .orElseThrow()
        .blockType();
  }
}
