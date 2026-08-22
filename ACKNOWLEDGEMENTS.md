# Acknowledgements

This project is a port of **[simstudioai/sim](https://github.com/simstudioai/sim)**.

## Licence and copyright

- simstudioai/sim is licensed under the **Apache License 2.0**. Copyright 2026 Sim Studio
  (`sim-src/LICENSE`, `sim-src/NOTICE`).
- **Nothing was copied verbatim.** Every Java file under `sim-akka/src` was written fresh
  against behaviour read out of, and run against, the TypeScript source; no source text or
  comments were transcribed. Two exceptions, both deliberate and named: the edge-handle prefix
  strings (`condition-`, `router-`) and the `BlockOutput` field names (`selectedOption`,
  `selectedRoute`) are the source's own protocol vocabulary, shared because SPEC-001's rule 5
  requires this port to match it exactly — that is the deterministic contract being ported, not
  copied prose. `ReferenceResolverTest` reuses the exact worked example
  (`"Trigger.dev 1" → "triggerdev1"`) from `packages/workflow-types/src/workflow.ts`'s own doc
  comment for `normalizeWorkflowBlockName`, as the grounding case for question-log row 4.
- **Behaviour is derived throughout**, plainly: the DAG readiness rule, the dead-branch-cascade
  rule, and the `<name.path>` reference-resolution rule are a direct port of the decision
  procedures in `apps/sim/executor/execution/edge-manager.ts` and
  `apps/sim/executor/variables/resolver.ts`. This is the nature of a port and is not something
  to obscure. Where the port deliberately simplifies or diverges — the `condition` block's
  expression language, the `agent` block's provider call, run monitoring's transport — see
  `README.md`'s "Where it differs from sim" section.
- Because no Apache-2.0 text was copied into this repository, nothing here is bound by
  simstudioai/sim's licence terms — the "copied material carries its licence with it" rule does
  not trigger, since nothing was copied. `LICENSE-sim` carries the original licence text for
  reference and attribution only.

## Also used

- [Akka](https://akka.io) — the SDK and runtime this port is built on.
