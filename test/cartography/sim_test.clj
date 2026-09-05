(ns cartography.sim-test
  "The harness, and the control that shows the harness can fail."
  (:require [clojure.test :refer [deftest is]]
            [cartography.sim :as sim]))

(deftest scenario-run-passes
  (let [r (sim/run)]
    (is (:ok? r))
    (is (empty? (:mismatches r)))
    (is (empty? (:wrote-anyway r)))
    (is (empty? (:ledger-breaks r)))))

(deftest run-demonstrates-refusals
  ;; The whole point of the harness. A governed actor's claim is that there
  ;; exist actions it refuses; a run with zero refusals has shown nothing.
  (is (pos? (:refusals (sim/run)))))

(deftest a-table-that-refuses-nothing-fails
  ;; The control. Without this, `scenario-run-passes` would keep passing after
  ;; someone deleted every refusing scenario -- the harness would print green
  ;; while demonstrating nothing, which is the exact shape it exists to catch.
  (with-redefs [sim/scenarios [{:name :clean-only
                                :request {:site-id "sim-site-1"
                                          :op :draft-survey-record}
                                :expect :commit
                                :why "no refusal in this table"}]]
    (let [r (sim/run)]
      (is (zero? (:refusals r)))
      (is (not (:ok? r)))
      (is (re-find #"REFUSING TO REPORT A PASS" (sim/report r))))))

(deftest a-wrong-expectation-fails
  ;; The second control: the table is a specification, so a scenario whose
  ;; actual phase differs must fail rather than be logged.
  (with-redefs [sim/scenarios [{:name :mislabelled
                                :request {:site-id "no-such-site"
                                          :op :draft-survey-record}
                                :expect :commit
                                :why "this actually holds"}]]
    (let [r (sim/run)]
      (is (not (:ok? r)))
      (is (= 1 (count (:mismatches r)))))))

(deftest every-scenario-names-a-phase-the-router-can-produce
  (is (every? #{:commit :hold :request-approval} (map :expect sim/scenarios))))

(deftest report-is-pure-and-mentions-every-scenario
  (let [r (sim/run)
        out (sim/report r)]
    (doseq [s sim/scenarios]
      (is (re-find (re-pattern (name (:name s))) out)))))
