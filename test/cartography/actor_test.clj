(ns cartography.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [cartography.actor :as actor]
            [cartography.store :as store]))

(deftest actor-builds
  (let [s (store/mem-store)
        graph (actor/build-graph {:store s})]
    (is (some? graph))))
