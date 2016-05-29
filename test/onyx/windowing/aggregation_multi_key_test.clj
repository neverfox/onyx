(ns onyx.windowing.aggregation-multi-key-test
  (:require [clojure.core.async :refer [chan >!! <!! close! sliding-buffer]]
            [clojure.test :refer [deftest is]]
            [onyx.plugin.core-async :refer [take-segments!]]
            [onyx.test-helper :refer [load-config with-test-env]]
            [onyx.api]))

(def input
  [{:id 1 :id-2 1 :age 21 :event-time #inst "2015-09-13T03:00:00.829-00:00"}
   {:id 1 :id-2 2 :age 12 :event-time #inst "2015-09-13T03:04:00.829-00:00"}
   {:id 2 :id-2 1 :age 3 :event-time #inst "2015-09-13T03:05:00.829-00:00"}
   {:id 2 :id-2 2 :age 64 :event-time #inst "2015-09-13T03:06:00.829-00:00"}
   {:id 3 :id-2 1 :age 53 :event-time #inst "2015-09-13T03:07:00.829-00:00"}
   {:id 3 :id-2 2 :age 52 :event-time #inst "2015-09-13T03:08:00.829-00:00"}
   {:id 4 :id-2 1 :age 24 :event-time #inst "2015-09-13T03:09:00.829-00:00"}
   {:id 4 :id-2 2 :age 35 :event-time #inst "2015-09-13T03:15:00.829-00:00"}
   {:id 5 :id-2 1 :age 49 :event-time #inst "2015-09-13T03:25:00.829-00:00"}
   {:id 5 :id-2 2 :age 37 :event-time #inst "2015-09-13T03:45:00.829-00:00"}
   {:id 6 :id-2 1 :age 15 :event-time #inst "2015-09-13T03:03:00.829-00:00"}
   {:id 6 :id-2 2 :age 22 :event-time #inst "2015-09-13T03:56:00.829-00:00"}
   {:id 7 :id-2 1 :age 83 :event-time #inst "2015-09-13T03:59:00.829-00:00"}
   {:id 7 :id-2 2 :age 60 :event-time #inst "2015-09-13T03:32:00.829-00:00"}
   {:id 8 :id-2 1 :age 35 :event-time #inst "2015-09-13T03:16:00.829-00:00"}])

(def expected-windows
  [[Double/NEGATIVE_INFINITY Double/POSITIVE_INFINITY
    {:n 15 :sum 565 :average 113/3}]])

(def test-state (atom []))

(defn update-atom! [event window trigger {:keys [lower-bound upper-bound event-type] :as opts} extent-state]
  (when-not (= :job-completed event-type)
    (swap! test-state conj [lower-bound upper-bound extent-state])))

(def in-chan (atom nil))

(def out-chan (atom nil))

(defn inject-in-ch [event lifecycle]
  {:core.async/chan @in-chan})

(defn inject-out-ch [event lifecycle]
  {:core.async/chan @out-chan})

(def in-calls
  {:lifecycle/before-task-start inject-in-ch})

(def out-calls
  {:lifecycle/before-task-start inject-out-ch})

(deftest average-test
  (let [id (java.util.UUID/randomUUID)
        config (load-config)
        env-config (assoc (:env-config config) :onyx/tenancy-id id)
        peer-config (assoc (:peer-config config) :onyx/tenancy-id id)
        batch-size 20
        workflow
        [[:in :identity] [:identity :out]]

        catalog
        [{:onyx/name :in
          :onyx/plugin :onyx.plugin.core-async/input
          :onyx/type :input
          :onyx/medium :core.async
          :onyx/batch-size batch-size
          :onyx/max-peers 1
          :onyx/doc "Reads segments from a core.async channel"}

         {:onyx/name :identity
          :onyx/fn :clojure.core/identity
          :onyx/type :function
          :onyx/uniqueness-key [:id :id-2]
          :onyx/max-peers 1
          :onyx/batch-size batch-size}

         {:onyx/name :out
          :onyx/plugin :onyx.plugin.core-async/output
          :onyx/type :output
          :onyx/medium :core.async
          :onyx/batch-size batch-size
          :onyx/max-peers 1
          :onyx/doc "Writes segments to a core.async channel"}]

        windows
        [{:window/id :collect-segments
          :window/task :identity
          :window/type :global
          :window/aggregation [:onyx.windowing.aggregation/average :age]
          :window/window-key :event-time}]

        triggers
        [{:trigger/window-id :collect-segments
          :trigger/refinement :onyx.refinements/accumulating
          :trigger/on :onyx.triggers/segment
          :trigger/threshold [15 :elements]
          :trigger/sync ::update-atom!}]

        lifecycles
        [{:lifecycle/task :in
          :lifecycle/calls ::in-calls}
         {:lifecycle/task :in
          :lifecycle/calls :onyx.plugin.core-async/reader-calls}
         {:lifecycle/task :out
          :lifecycle/calls ::out-calls}
         {:lifecycle/task :out
          :lifecycle/calls :onyx.plugin.core-async/writer-calls}]]

    (reset! in-chan (chan (inc (count input))))
    (reset! out-chan (chan (sliding-buffer (inc (count input)))))
    (reset! test-state [])

    (with-test-env [test-env [3 env-config peer-config]]
      (onyx.api/submit-job
       peer-config
       {:catalog catalog
        :workflow workflow
        :lifecycles lifecycles
        :windows windows
        :triggers triggers
        :task-scheduler :onyx.task-scheduler/balanced})
      
      (doseq [i input]
        (>!! @in-chan i))
      (>!! @in-chan :done)

      (close! @in-chan)

      (let [results (take-segments! @out-chan)]
        (is (= (into #{} input) (into #{} (butlast results))))
        (is (= :done (last results)))
        (is (= expected-windows @test-state))))))