# Baseline configurations

Keep model, tool implementation, database fixtures and evaluation cases fixed. Change only the
coordination strategy. Record model/version, temperature, prompt version and tool version with every
report so results remain reproducible.

| Baseline | Coordination |
|---|---|
| rule-workflow | Existing deterministic `nextAgent` chain |
| single-agent | One agent can invoke every allowed tool |
| centralized-multi-agent | Orchestrator delegates to role agents |
| supervised-multi-agent | DAG execution with messages, Supervisor and Verifier |
