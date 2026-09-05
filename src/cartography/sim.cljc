(ns cartography.sim
  "Deterministic governed-scenario harness for the ISCO-08 2165 cartography
  actor: run a table of requests through the real StateGraph and report which
  ones the governor refused.

  Runtime: `run` and `report` are portable `.cljc`. `-main` is `:clj`-only,
  because process exit codes are a host concern; the `:cljs` branch throws
  rather than pretending to exit.

  Why this namespace exists, and why it fails loudly. A governed actor's
  claim is not that it acts — it is that there exist actions it refuses. A
  harness that ran only clean scenarios would print green while demonstrating
  nothing, which is the shape this workspace has repeatedly caught: a check
  that could not fail returning the same value as a check that passed.

  So `run` counts refusals, and `-main` exits non-zero when the count is zero.
  A scenario table that has stopped exercising the governor is a defect in the
  table, and it is reported as one rather than as a pass.

  The three questions this harness answers that a unit test does not:
    * does the *wired graph* refuse, or only the pure `check` function
    * does an escalated request actually interrupt rather than write
    * does the ledger it leaves behind verify, and does it record who
      approved each write"
  (:require [cartography.actor :as actor]
            [cartography.ledger :as led]
            [cartography.phase :as phase]
            [cartography.store :as store]))

(def registered-site
  {:site-id "sim-site-1" :location "downtown" :project "survey-2165"})

(def scenarios
  "Each scenario names the phase it must reach. `:expect` is asserted, not
  merely printed — a scenario whose actual phase differs is a mismatch and
  fails the run, so this table is a specification and not a log."
  [{:name :clean-survey-record
    :request {:site-id "sim-site-1" :op :draft-survey-record}
    :expect :commit
    :why "declared op, registered site, confidence above floor"}

   {:name :clean-map-product
    :request {:site-id "sim-site-1" :op :draft-map-product}
    :expect :commit
    :why "declared op, confidence above floor"}

   {:name :unregistered-site
    :request {:site-id "no-such-site" :op :draft-survey-record}
    :expect :hold
    :why "provenance: the site was never registered"}

   {:name :request-without-site
    :request {:op :draft-survey-record}
    :expect :hold
    :why "provenance: the request names no site"}

   {:name :undeclared-operation
    :request {:site-id "sim-site-1" :op :bulldoze-the-boundary-marker}
    :expect :hold
    :why "vocabulary: op is not in cartography.operation/supported"}

   {:name :reserved-certify-legal-survey
    :request {:site-id "sim-site-1" :op :certify-legal-survey}
    :expect :hold
    :why "authority: legal survey certification is the licensed surveyor's"}

   {:name :reserved-surveyor-sign-off
    :request {:site-id "sim-site-1" :op :issue-surveyor-sign-off}
    :expect :hold
    :why "authority: a sign-off cannot be issued by an actor"}

   {:name :reserved-bind-boundary
    :request {:site-id "sim-site-1" :op :bind-boundary-determination}
    :expect :hold
    :why "authority: binding a boundary determination is the surveyor's"}

   {:name :boundary-discrepancy-escalates
    :request {:site-id "sim-site-1" :op :flag-boundary-discrepancy}
    :expect :request-approval
    :why "the operation itself always requires human sign-off"}])

(defn- run-one [scenario]
  (let [st (store/mem-store)
        _ (store/register-site! st registered-site)
        graph (actor/build-graph {:store st})
        thread (str "sim-" (name (:name scenario)))
        result (actor/run-request! graph (:request scenario) {} thread)
        state (:state result)
        actual (or (:disposition state)
                   ;; A run that never reached :decide produced no phase at
                   ;; all; report that rather than defaulting it to a phase,
                   ;; which would make an unrun scenario look like a verdict.
                   :no-phase)]
    {:name (:name scenario)
     :expect (:expect scenario)
     :actual actual
     :why (:why scenario)
     :status (:status result)
     :match? (= actual (:expect scenario))
     :refusal? (and (not= actual :no-phase) (phase/refusal? actual))
     :wrote? (pos? (count (store/records-of st (:site-id (:request scenario)))))
     :ledger-verify (led/verify (store/ledger st))}))

(defn run
  "Run every scenario. Returns
  `{:results [..] :refusals n :mismatches [..] :ledger-breaks [..] :ok? bool}`.

  `:ok?` requires all three: every scenario reached its expected phase, at
  least one refusal was demonstrated, and every ledger left behind verifies."
  []
  (let [results (mapv run-one scenarios)
        refusals (count (filter :refusal? results))
        mismatches (filterv (complement :match?) results)
        ;; A refusal that still wrote a record is the worst outcome available
        ;; and would otherwise hide inside a matching phase.
        wrote-anyway (filterv #(and (:refusal? %) (:wrote? %)) results)
        ledger-breaks (filterv #(not (:ok? (:ledger-verify %))) results)]
    {:results results
     :refusals refusals
     :mismatches mismatches
     :wrote-anyway wrote-anyway
     :ledger-breaks ledger-breaks
     :ok? (and (empty? mismatches)
               (empty? wrote-anyway)
               (empty? ledger-breaks)
               (pos? refusals))}))

(defn report
  "Human-readable run report. Pure: takes the result of `run`."
  [{:keys [results refusals mismatches wrote-anyway ledger-breaks ok?]}]
  (str
   "cartography.sim — governed scenario run\n"
   (apply str
          (for [r results]
            (str "  " (if (:match? r) "ok  " "BAD ")
                 (name (:name r))
                 " expect=" (name (:expect r))
                 " actual=" (name (:actual r))
                 (when (:refusal? r) " [refused]")
                 "\n")))
   "  scenarios=" (count results)
   " refusals=" refusals
   " mismatches=" (count mismatches)
   " wrote-anyway=" (count wrote-anyway)
   " ledger-breaks=" (count ledger-breaks)
   "\n"
   (cond
     (zero? refusals)
     "  REFUSING TO REPORT A PASS: the scenario table demonstrated no refusal.\n"
     ok? "  PASS\n"
     :else "  FAIL\n")))

#?(:clj
   (defn -main [& _]
     (let [r (run)]
       (print (report r))
       (flush)
       (System/exit (if (:ok? r) 0 1))))
   :cljs
   (defn -main [& _]
     (throw (ex-info "cartography.sim/-main is :clj-only (process exit codes are a host concern); call `run` and inspect the result instead" {}))))
