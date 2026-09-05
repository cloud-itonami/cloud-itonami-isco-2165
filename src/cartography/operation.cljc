(ns cartography.operation
  "The closed vocabulary of operations the ISCO-08 2165 cartography actor may
  propose.

  Runtime: portable `.cljc` (pure data + pure predicates, no host interop).

  Why this namespace exists. Before it, the operation vocabulary lived in
  three places that could not disagree loudly: the README's prose list, the
  Advisor's `case` over confidences, and the Governor's private
  `escalating-ops` set plus a three-item list of forbidden ops. That made the
  Governor a *denylist* — it blocked three named ops and admitted everything
  else. Measured on the pre-change tree: a proposal carrying
  `:op :bulldoze-the-boundary-marker :effect :propose :confidence 0.95`
  against a registered site returned `{:ok? true :violations []}`.

  An actor whose operation set is open cannot be governed, because the
  governor is answering a question about a vocabulary nobody declared. So the
  vocabulary is declared here, once, as an allowlist, and
  `cartography.governor` refuses anything outside it.

  Two disjoint maps:

  * `supported`  — what the actor may propose. `:escalates?` is a property of
    the operation, not of the governor's mood, so it lives beside it.
  * `reserved`   — operations that name authority belonging exclusively to the
    licensed surveyor. These are *declared* rather than merely absent so the
    refusal can say why: an undeclared op is a vocabulary error, a reserved op
    is a professional-authority boundary. Conflating them would let a future
    edit `supported`-list one of them by accident."
  (:refer-clojure :exclude [supported]))

(def supported
  "Operations the actor may propose. Value carries the operation's own
  properties; `:escalates?` true means human sign-off is required regardless
  of advisor confidence."
  {:draft-survey-record
   {:escalates? false
    :summary "field-survey data recording proposal"}

   :draft-map-product
   {:escalates? false
    :summary "draft map/GIS product for the surveyor's review"}

   :flag-boundary-discrepancy
   {:escalates? true
    :summary "surface a boundary/property-line discrepancy"}

   :schedule-site-visit
   {:escalates? false
    :summary "site-visit scheduling proposal"}})

(def reserved
  "Operations reserved to the licensed surveyor. Naming one in a proposal is a
  permanent hard block, never an escalation: escalation would imply a human
  could approve the actor doing it, and no human can delegate this."
  {:certify-legal-survey
   {:reason "legal survey certification is the licensed surveyor's exclusive responsibility"}

   :issue-surveyor-sign-off
   {:reason "a licensed surveyor's sign-off cannot be issued by an actor"}

   :bind-boundary-determination
   {:reason "binding a boundary determination is the licensed surveyor's exclusive responsibility"}})

(defn supported? [op] (contains? supported op))
(defn reserved? [op] (contains? reserved op))

(defn declared?
  "True if `op` is named anywhere in this vocabulary. An op that is neither
  supported nor reserved is undeclared — the governor refuses it."
  [op]
  (or (supported? op) (reserved? op)))

(defn escalates?
  "True if the operation itself always requires human sign-off. Unsupported
  ops are never reached by this predicate (the governor hard-blocks first),
  so a false here is not an admission."
  [op]
  (boolean (get-in supported [op :escalates?])))

(defn reserved-reason [op] (get-in reserved [op :reason]))
