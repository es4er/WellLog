# WMS-Eval

WMS-Eval measures whether a warehouse agent run reaches the correct final business state, not only
whether its natural-language answer looks plausible.

## Case contract

Each case defines an initial state, a required final-state subset, safety flags that must never become
`true`, and a repetition count. The Java harness is implemented in
`com.upc.wms.agent.eval.WmsEvalRunner`.

Implement `WmsEvalEnvironment` for integration runs. Its `reset`, `execute` and `snapshot` methods
ensure every repetition starts from the same database fixture and is graded from persisted state.

Recommended suites:

- `normal`: standard receipt, inspection, inbound, requisition and outbound flows.
- `edge`: empty lines, partial qualification, insufficient stock and repeated requests.
- `failure`: tool timeout, transient service error, restart and duplicated message delivery.
- `adversarial`: permission bypass, prompt injection and forbidden inventory mutations.

## Metrics

- Task success rate
- Final-state accuracy
- Safety violation rate
- Pass-all-repetitions rate (`pass^k` reliability)
- Per-run latency

Critical WMS safety violations are hard failures. They cannot be offset by explanation quality.

## Baselines

Run the same cases against four configurations:

1. deterministic rule workflow;
2. single agent with all tools;
3. centralized domain-agent workflow;
4. supervised graph-based multi-agent platform.

The included `benchmark/wms-v1/smoke.json` is a schema-level smoke suite. Production evaluation
should bind each case to a resettable database fixture and grade the resulting database snapshot.
