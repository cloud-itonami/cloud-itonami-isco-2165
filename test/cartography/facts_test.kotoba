(ns cartography.facts-test
  "Well-formedness checks. Each of these fires on a value the pre-change tree
  admitted."
  (:require [clojure.test :refer [deftest is]]
            [cartography.facts :as facts]))

(defn- rules [vs] (set (map :rule vs)))

(deftest well-formed-site-passes
  (is (empty? (facts/site-record-violations
               {:site-id "site-1" :location "downtown"}))))

(deftest blank-site-registration-is-a-violation
  ;; Measured on the pre-change tree: registering {} put a record under the
  ;; key nil, and a request carrying no :site-id then resolved to it, so the
  ;; governor's `(nil? site-record)` provenance check never fired.
  (is (contains? (rules (facts/site-record-violations {})) :site-without-id))
  (is (contains? (rules (facts/site-record-violations {:site-id "   "})) :site-without-id))
  (is (contains? (rules (facts/site-record-violations {:site-id ""})) :site-without-id)))

(deftest non-string-site-id-is-a-violation
  ;; A keyword or number reads as "present" to a nil check but cannot be
  ;; matched against the request's string id.
  (is (contains? (rules (facts/site-record-violations {:site-id :site-1})) :site-without-id))
  (is (contains? (rules (facts/site-record-violations {:site-id 1})) :site-without-id)))

(deftest non-map-site-record-is-a-violation
  (is (contains? (rules (facts/site-record-violations "site-1")) :site-not-a-record)))

(deftest request-must-name-a-site
  (is (empty? (facts/request-violations {:site-id "site-1"})))
  (is (contains? (rules (facts/request-violations {})) :request-without-site-id))
  (is (contains? (rules (facts/request-violations {:site-id nil})) :request-without-site-id))
  (is (contains? (rules (facts/request-violations nil)) :request-not-a-map)))

(deftest proposal-envelope-checks
  (is (empty? (facts/proposal-violations
               {:op :draft-survey-record :effect :propose :confidence 0.9})))
  (is (contains? (rules (facts/proposal-violations {:effect :propose}))
                 :proposal-without-op))
  (is (contains? (rules (facts/proposal-violations
                         {:op :draft-survey-record :confidence 1.5}))
                 :confidence-out-of-range))
  (is (contains? (rules (facts/proposal-violations
                         {:op :draft-survey-record :confidence -0.1}))
                 :confidence-out-of-range))
  (is (contains? (rules (facts/proposal-violations
                         {:op :draft-survey-record :confidence "high"}))
                 :confidence-out-of-range)))

(deftest absent-confidence-is-not-a-violation
  ;; A missing confidence already reads as 0.0 in the governor and therefore
  ;; escalates, which is the safe direction. Only a *present but unusable*
  ;; value is the defect -- asserted so a future tightening does not turn the
  ;; safe case into a hard block by accident.
  (is (empty? (facts/proposal-violations {:op :draft-survey-record :effect :propose}))))

(deftest nil-payload-is-a-violation
  ;; Measured on the pre-change tree: {:payload nil} returned :ok? true.
  (is (contains? (rules (facts/proposal-violations
                         {:op :draft-survey-record :payload nil}))
                 :payload-not-a-map)))

(deftest vocabulary-classifies-undeclared-and-reserved-differently
  (is (empty? (facts/vocabulary-violations {:op :draft-survey-record})))
  (is (= #{:undeclared-operation}
         (rules (facts/vocabulary-violations {:op :bulldoze-the-boundary-marker}))))
  (is (= #{:no-legal-authority}
         (rules (facts/vocabulary-violations {:op :certify-legal-survey}))))
  ;; A reserved op must NOT also be reported as undeclared: the two refusals
  ;; mean different things and a caller reading only the first rule would be
  ;; told a vocabulary error where an authority boundary was crossed.
  (is (not (contains? (rules (facts/vocabulary-violations {:op :certify-legal-survey}))
                      :undeclared-operation))))
