(ns typesense-indexer.core
  (:require
   [typesense-indexer.system :as system]
   [com.stuartsierra.component :as component]
   ))

(defn -main []
  (component/start (system/new-system))
  )


(comment
  (component/stop (system/new-system))
  )
