package io.akka.sim.domain;

/** Events persisted for one workflow run — the durable record rule 7 requires. */
public sealed interface RunEvent {

  record RunStarted(String workflowName) implements RunEvent {}

  record BlockRecorded(BlockLog log) implements RunEvent {}

  record RunSucceeded(BlockOutput finalOutput) implements RunEvent {}

  record RunFailed(String error) implements RunEvent {}
}
