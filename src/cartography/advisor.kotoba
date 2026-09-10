(ns cartography.advisor
  "CartographyAdvisor — the field-data collection and record-keeping
  LLM/advisor for ISCO-08 2165 cartographer and surveyor support operations.
  Accepts field survey requests and generates data-recording, map-product,
  and site-visit proposals. The Governor enforces that all proposals
  have :effect :propose and never attempt legal surveyor authority.

  Protocol: the advisor receives a request (containing :site-id and :op for
  the data-collection task) and generates a proposal. The Governor
  downstream can reject the proposal if it violates invariants (no site
  registration, wrong :effect, etc.).

  Proposal structure for survey-data operations:
  - :op :draft-survey-record — field-survey data recording proposal
  - :op :draft-map-product — draft map/GIS product for the surveyor's review
  - :op :flag-boundary-discrepancy — surface a boundary/property-line discrepancy (escalates)
  - :op :schedule-site-visit — site-visit scheduling proposal

  All proposals carry :effect :propose (enforcement: Governor.hard-violations)."
  (:require [cartography.store :as store]))

(defprotocol Advisor
  (-advise [this store request]
    "Generate a proposal from a field survey request.
    Returns {:op .. :effect :propose :confidence n :payload ..}."))

(defn mock-advisor
  "Simple mock advisor for testing. Generates basic survey proposals."
  []
  (reify Advisor
    (-advise [this store request]
      (let [site-id (:site-id request)
            op (or (:op request) :draft-survey-record)
            confidence (case op
                         :flag-boundary-discrepancy 0.5  ; always escalates (low conf + escalating-op)
                         :schedule-site-visit 0.8
                         :draft-map-product 0.75
                         :draft-survey-record 0.7
                         0.5)]
        {:op op
         :effect :propose
         :site-id site-id
         :confidence confidence
         :payload {:site-id site-id
                   :op op
                   :timestamp #?(:clj (System/currentTimeMillis) :cljs (.getTime (js/Date.)))
                   :data-source :field-survey}}))))
