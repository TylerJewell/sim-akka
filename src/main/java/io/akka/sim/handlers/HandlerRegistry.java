package io.akka.sim.handlers;

import io.akka.sim.domain.BlockType;
import java.util.Map;

/** The fixed set of block handlers this port ships — SPEC-001 §1. */
public final class HandlerRegistry {
  private static final Map<BlockType, BlockHandler> HANDLERS =
      Map.of(
          BlockType.START, new StartBlockHandler(),
          BlockType.AGENT, new AgentBlockHandler(),
          BlockType.CONDITION, new ConditionBlockHandler(),
          BlockType.ROUTER, new RouterBlockHandler(),
          BlockType.RESPONSE, new ResponseBlockHandler());

  public BlockHandler forType(BlockType type) {
    var handler = HANDLERS.get(type);
    if (handler == null) {
      throw new IllegalStateException("no handler registered for block type " + type);
    }
    return handler;
  }
}
