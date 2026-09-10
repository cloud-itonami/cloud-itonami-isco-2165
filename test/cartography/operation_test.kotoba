(ns cartography.operation-test
  "The operation vocabulary is a closed set, and the two halves of it mean
  different things."
  (:require [clojure.test :refer [deftest is]]
            [clojure.set]
            [cartography.operation :as op]))

(deftest supported-and-reserved-are-disjoint
  ;; If an op ever appeared in both maps, `vocabulary-violations` would report
  ;; it as a reserved-authority breach while the governor's escalation logic
  ;; treated it as proposable. Keep them apart by assertion, not by care.
  (is (empty? (clojure.set/intersection
               (set (keys op/supported))
               (set (keys op/reserved))))))

(deftest declared-covers-both-halves
  (is (op/declared? :draft-survey-record))
  (is (op/declared? :certify-legal-survey))
  (is (not (op/declared? :bulldoze-the-boundary-marker))))

(deftest supported-does-not-admit-reserved-ops
  ;; The pre-change governor was a denylist; this is the assertion that the
  ;; allowlist and the denylist cannot drift into each other.
  (doseq [o (keys op/reserved)]
    (is (not (op/supported? o))
        (str o " must never become proposable"))))

(deftest escalation-is-a-property-of-the-operation
  (is (op/escalates? :flag-boundary-discrepancy))
  (is (not (op/escalates? :draft-survey-record)))
  (is (not (op/escalates? :draft-map-product)))
  (is (not (op/escalates? :schedule-site-visit))))

(deftest undeclared-op-does-not-escalate-into-existence
  ;; `escalates?` returning false for an unknown op must not be readable as
  ;; "this op is fine to run" -- the governor hard-blocks it first. Asserted
  ;; here so a future refactor cannot quietly make escalates? the only gate.
  (is (not (op/escalates? :bulldoze-the-boundary-marker)))
  (is (not (op/supported? :bulldoze-the-boundary-marker))))

(deftest every-reserved-op-explains-itself
  (doseq [o (keys op/reserved)]
    (is (seq (op/reserved-reason o))
        (str o " must carry a reason the refusal can quote"))))
