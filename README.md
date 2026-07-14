# cloud-itonami-isco-2165

Open Occupation Blueprint for **ISCO-08 2165**: Cartographers and Surveyors.

This repository designs a forkable OSS platform for a cartography/surveying support robot: a field-data collection and record-keeping assistant prepares survey records, GIS products, and boundary-discrepancy reports under a governor-gated actor, so the practice keeps its own field records and maintains professional control over licensed surveyor authority (legal survey certification, boundary determination sign-off, and licensed surveyor sign-off remain the professional surveyor's exclusive responsibility).

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a cartography-support robot records field survey data, drafts map/GIS products, schedules site visits, and flags boundary discrepancies under an actor that proposes actions and an independent **Cartography Governor** that gates them. The governor never
dispatches the surveyor's or surveying authority's binding authority; `:safety-critical` actions (such as
certifying a legal survey, issuing a licensed surveyor's sign-off, or binding a boundary determination) remain the professional licensed surveyor's exclusive responsibility and can only be proposed, never automated.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
site location + baseline property records + field measurements + GIS data
        |
        v
Cartography Advisor -> Cartography Governor -> survey record draft / map product, or human sign-off
        |
        v
robot actions (gated) + field records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or issue a surveyor's binding determination without governor approval and
audit evidence. No proposal can claim to issue a legal survey certification, licensed surveyor
sign-off, or binding boundary determination — those remain the professional licensed surveyor's exclusive
professional and legal responsibility.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `2165`). Required capabilities:

- :robotics
- :identity
- :survey-forms
- :gis
- :dmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Reference implementation (`:maturity :implemented`)

Full itonami Actor pattern (per ADR-2607011000 / CLAUDE.md's Actors
section, alongside `cloud-itonami-isco-2164` and other ISCO-08 implementations): a real
[`kotoba-lang/langgraph`](https://github.com/kotoba-lang/langgraph)
`StateGraph`, with the Advisor and Governor as distinct graph nodes and
human-in-the-loop interrupt/resume via checkpointing.

```text
:intake -> :advise -> :govern -> :decide -+-> :commit            (:ok? true)
                                          +-> :request-approval   (:escalate? true, interrupt-before)
                                          +-> :hold               (:hard? true)
```

- `src/cartography/store.cljc` — `Store` protocol + `MemStore`:
  registered sites/projects, committed survey records, an append-only audit ledger.
- `src/cartography/advisor.cljc` — `Advisor` protocol; `mock-advisor`
  (deterministic, default) proposes a field-survey or cartographic operation from a
  request; `llm-advisor` wraps a `langchain.model/ChatModel` — either
  way the advisor only ever produces a `:propose`-effect proposal,
  never a legal survey certification or licensed surveyor's sign-off, and LLM parse failures always yield
  `confidence 0.0` (forces escalation, never fabricated confidence).
- `src/cartography/governor.cljc` — `CartographyGovernor/check`: a pure
  function, wired as its own `:govern` node. Hard invariants
  (unregistered site, a proposal whose `:effect` isn't `:propose`,
  any attempt to certify a legal survey, issue a licensed surveyor's sign-off, or bind a boundary determination)
  always route to `:hold`. Escalation invariants (boundary-discrepancy flags
  or low advisor confidence) always route to
  `:request-approval` — an `interrupt-before` node that the graph
  checkpoints and only resumes on explicit human approval
  (`actor/approve!`), matching the README's robotics-premise statement
  that binding surveyor authority always remains the
  professional surveyor's sole responsibility.
- `src/cartography/actor.cljc` — `build-graph`, `run-request!`,
  `approve!`: the `langgraph.graph/state-graph` wiring itself.

## Supported operations

- `:draft-survey-record` — field-survey data recording proposal
- `:draft-map-product` — draft map/GIS product for the surveyor's review
- `:flag-boundary-discrepancy` — surface a boundary/property-line discrepancy (always escalates for human review)
- `:schedule-site-visit` — site-visit scheduling proposal

All proposals carry `:effect :propose` (enforcement: Governor.hard-violations).

```bash
clojure -M:test
```

This is what backs this repo's `:maturity :implemented` entry in
[`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation).

## License

AGPL-3.0-or-later.
