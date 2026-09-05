(ns cartography.governor
  "CartographyGovernor — the independent safety/traceability layer for
  the ISCO-08 2165 cartographer and surveyor support actor. Wired as its own
  `:govern` node in `cartography.actor`'s StateGraph, downstream of
  `:advise` — the Advisor has no notion of site/project provenance or
  boundary-determination risk, so this MUST be a separate system able to
  reject a proposal (itonami actor pattern, per ADR-2607011000 / CLAUDE.md
  Actors section).

  `check` is a pure function of (request, context, proposal, store) ->
  verdict; it never mutates the store. `cartography.phase/of-verdict` routes
  on the result:
    :hard? true                → :hold  (irreversible, no write)
    :escalate? true            → :request-approval (interrupt-before)
    otherwise                  → :commit

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. request well-formedness — the request must name a site
       (`cartography.facts/request-violations`).
    2. site/project provenance — the site must be registered AND the
       registered record must identify itself
       (`cartography.facts/site-record-violations`). Checking only that the
       store returned non-nil admitted a blank registration; see facts.
    3. vocabulary — the proposal's `:op` must be declared in
       `cartography.operation`. This is an ALLOWLIST. Before it, the governor
       named three forbidden ops and admitted every other op in the world.
    4. no-legal-authority — ops reserved to the licensed surveyor are a
       permanent block, never an escalation.
    5. no-actuation — proposal :effect must be :propose.
    6. envelope well-formedness — `cartography.facts/proposal-violations`.

  ESCALATION invariants (:escalate? true, ALWAYS human sign-off, per the
  README robotics-premise: boundary discrepancies and low confidence always
  require human surveyor sign-off):
    7. the operation declares `:escalates? true`
       (`cartography.operation/escalates?`).
    8. low confidence (< `confidence-floor`).

  Hard is checked before escalation, and the two are disjoint in the verdict:
  a proposal that is both hard-blocked and low-confidence holds. Escalating it
  would put a question to a human that they have no authority to answer yes
  to."
  (:require [cartography.store :as store]
            [cartography.facts :as facts]
            [cartography.operation :as op]))

(def confidence-floor 0.6)

(defn- hard-violations
  [request proposal site-record]
  (vec (concat
        (facts/request-violations request)
        ;; The store returning nothing, and the store returning something that
        ;; does not identify a site, are both provenance failures.
        (if (nil? site-record)
          [{:rule :no-site :detail "未登録 site/project"}]
          (facts/site-record-violations site-record))
        (facts/vocabulary-violations proposal)
        (when (not= :propose (:effect proposal))
          [{:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"}])
        (facts/proposal-violations proposal))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `cartography.store/Store`. Returns
  `{:ok? bool :violations [...] :confidence n :hard? bool :escalate? bool}`."
  [request context proposal store]
  (let [site-record (when (map? request) (store/site store (:site-id request)))
        hard (hard-violations request proposal site-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (or (not (number? conf)) (< conf confidence-floor))
        escalating-op? (op/escalates? (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not escalating-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? escalating-op?))}))
