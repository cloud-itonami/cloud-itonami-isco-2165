(ns cartography.actor-test
  "End-to-end through the wired StateGraph. Each of these closes a hole that
  was measured open on the pre-change tree -- the pure `governor/check` tests
  live in governor_test; these assert the *graph* behaves the same way, which
  is a separate claim."
  (:require [clojure.test :refer [deftest is]]
            [cartography.actor :as actor]
            [cartography.ledger :as led]
            [cartography.store :as store]))

(defn- fresh []
  (let [s (store/mem-store)]
    (store/register-site! s {:site-id "s" :location "downtown" :project "p"})
    [s (actor/build-graph {:store s})]))

(deftest actor-builds
  (let [[_ graph] (fresh)]
    (is (some? graph))))

(deftest clean-request-commits-and-is-attributed-to-the-actor
  (let [[s g] (fresh)
        r (actor/run-request! g {:site-id "s" :op :draft-survey-record} {} "t1")]
    (is (= :done (:status r)))
    (is (= 1 (count (store/records-of s "s"))))
    (let [e (last (store/ledger s))]
      (is (= :commit (:disposition e)))
      (is (= :actor (:approved-by e))))))

(deftest undeclared-operation-is-held-by-the-graph
  ;; Measured on the pre-change tree: :bulldoze-the-boundary-marker with
  ;; :effect :propose, a registered site and confidence 0.95 returned
  ;; {:ok? true :violations []} and would have been written.
  (let [[s g] (fresh)
        r (actor/run-request! g {:site-id "s" :op :bulldoze-the-boundary-marker} {} "t2")]
    (is (= :done (:status r)))
    (is (zero? (count (store/records-of s "s"))))
    (let [e (last (store/ledger s))]
      (is (= :hold (:disposition e)))
      (is (some #(= :undeclared-operation (:rule %))
                (get-in e [:verdict :violations]))))))

(defn- violations-of [s] (get-in (last (store/ledger s)) [:verdict :violations]))

(deftest request-without-a-site-is-held-for-that-reason
  ;; Measured on the pre-change tree: registering {} put a record under the
  ;; key nil, so a request naming no site resolved to it and passed the
  ;; provenance invariant. It is held now -- and the assertion names WHICH
  ;; rule held it, because a hold reached for some other reason would
  ;; otherwise be counted as this invariant working.
  (let [s (store/mem-store)
        _ (store/register-site! s {})
        g (actor/build-graph {:store s})
        r (actor/run-request! g {} {} "t3")]
    (is (= :done (:status r)))
    (is (= :hold (:disposition (last (store/ledger s)))))
    (is (some #(= :request-without-site-id (:rule %)) (violations-of s)))))

(deftest a-store-returning-a-blank-record-does-not-establish-provenance
  ;; The site-record half of provenance, which the test above does NOT
  ;; exercise: there the request is malformed and is refused before the store
  ;; is ever consulted. Measured 2026-09-06 by neutering the :site-without-id
  ;; rule -- the test above stayed green, so it was attesting to a rule it
  ;; never reached.
  ;;
  ;; `Store` is a protocol, so "the store returned something" is not the same
  ;; claim as "that something identifies a site". A stub is the honest way to
  ;; reach the second claim: mem-store keys records by their own :site-id and
  ;; so can never disagree with itself, but another implementation can.
  (let [blank-store (reify store/Store
                      (site [_ _] {})
                      (register-site! [_ _] nil)
                      (commit-record! [_ _] nil)
                      (append-ledger! [_ _] nil)
                      (records-of [_ _] [])
                      (ledger [_] []))
        captured (atom [])
        recording (reify store/Store
                    (site [_ id] (store/site blank-store id))
                    (register-site! [_ _] nil)
                    (commit-record! [_ r] (swap! captured conj r))
                    (append-ledger! [_ e] (swap! captured conj e))
                    (records-of [_ _] [])
                    (ledger [_] @captured))
        g (actor/build-graph {:store recording})
        r (actor/run-request! g {:site-id "s"} {} "t3b")]
    (is (= :done (:status r)))
    (is (= :hold (:disposition (last @captured))))
    (is (some #(= :site-without-id (:rule %))
              (get-in (last @captured) [:verdict :violations])))))

(deftest unregistered-site-is-held-for-that-reason
  (let [[s g] (fresh)
        r (actor/run-request! g {:site-id "no-such-site" :op :draft-survey-record} {} "t3c")]
    (is (= :done (:status r)))
    (is (= :hold (:disposition (last (store/ledger s)))))
    (is (some #(= :no-site (:rule %)) (violations-of s)))))

(deftest reserved-operation-is-held-as-an-authority-breach-not-a-typo
  ;; The refusal must name the authority boundary, not the vocabulary: an
  ;; operator reading :undeclared-operation would think someone misspelled an
  ;; op, when what happened is that the actor reached for the licensed
  ;; surveyor's authority.
  (let [[s g] (fresh)
        r (actor/run-request! g {:site-id "s" :op :issue-surveyor-sign-off} {} "t3d")]
    (is (= :done (:status r)))
    (is (some #(= :no-legal-authority (:rule %)) (violations-of s)))
    (is (not (some #(= :undeclared-operation (:rule %)) (violations-of s))))))

(deftest escalated-request-interrupts-without-writing
  (let [[s g] (fresh)
        r (actor/run-request! g {:site-id "s" :op :flag-boundary-discrepancy} {} "t4")]
    (is (= :interrupted (:status r)))
    (is (zero? (count (store/records-of s "s"))))
    (is (zero? (count (store/ledger s))))))

(deftest human-approved-commit-is-distinguishable-in-the-ledger
  ;; Measured on the pre-change tree: an automatic commit and one reached
  ;; through actor/approve! produced byte-identical {:disposition :commit ..}
  ;; entries, so an audit could not show the README's robotics premise had
  ;; been honoured on any given write.
  (let [[s g] (fresh)
        _ (actor/run-request! g {:site-id "s" :op :draft-survey-record} {} "auto")
        _ (actor/run-request! g {:site-id "s" :op :flag-boundary-discrepancy} {} "esc")
        _ (actor/approve! g "esc")
        entries (store/ledger s)
        by (mapv :approved-by entries)]
    (is (= 2 (count entries)))
    (is (= [:actor :human] by))
    (is (not= (dissoc (nth entries 0) :ledger/seq :ledger/prev :ledger/hash :record)
              (dissoc (nth entries 1) :ledger/seq :ledger/prev :ledger/hash :record)))))

(deftest the-ledger-a-run-leaves-behind-verifies
  (let [[s g] (fresh)]
    (actor/run-request! g {:site-id "s" :op :draft-survey-record} {} "v1")
    (actor/run-request! g {:site-id "s" :op :certify-legal-survey} {} "v2")
    (let [v (led/verify (store/ledger s))]
      (is (:ok? v))
      (is (= 2 (:length v))))))
