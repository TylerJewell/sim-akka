# sim-akka

Runs a workflow of typed blocks — start, agent, condition, router, response — as a graph of
arrows: a block only runs once every arrow pointing into it has fired or been ruled out, a
block that picks one of several arrows rules out the others (and everything only those lead
to), and independently-ready blocks run at the same time rather than one after another. Every
block's result is recorded with its own start time, end time and outcome, so a run can be
read back block by block after it finishes.

A port of [simstudioai/sim](https://github.com/simstudioai/sim) onto **Akka**.

---

## Where it came from

simstudioai/sim is an open-source workspace for building and running AI agents — a visual
builder connects blocks into a graph, and a run drives that graph end to end. This port takes
one slice of it: the graph-running engine itself, and the record it leaves behind for
inspecting a run afterward. Everything else — the visual builder, the 1,000+ third-party
connectors, live LLM calls, and the workspace around Sim's own chat, tables and files — is a
different capability layered on top of the engine, not the engine.

## simstudioai/sim → this port

📉 196 TypeScript lines → **143 Java lines** (the shared graph-running rule; both counted the
same way — see `bench/REPORT.md`)<br>
🎯 3 of 3 → **3 of 3** graph scenarios giving the same answer, run against the real source and
the port side by side<br>
🧪 not measured → **24 of 24** tests, 3 of them deliberate breakages of the graph rule and every
one caught<br>
⚡ 0.018 → **0.042** milliseconds to decide which blocks are ready after a branching step (not
a fair speed comparison — see `bench/REPORT.md` for why)

Full method and the numbers that did *not* make this list:
[`bench/REPORT.md`](../sim-port/bench/REPORT.md).

---

## What it took to build

⏱️ **0.9 hours** from the first command to the published repository, **0.9** of them active<br>
💬 **442** exchanges with the model<br>
✍️ **265,253** tokens written by the model, **107,836,777** counting everything sent and re-sent<br>
🙋 **0** questions to a human<br>
🧪 **24** tests

```bash
python toolkit/tokens.py --port sim    # turns, tokens, elapsed and active time
```

The record of every question, and where the time went, is in
[`port-log/`](../port-log).

---

## What it does

From the specification (`sim-port/specs/SPEC-001-sim.md`):

- **A block runs once every arrow into it has been settled, not once they have all fired.**
  An arrow a block chose not to take still has to be ruled out before the block on the other
  end can start — otherwise a block waiting on two arrows would wait forever for the one that
  was never coming.
- **Ruling out an arrow can rule out everything past it.** If the block at the far end of a
  ruled-out arrow has no other way to be reached, every arrow leading on from it is ruled out
  too, all the way down the dead branch — unless that block already has a real way in from
  somewhere else, in which case the dead branch stops there instead of cutting off a block
  that is actually going to run.
- **Blocks that are both ready at once run at once**, not in the order they became ready.
- **A response block ends the run the moment it finishes.** Anything else still waiting is
  never started, however far along it was.
- **A condition block picks the arrow matching exactly what it decided**, never the first
  arrow, never one picked by position.
- **A block can read another block's result by name.** `<Greeter.response>` inside a later
  block's text is replaced with whatever the block named "Greeter" produced, matched
  case-and-space-insensitively.
- **Every block's run is written down as it happens** — which block, whether it succeeded,
  when it started, when it ended, and what it produced or what went wrong — so a finished
  run can be read back block by block, not just as a single pass/fail result.

---

## Design decisions

**A stand-in instead of a live model call.** Calling a real AI model needs an account, a key,
and a network connection, none of which belong in a graph-running rule. An agent block calls
an interface instead, and the one shipped here just repeats back what it was asked — so the
graph rules can be tested and run without any of that, and a real call can be plugged in later
without touching the graph engine at all.

**One structured comparison instead of a small programming language.** A condition block asks
a yes-or-no question about one thing at a time — is this equal to that, does it contain that,
is it bigger than that — instead of running a whole snippet of code someone wrote. Every arrow
rule the graph engine has to obey can be tested this way, without also having to build and
guard a place to safely run somebody else's code.

**A run's full story is written down all at once, at the end.** A run happens inside a single
step, and everything it did — every block it ran, in what order, with what result — is
recorded together the moment that step finishes, rather than being trickled out block by
block while it is still going. This makes a finished run simple to read back in full; it also
means nothing about a run is visible while it is still in progress. Getting a running list to
show up as it happens is a real, separate piece of engineering, on top of this one rather than
inside it.

---

## Running it — the developer path

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once

### Start the service

```bash
mvn compile
akka local run
```

The service starts on **port 9062**.

### Try it

```bash
# start a run
curl -s localhost:9062/runs/run-1 -X POST -H 'content-type: application/json' -d '{
  "name": "greeting",
  "blocks": [
    {"id": "start", "name": "Start", "blockType": "START", "config": {"type": "Start"}},
    {"id": "greet", "name": "Greeter", "blockType": "AGENT", "config": {"type": "Agent", "promptTemplate": "hello"}},
    {"id": "done", "name": "Done", "blockType": "RESPONSE", "config": {"type": "Response"}}
  ],
  "edges": [
    {"source": "start", "target": "greet", "sourceHandle": null},
    {"source": "greet", "target": "done", "sourceHandle": null}
  ]
}'

# read the run back afterward
curl -s localhost:9062/runs/run-1
```

---

## Configuration

| Variable | Default | Notes |
|---|---|---|
| none | — | The service reads no environment variables. The port it listens on is set in `src/main/resources/application.conf`. |

This port calls no model provider — the agent block's stand-in needs no key. See "Design
decisions" above for how to plug a real one in.

---

## Where it differs from simstudioai/sim

Everything not listed here behaves the same way on purpose, including the parts that look
like mistakes.

- **Loops and repeating a branch of the graph.** Sim can run a piece of the graph in a loop or
  split it to run several times in parallel, and both have their own extra rules for when the
  arrows around them count as settled. This port has neither — the arrow rules above only ever
  cover a graph that runs each block once, because a loop or a repeated branch is a separate
  capability sitting on top of the same arrow rules, not a different version of them.
- **What a condition block is allowed to ask.** Sim lets a condition block run a small snippet
  of code someone wrote. This port limits it to one structured comparison per branch, chosen
  because it is enough to exercise every arrow rule a condition block can trigger without also
  needing a safe place to run somebody else's code.
- **What an agent block actually does.** Sim's agent block calls a real AI model over the
  network. This port calls a stand-in that repeats back what it was asked, because a graph-
  running rule should not need an account, a key, or a network connection to be tested — see
  "Design decisions."
- **A router block choosing an arrow that does not exist.** Sim's routing can, in some cases,
  quietly treat this as a dead end the same way a condition's unchosen branch is treated. This
  port was given a rule rather than copying one — read but not run against a live router block,
  because doing so depends on the loop/parallel machinery this port does not have — and ends the
  run with an error instead.
- **Watching a run while it is still going.** Sim's own interface follows a run live, block by
  block, as it happens. This port answers with a run's full story once the run has finished and
  does not push updates out while it is in progress — see "Design decisions." **Not checked**
  against the original in the sense of comparing what a live view would show, since this port
  has no live view to compare.
- **What happens when the same run is started twice.** Sim's own behaviour for this exact case
  — starting the identical run a second time — was not checked. This port refuses the second
  start outright, because letting it through would mix two runs' block-by-block stories
  together under one name.

---

## Licence

simstudioai/sim is under the Apache License 2.0, © 2026 Sim Studio. This port reimplements
the behaviour without copied source; see `ACKNOWLEDGEMENTS.md`.
