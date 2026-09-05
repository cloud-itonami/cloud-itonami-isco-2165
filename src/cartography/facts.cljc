(ns cartography.facts
  "Well-formedness of the values the ISCO-08 2165 cartography actor governs:
  the site record, the request, and the proposal envelope.

  Runtime: portable `.cljc` (pure predicates, no host interop).

  Why this namespace exists. The Governor's site-provenance invariant was
  written as `(nil? site-record)` — it asked whether the store returned
  something, not whether that something identified a site. Measured on the
  pre-change tree: registering the empty map put a record under the key `nil`,
  after which a request carrying no `:site-id` resolved to it and
  `governor/check` returned `{:ok? true :violations []}`. The provenance
  invariant was defeated by a blank registration.

  `nil?` is a fact about the store's return value. Provenance is a fact about
  the record. Those are different questions, and the second one needs a place
  to live.

  Every function here returns a vector of `{:rule .. :detail ..}` maps —
  empty means well-formed — so violations compose with the Governor's own
  rules without a second shape."
  (:require [clojure.string :as str]
            [cartography.operation :as op]))

(defn- blank?
  "True for nil, non-strings, and strings that are empty or all whitespace.
  Identifiers that are not strings are as unusable as absent ones."
  [v]
  (or (nil? v)
      (not (string? v))
      (empty? (str/trim v))))

(defn site-record-violations
  "A registered site must identify itself. A record without a usable
  `:site-id` cannot establish provenance for anything committed against it,
  so the governor must not treat its mere existence as provenance."
  [site-record]
  (cond-> []
    (not (map? site-record))
    (conj {:rule :site-not-a-record
           :detail "site record must be a map"})

    (and (map? site-record) (blank? (:site-id site-record)))
    (conj {:rule :site-without-id
           :detail "registered site record has no usable :site-id"})))

(defn request-violations
  "A request must name the site it is about."
  [request]
  (cond-> []
    (not (map? request))
    (conj {:rule :request-not-a-map :detail "request must be a map"})

    (and (map? request) (blank? (:site-id request)))
    (conj {:rule :request-without-site-id
           :detail "request has no usable :site-id"})))

(defn proposal-violations
  "The proposal envelope. Payload contents are the domain's business; the
  envelope is the governor's, because routing decisions are read off it.

  `:confidence` is checked because the escalation invariant is a numeric
  comparison against it — a proposal whose confidence is absent, non-numeric,
  or outside [0,1] would make that comparison answer a question it was not
  asked. A missing confidence already reads as 0.0 in the governor and thus
  escalates, which is correct and left alone; a *present but unusable* one is
  the defect."
  [proposal]
  (cond-> []
    (not (map? proposal))
    (conj {:rule :proposal-not-a-map :detail "proposal must be a map"})

    (and (map? proposal) (not (keyword? (:op proposal))))
    (conj {:rule :proposal-without-op :detail "proposal :op must be a keyword"})

    (and (map? proposal)
         (contains? proposal :confidence)
         (not (and (number? (:confidence proposal))
                   (<= 0 (:confidence proposal) 1))))
    (conj {:rule :confidence-out-of-range
           :detail "proposal :confidence must be a number in [0.0, 1.0]"})

    (and (map? proposal)
         (contains? proposal :payload)
         (not (map? (:payload proposal))))
    (conj {:rule :payload-not-a-map
           :detail "proposal :payload, when present, must be a map"})))

(defn vocabulary-violations
  "The operation must be one this repo declared. Undeclared and reserved are
  reported as different rules on purpose — see `cartography.operation`."
  [proposal]
  (let [o (:op proposal)]
    (cond
      (op/reserved? o)
      [{:rule :no-legal-authority :detail (op/reserved-reason o)}]

      (and (keyword? o) (not (op/supported? o)))
      [{:rule :undeclared-operation
        :detail (str "operation " o " is not in cartography.operation/supported")}]

      :else [])))
