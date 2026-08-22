package io.akka.sim.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.akka.sim.domain.ExecutionEngine;
import io.akka.sim.domain.RunEvent;
import io.akka.sim.domain.RunState;
import io.akka.sim.domain.WorkflowDefinition;
import io.akka.sim.handlers.EchoAgentCaller;
import java.util.ArrayList;
import java.util.List;

/**
 * One workflow run — SPEC-001. The entity id is the run id. {@link ExecutionEngine} owns every
 * rule about how the DAG executes and is tested without a runtime (see {@code io.akka.sim.domain}
 * tests); this class runs it once per {@link StartRun} command and persists the resulting
 * per-block trace as its own event stream (rule 7).
 */
@Component(id = "workflow-run")
public class WorkflowRunEntity extends EventSourcedEntity<RunState, RunEvent> {

  public WorkflowRunEntity(EventSourcedEntityContext context) {}

  @Override
  public RunState emptyState() {
    return RunState.empty();
  }

  @Override
  public RunState applyEvent(RunEvent event) {
    return currentState().apply(event);
  }

  public record StartRun(WorkflowDefinition definition) {}

  /**
   * Runs the workflow definition to completion (SPEC-001 §3) and persists the whole event
   * sequence at once — spec §4's documented simplification: a run's monitoring detail becomes
   * visible once this command's effect settles, not incrementally while it runs.
   */
  public Effect<RunState> startRun(StartRun command) {
    if (currentState().status() != RunState.Status.NOT_STARTED) {
      return effects().error("run " + commandContext().entityId() + " already started");
    }

    var result = new ExecutionEngine().run(command.definition(), new EchoAgentCaller());

    var events = new ArrayList<RunEvent>();
    events.add(new RunEvent.RunStarted(command.definition().name()));
    for (var log : result.blockLogs()) {
      events.add(new RunEvent.BlockRecorded(log));
    }
    if (result.success()) {
      events.add(new RunEvent.RunSucceeded(result.finalOutput()));
    } else {
      events.add(new RunEvent.RunFailed(result.error()));
    }

    return effects().persistAll(List.copyOf(events)).thenReply(updated -> updated);
  }

  public ReadOnlyEffect<RunState> get() {
    return effects().reply(currentState());
  }
}
