package io.akka.sim.domain;

import io.akka.sim.handlers.AgentCaller;
import io.akka.sim.handlers.HandlerRegistry;
import io.akka.sim.handlers.RunContext;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Drives a workflow definition's DAG to completion — SPEC-001 §3. Ready-queue driven: every
 * independently-ready node executes concurrently (rule 3, virtual threads — §4's documented
 * simplification of the source's async event loop), and a {@code response} block ends the run
 * immediately (rule 4).
 */
public final class ExecutionEngine {

  private record NodeResult(String nodeId, BlockLog log, BlockOutput output) {}

  public ExecutionResult run(WorkflowDefinition definition, AgentCaller agentCaller) {
    var dag = Dag.build(definition);
    var edgeManager = new EdgeManager(dag);
    var context = new RunContext(dag, agentCaller);
    var handlers = new HandlerRegistry();

    var readyQueue = new ArrayDeque<String>();
    readyQueue.add(dag.startNode().id);

    var blockLogs = new ArrayList<BlockLog>();
    var responseOutput = new BlockOutput[] {null};
    var responded = new boolean[] {false};
    var failure = new String[] {null};

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      CompletionService<NodeResult> completionService = new ExecutorCompletionService<>(executor);
      int inFlight = 0;

      while ((!readyQueue.isEmpty() || inFlight > 0) && failure[0] == null && !responded[0]) {
        while (!readyQueue.isEmpty()) {
          var nodeId = readyQueue.poll();
          var node = dag.nodes.get(nodeId);
          completionService.submit(() -> executeOne(context, handlers, node));
          inFlight++;
        }

        if (inFlight == 0) break;

        NodeResult result = take(completionService);
        inFlight--;
        blockLogs.add(result.log());

        if (result.output().hasError()) {
          failure[0] = result.output().error();
          break;
        }

        var node = dag.nodes.get(result.nodeId());
        context.recordOutput(result.nodeId(), result.output());

        if (node.blockType == BlockType.RESPONSE) {
          responseOutput[0] = result.output();
          responded[0] = true;
          break;
        }

        var newlyReady = edgeManager.processOutgoingEdges(node, result.output());
        readyQueue.addAll(newlyReady);
      }
    }

    if (failure[0] != null) {
      return new ExecutionResult(false, null, List.copyOf(blockLogs), failure[0]);
    }
    var finalOutput = responseOutput[0] != null ? responseOutput[0] : lastOutput(blockLogs);
    return new ExecutionResult(true, finalOutput, List.copyOf(blockLogs), null);
  }

  private static NodeResult take(CompletionService<NodeResult> completionService) {
    try {
      return completionService.take().get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("execution interrupted", e);
    } catch (java.util.concurrent.ExecutionException e) {
      throw new IllegalStateException("block execution failed unexpectedly", e.getCause());
    }
  }

  private static NodeResult executeOne(
      RunContext context, HandlerRegistry handlers, DagNode node) {
    var startedAt = Instant.now();
    BlockOutput output;
    try {
      output = handlers.forType(node.blockType).execute(context, node);
    } catch (Exception e) {
      output = BlockOutput.failed(e.getMessage() == null ? e.toString() : e.getMessage());
    }
    var endedAt = Instant.now();
    var status = output.hasError() ? BlockLog.Status.FAILED : BlockLog.Status.COMPLETED;
    var log =
        new BlockLog(
            node.id,
            status,
            startedAt,
            endedAt,
            endedAt.toEpochMilli() - startedAt.toEpochMilli(),
            output,
            output.error());
    return new NodeResult(node.id, log, output);
  }

  private static BlockOutput lastOutput(List<BlockLog> logs) {
    return logs.isEmpty() ? BlockOutput.of(java.util.Map.of()) : logs.get(logs.size() - 1).output();
  }
}
