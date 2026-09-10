(ns cartography.phase-test
  "Routing. Extracted from the actor's `:decide` node so it can be tested
  without building a graph."
  (:require [clojure.test :refer [deftest is]]
            [cartography.phase :as phase]))

(deftest clean-verdict-commits
  (is (= :commit (phase/of-verdict {:ok? true :hard? false :escalate? false}))))

(deftest escalating-verdict-requests-approval
  (is (= :request-approval (phase/of-verdict {:hard? false :escalate? true}))))

(deftest hard-verdict-holds
  (is (= :hold (phase/of-verdict {:hard? true :escalate? false}))))

(deftest hard-beats-escalate
  ;; The ordering IS the safety claim. If escalation were checked first, a
  ;; hard-blocked low-confidence proposal would be put to a human for
  ;; approval -- asking them to authorise something no one may authorise.
  (is (= :hold (phase/of-verdict {:hard? true :escalate? true}))))

(deftest only-commit-writes
  (is (phase/writes? :commit))
  (is (not (phase/writes? :hold)))
  (is (not (phase/writes? :request-approval))))

(deftest both-non-writing-phases-are-refusals
  ;; :request-approval is a refusal to act without a human, not an
  ;; approval-in-waiting. cartography.sim counts on this distinction.
  (is (phase/refusal? :hold))
  (is (phase/refusal? :request-approval))
  (is (not (phase/refusal? :commit))))

(deftest approval-provenance-is-read-off-the-disposition
  (is (phase/approved-commit? :request-approval))
  (is (not (phase/approved-commit? :commit)))
  (is (not (phase/approved-commit? nil))))

(deftest every-phase-of-verdict-can-return-is-described
  ;; A phase the router can produce but the table does not describe would make
  ;; writes?/refusal? answer nil-as-false for it -- an unknown phase would
  ;; read as "does not write", which is the safe direction only by luck.
  (doseq [v [{:hard? true} {:escalate? true} {:ok? true}]]
    (is (contains? phase/phases (phase/of-verdict v)))))
