(ns typesense-indexer.components.websocket
  (:require [hato.websocket :as ws]
            [com.stuartsierra.component :as component]
            [cheshire.core :as json]
            [typesense-indexer.components.typesense :as typesense])
  (:import [java.nio CharBuffer]))

(defn init-request-30142 [wc last-event newest-event reason]
  (case reason
    "eose" (do
             (ws/send! wc (json/generate-string ["CLOSE" "RAND"]))
             (ws/send! wc (json/generate-string ["REQ" "RANDNEW" {:kinds [30142]
                                                                  :since (:created_at @newest-event)}])))
    "reconnect" (ws/send! wc (json/generate-string ["REQ" "RAND" {:kinds [1]
                                                                  :limit 4
                                                                  :until (:created_at @last-event)}]))
    "init" (ws/send! wc (json/generate-string ["REQ" "RAND" {:kinds [30142]
                                                             :limit 6000}]))))

(defn on-message-handler [last-parsed-event newest-event ws msg last?]
  (let [msg-str (if (instance? CharBuffer msg)
                  (str msg)
                  msg)
        parsed (json/parse-string msg-str true)
        event (nth parsed 2 nil)]
    (println (first parsed) (:id event))
    (when (and (= "EOSE" (first parsed))
               (not= (:created_at @last-parsed-event) (:created_at @newest-event)))
      (init-request-30142 ws last-parsed-event newest-event "eose"))
    (when (> (:created_at event) (or (:created_at @newest-event) 0))
      (reset! newest-event event))
    (when (= "EVENT" (first parsed))
      (do
        (typesense/insert-to-typesense "amb" event)
        (reset! last-parsed-event event)))))

(defn create-websocket [url on-close-handler last-parsed-event newest-event]
  @(ws/websocket url
                 {:on-open (fn [ws]
                             (println "Opened connection to url " url))
                  :on-message (fn [ws msg last?]
                                (on-message-handler last-parsed-event newest-event ws msg last?))
                  :on-close (fn [ws status reason]
                              ;; status 1000 für ws/close!, status 1006 bei connection lost
                              (println "closed connection. status: " status " reason " reason)
                              (on-close-handler status))}))

(defrecord WebsocketConnection [url connection]
  component/Lifecycle
  (start [component]
    (println ";; Starting WebsocketConnection")
    (let [last-parsed-event (atom {:created_at nil})
          newest-event (atom nil)
          reconnect (fn reconnect [status]
                      (println "Lost connection, attempting reconnect for: " url)
                      (when (= 1006 status)
                        (try
                          (Thread/sleep 3000)
                          (when-not (:connection component)
                            (let [wc (create-websocket url reconnect last-parsed-event newest-event)]
                              (assoc component :connection wc)
                              (init-request-30142 wc last-parsed-event newest-event "reconnect")))
                          (catch Exception e
                            (println "Reconnect failed for " url ":" (.getMessage e))
                            (reconnect status)))))]
      (try
        (let [wc (create-websocket url reconnect last-parsed-event newest-event)]
          (assoc component :connection wc)
          (init-request-30142 wc last-parsed-event newest-event "init"))

        (catch Exception e
          (println "Failed to start connection for " url)
          (reconnect 1006)))))

  (stop [component]
    (println ";; Stopping WebsocketConnection for url " url)
    (when-let [wc (:connection component)]
      (ws/close! wc))
    (assoc component :connection nil)))

(defn new-websocket-connection [url]
  (map->WebsocketConnection {:url url}))

