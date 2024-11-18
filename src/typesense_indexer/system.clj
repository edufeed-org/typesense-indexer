(ns typesense-indexer.system
  (:require [com.stuartsierra.component :as component]
            [typesense-indexer.components.websocket :as ws]
            [typesense-indexer.components.typesense :as typesense]))

(defn system []
  (component/system-map
   :typesense (typesense/new-typesense-component)
   :websocket (component/using  (ws/new-websocket-connection "ws://localhost:7778")
                                [:typesense])))
