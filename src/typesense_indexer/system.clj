(ns typesense-indexer.system
  (:require [com.stuartsierra.component :as component]
            [typesense-indexer.components.websocket :as ws]
            [typesense-indexer.components.typesense :as typesense]))

(defn new-system-map []
  (component/system-map
   :typesense (typesense/new-typesense-component)
   :websocket (component/using  (ws/new-websocket-connection "ws://localhost:7778")
                                [:typesense])))

(defn new-dependency-map [] {})

(defn new-system
  "Create the production system"
  []
  (println ";; Setting up new system")
  (-> (new-system-map)
      (component/system-using (new-dependency-map))))


