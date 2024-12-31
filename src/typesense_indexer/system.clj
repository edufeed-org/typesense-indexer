(ns typesense-indexer.system
  (:require [com.stuartsierra.component :as component]
            [typesense-indexer.config :as config]
            [typesense-indexer.components.websocket :as ws]
            [typesense-indexer.components.typesense :as typesense]))

(defn new-system-map [config]
  (component/system-map
   :typesense (typesense/new-typesense-component (:typesense config))
   :websocket   (ws/new-websocket-connection (:websocket config))))

(defn configure [system]
  (let [config (config/read-config)]
    (merge-with merge system config)))

(defn new-dependency-map [] {})

(defn -main []
  (let [system (-> (config/read-config)
                   (new-system-map)
                   (component/start-system))
        shutdown (-> system :websocket :shutdown)]
    ;; Add the shutdown hook before blocking
    (.addShutdownHook
     (Runtime/getRuntime)
     (Thread.
      #(do
         (println ";; Shutdown hook triggered")
         (component/stop-system system)
         (println ";; System stopped"))))

    ;; Block on the shutdown promise
    (println "System running. Press Ctrl+C to stop.")
    @shutdown ;; Blocks until the shutdown promise is delivered
    (println ";; Exiting application...")))
