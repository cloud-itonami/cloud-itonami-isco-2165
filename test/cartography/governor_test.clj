(ns cartography.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [cartography.store :as store]
            [cartography.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-site! st {:site-id "site-1" :location "downtown" :project "survey-2165"})
    st))

(deftest ok-on-clean-survey-draft
  (let [st (fresh-store)
        proposal {:op :draft-survey-record :effect :propose :confidence 0.9}
        v (governor/check {:site-id "site-1"} {} proposal st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest hard-on-unregistered-site
  (let [st (fresh-store)
        proposal {:op :draft-survey-record :effect :propose :confidence 0.9}
        v (governor/check {:site-id "no-such-site"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-site (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        proposal {:op :draft-survey-record :effect :act :confidence 0.9}
        v (governor/check {:site-id "site-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-attempt-to-certify-legal-survey
  (let [st (fresh-store)
        proposal {:op :certify-legal-survey :effect :propose :confidence 0.9}
        v (governor/check {:site-id "site-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-legal-authority (:rule %)) (:violations v)))))

(deftest hard-on-attempt-to-issue-surveyor-sign-off
  (let [st (fresh-store)
        proposal {:op :issue-surveyor-sign-off :effect :propose :confidence 0.9}
        v (governor/check {:site-id "site-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-legal-authority (:rule %)) (:violations v)))))

(deftest hard-on-attempt-to-bind-boundary-determination
  (let [st (fresh-store)
        proposal {:op :bind-boundary-determination :effect :propose :confidence 0.9}
        v (governor/check {:site-id "site-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-legal-authority (:rule %)) (:violations v)))))

(deftest escalates-on-boundary-discrepancy-flag
  (let [st (fresh-store)
        proposal {:op :flag-boundary-discrepancy :effect :propose :confidence 0.9}
        v (governor/check {:site-id "site-1"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest escalates-on-low-confidence
  (let [st (fresh-store)
        proposal {:op :draft-map-product :effect :propose :confidence 0.4}
        v (governor/check {:site-id "site-1"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest store-records-and-ledger-append-only
  (let [st (fresh-store)]
    (store/commit-record! st {:site-id "site-1" :op :draft-survey-record})
    (store/append-ledger! st {:disposition :commit})
    (is (= 1 (count (store/records-of st "site-1"))))
    (is (= 1 (count (store/ledger st))))))
