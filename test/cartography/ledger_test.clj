(ns cartography.ledger-test
  "The chain, and the limits of what it can show."
  (:require [clojure.test :refer [deftest is]]
            [cartography.ledger :as led]
            [cartography.store :as store]))

(defn- three []
  (-> []
      (led/append {:disposition :commit :note "a"})
      (led/append {:disposition :hold :note "b"})
      (led/append {:disposition :commit :note "c"})))

(deftest append-stamps-sequence-and-links
  (let [l (three)]
    (is (= [0 1 2] (mapv :ledger/seq l)))
    (is (= 0 (:ledger/prev (first l))))
    (is (= (:ledger/hash (nth l 0)) (:ledger/prev (nth l 1))))
    (is (= (:ledger/hash (nth l 1)) (:ledger/prev (nth l 2))))))

(deftest intact-chain-verifies
  (is (:ok? (led/verify (three))))
  (is (= 3 (:length (led/verify (three)))))
  ;; An empty ledger is intact, not broken -- otherwise a fresh store would
  ;; report tampering.
  (is (:ok? (led/verify []))))

(deftest edited-entry-breaks-the-chain
  ;; This is the property the pre-change ledger did not have: the vector was
  ;; append-only by code path, so an edited entry was indistinguishable from
  ;; the real one.
  (let [l (three)
        tampered (assoc-in l [1 :note] "edited")
        v (led/verify tampered)]
    (is (not (:ok? v)))
    (is (= 1 (:broken-at v)))
    (is (= :hash-mismatch (:reason v)))))

(deftest reordered-entries-break-the-chain
  (let [l (three)
        swapped [(nth l 0) (nth l 2) (nth l 1)]
        v (led/verify swapped)]
    (is (not (:ok? v)))
    (is (= :seq-mismatch (:reason v)))))

(deftest entry-removed-from-the-middle-breaks-the-chain
  (let [l (three)
        v (led/verify [(nth l 0) (nth l 2)])]
    (is (not (:ok? v)))
    (is (= :seq-mismatch (:reason v)))))

(deftest relinked-entry-still-breaks-on-hash
  ;; Fixing up :ledger/seq and :ledger/prev by hand is the obvious forgery;
  ;; the hash is what stops it.
  (let [l (three)
        forged (assoc (nth l 1) :note "edited")
        v (led/verify [(nth l 0) forged (nth l 2)])]
    (is (not (:ok? v)))
    (is (= :hash-mismatch (:reason v)))))

(deftest truncation-is-not-detected-and-the-docstring-says-so
  ;; A chain cannot detect entries it never saw. This test exists so the limit
  ;; is recorded as a known one rather than discovered later as a surprise --
  ;; detecting truncation needs a signed head, which this in-memory store has
  ;; no way to hold.
  (let [l (three)]
    (is (:ok? (led/verify (subvec l 0 2))))))

(deftest chain-hash-is-deterministic
  (is (= (led/chain-hash 0 {:a 1}) (led/chain-hash 0 {:a 1})))
  (is (not= (led/chain-hash 0 {:a 1}) (led/chain-hash 0 {:a 2})))
  ;; Position matters: the same content at a different point in the chain
  ;; must hash differently, or a replayed entry would verify.
  (is (not= (led/chain-hash 0 {:a 1}) (led/chain-hash 7 {:a 1}))))

(deftest commit-entry-records-who-approved
  ;; Measured on the pre-change tree: an automatic commit and a
  ;; human-approved one produced identical ledger entries.
  (is (= :human (:approved-by (led/commit-entry {:op :x} :human))))
  (is (= :actor (:approved-by (led/commit-entry {:op :x} :actor))))
  ;; Both shapes carry the key, so an absent field can never be mistaken for
  ;; an unaudited write.
  (is (contains? (led/commit-entry {:op :x} :actor) :approved-by))
  (is (= :none (:approved-by (led/hold-entry {:hard? true})))))

(deftest store-chains-every-append
  (let [st (store/mem-store)]
    (store/append-ledger! st {:disposition :commit})
    (store/append-ledger! st {:disposition :hold})
    (let [l (store/ledger st)]
      (is (= 2 (count l)))
      (is (:ok? (led/verify l)))
      (is (every? :ledger/hash l)))))
