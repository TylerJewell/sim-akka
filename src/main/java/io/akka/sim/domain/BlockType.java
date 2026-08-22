package io.akka.sim.domain;

/** The block types this port executes — SPEC-001 §1. */
public enum BlockType {
  START,
  AGENT,
  CONDITION,
  ROUTER,
  RESPONSE
}
