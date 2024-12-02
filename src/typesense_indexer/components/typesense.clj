(ns typesense-indexer.components.typesense
  (:require [com.stuartsierra.component :as component]
            [cheshire.core :as json]
            [org.httpkit.client :as http]
            [nostr.edufeed :as nostr]))

;; Typesense Client Configuration
(def typesense-url "http://localhost:8108")
(def typesense-api-key "xyz")

(def amb-schema {:name "amb"
                 :enable_nested_fields true
                 :fields [{:name "name" :type "string"}
                          {:name "author" :type "string" :optional true}
                          {:name "creator" :type "object[]" :optional true}
                          {:name "description" :type "string" :optional true}
                          {:name "keywords" :type "string[]" :optional true}
                          {:name "about.id" :type "string[]" :optional true}
                          {:name "about.prefLabel" :type "object[]" :optional true}
                          {:name "learningResourceType.id" :type "string[]" :optional true}
                          {:name "learningResourceType.prefLabel" :type "object[]" :optional true}]})

(defn insert-collection []
  (http/post (str typesense-url "/collections")
             {:headers {"X-TYPESENSE-API-KEY" typesense-api-key
                        "Content-Type" "application/json"}
              :body (json/generate-string amb-schema)}))

(defn check-for-schema []
  (http/get (str typesense-url "/collections")
            {:headers {"X-TYPESENSE-API-KEY" typesense-api-key
                       "Content-Type" "application/json"}}
            (fn [{:keys [status headers body error opts]}]
              (if error
                (println "Failed, exception is " error)
                (when-not (some #(= "amb" (:name %))  (json/parse-string body true))
                  (do (println "AMB not found...inserting collection")
                      (insert-collection)))))))

;; Utility function to insert a document into Typesense
(defn insert-to-typesense [collection event]
  (let [url (str typesense-url "/collections/" collection "/documents?dirty_values=drop&action=upsert")
        doc (nostr/convert-30142-to-nostr-amb event false)]
    (http/post url
               {:body (json/encode doc)
                :headers {"X-TYPESENSE-API-KEY" typesense-api-key
                          "Content-Type" "application/json"}}
               (fn [{:keys [status headers body error opts]}]
                 (if error
                   (println "Failed, error is: ")
                   (println "Status " status "ID:" (:id doc)))))))

(defrecord Typesense []
  component/Lifecycle
  (start [component]
    (println ";; Starting Typesense Connection")
    (assoc component :typesense typesense-url)
    (check-for-schema))

  (stop [component]
    (println ";; Stopping Typesense")
    (assoc component :typesense nil)))

(defn new-typesense-component []
  (map->Typesense {}))

