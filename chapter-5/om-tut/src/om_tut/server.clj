(ns om-tut.server
  (:require [bidi.bidi :as bidi]
            [bidi.ring]
            [bidi.schema]
            [schema.core :as s]
            [ring.adapter.jetty :as jetty]
            [cognitect.transit :as transit])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

; user=> (require '[om-tut.server :as server])

(defn to-transit [x]
  (let [baos (ByteArrayOutputStream. 4096)
        writer (transit/writer baos :json {})]
    (transit/write writer x)
    (.toString baos))) (defn from-transit [s]
                         (let [bais (ByteArrayInputStream. (.getBytes s "UTF-8"))
                               reader (transit/reader bais :json)]
                           (transit/read reader)))

(defn with-cors [resp]
  (-> resp
      (update-in [:headers]
                 merge {"Access-Control-Allow-Origin" "*"
                        "Access-Control-Allow-Methods" "GET, POST, OPTIONS"
                        "Access-Control-Allow-Headers" "Content-Type"
                        "Access-Control-Max-Age" "31536000"})))

(defn transit-response [resp]
  {:pre [(map? resp)]} (-> resp
                           (update-in [:headers "Content-Type"] #(or %
                                                                     "application/transit+json; charset=UTF-8")) (update-in
                                                                                                                  [:status] #(or % 200)) (update-in [:body] (fn [body]
                                                                                                                                                              (str (to-transit body)))) (with-cors)))

;; Data
(defonce todos (atom {}))
(defonce ids (atom 0))
(defn next-id []
  (swap! ids inc))

(defn get-todo [{:keys [route-params] :as req}]
  (if-let [t (get @todos (:id route-params))]
    (transit-response {:body t})
    {:status 404}))

(defn create-todo [req]
  (let [id (next-id)
        t (slurp (:body req))
        t (from-transit t)
        t (assoc t :id id)]
    (println "create:" t)
    (swap! todos assoc id t)
    (with-cors (transit-response {:body id}))))
(defn list-todos [{:as req}]
  (with-cors (transit-response {:body (vec (vals @todos))})))
(defn options [req]
  (with-cors {:status 200}))

(def routes ["/" {"todos" {:get list-todos
                           :post create-todo
                           :options options
                           ["/" [#"\d+" :id]] {:get get-todo}}
                  "" {:options options}}])

(defn handler [req]
  (let [resp ((bidi.ring/make-handler routes) req)]
    (println (:request-method req) (:uri req) "->" (:status resp)) resp))

(defn start-server []
  (jetty/run-jetty #'handler {:port 8080
                              :host "0.0.0.0"
                              :join? false}))
