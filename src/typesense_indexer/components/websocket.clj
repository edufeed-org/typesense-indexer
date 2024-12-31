(ns typesense-indexer.components.websocket
  (:require [com.stuartsierra.component :as component]
            [typesense-indexer.components.typesense :as typesense]
            [clojure.core.async :as async :refer [chan go-loop alts! go >! <!! <! take!]]
            [nostr.core :as nostr]))

(defn init-request-30142 [ws last-event newest-event reason]
  (println "init request with reason" reason)
  (case reason
    "eose" (do
             (nostr/unsubscribe ws {:kinds [30142]
                                    :limit 6})
             (nostr/subscribe  ws  {:kinds [30142]
                                    :since (:created_at @newest-event)}))
    "reconnect" (nostr/subscribe ws  {:kinds [1]
                                      :limit 4
                                      :until (:created_at @last-event)})
    "init" (nostr/subscribe ws {:kinds [30142]
                                :limit 6})))

(defn on-message-handler [ws parsed last-parsed-event newest-event typesense]
  (try
    (let [event (nth parsed 2 parsed)]
      (when (and (= "EOSE" (first parsed))
                 (not= (:created_at @last-parsed-event) (:created_at @newest-event)))
        (init-request-30142 ws last-parsed-event newest-event "eose"))
      (when (> (get event :created_at 0) (get @newest-event :created_at 0))
        (reset! newest-event event))
      (when (= "EVENT" (first parsed))
        (do
          (typesense/insert-to-typesense "amb" event (:url typesense) (:api-key typesense))
          (reset! last-parsed-event event))))
    (catch Exception e
      (println "error parsing event" parsed
               "\n Error: "
               e))))

(defrecord WebsocketConnection [config
                                ws channel shutdown
                                typesense]
  component/Lifecycle
  (start [component]
    (println ";; Starting WebsocketConnection")
    (println ";; typesense" typesense)
    (let [last-parsed-event (atom {:created_at nil})
          newest-event (atom nil)]
      (try
        (let [{:keys [ws channel]} (nostr/connect-channel (:url config))]
          (init-request-30142 ws last-parsed-event newest-event "init")
          (go-loop []
            (when-some [message (<! channel)]
              (on-message-handler ws message last-parsed-event newest-event typesense)
              (recur)))

          (assoc component
                 :ws ws
                 :channel channel
                 :shutdown (promise)))
        (catch Exception e
          (println "Failed to start connection for " (:url config) "Error: " e))))) ;; 1006 means "abnormal closure"

  (stop [component]
    (println ";; Stopping WebsocketConnection for url" (:url config))
    (when-let [ws (:ws component)]
      ;; Signal any ongoing loops to stop
      (when-let [shutdown (:shutdown component)]
        (deliver shutdown true))
      (nostr/close! ws)) ;; Close the WebSocket connection
    (assoc component :ws nil :channel nil :shutdown nil)))

(defn new-websocket-connection [config]
  (component/using (map->WebsocketConnection {:config config }) [:typesense]))

