(ns cartography.governor
  "CartographyGovernor — the independent safety/traceability layer for
  the ISCO-08 2165 cartographer and surveyor support actor. Wired as its own
  `:govern` node in `cartography.actor`'s StateGraph, downstream of
  `:advise` — the Advisor has no notion of site/project provenance or
  boundary-determination risk, so this MUST be a separate system able to
  reject a proposal (itonami actor pattern, per ADR-2607011000 / CLAUDE.md
  Actors section).

  `check` is a pure function of (request, context, proposal, store) ->
  verdict; it never mutates the store. The StateGraph's `:decide` node
  routes on the verdict:
    :hard? true                → :hold  (irreversible, no write)
    :escalate? true            → :request-approval (interrupt-before)
    otherwise                  → :commit

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. site/project provenance — the request's site/project must be registered.
    2. no-actuation         — proposal :effect must be :propose.
    3. no-legal-authority   — any attempt to certify a legal survey, issue
       a licensed surveyor's sign-off, or bind the surveying authority to
       a boundary determination is a permanent block (those remain the
       professional licensed surveyor's exclusive responsibility).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off, per the
  README robotics-premise: boundary discrepancies and low confidence always
  require human surveyor sign-off):
    4. :op :flag-boundary-discrepancy (always escalates).
    5. low confidence (< `confidence-floor`)."
  (:require [cartography.store :as store]))

(def confidence-floor 0.6)
(def ^:private escalating-ops #{:flag-boundary-discrepancy})

(defn- hard-violations [{:keys [proposal]} site-record]
  (cond-> []
    (nil? site-record)
    (conj {:rule :no-site :detail "未登録 site/project"})

    (not= :propose (:effect proposal))
    (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

    (or (= :certify-legal-survey (:op proposal))
        (= :issue-surveyor-sign-off (:op proposal))
        (= :bind-boundary-determination (:op proposal)))
    (conj {:rule :no-legal-authority :detail "legal survey certification, licensed surveyor sign-off, and binding boundary determinations are the professional surveyor's exclusive responsibility"})))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `cartography.store/Store`. Returns
  `{:ok? bool :violations [...] :confidence n :hard? bool :escalate? bool}`."
  [request context proposal store]
  (let [site-record (store/site store (:site-id request))
        hard (hard-violations {:proposal proposal} site-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        escalating-op? (contains? escalating-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not escalating-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? escalating-op?))}))
