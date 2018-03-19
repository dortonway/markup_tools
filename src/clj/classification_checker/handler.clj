(ns classification-checker.handler
  (:require [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found resources]]
            [hiccup.page :refer [include-js include-css html5]]
            [ring.util.response :refer [response]]
            [ring.util.http-response :refer [accepted created ok see-other forbidden]]
            [ring.middleware.json :refer [wrap-json-response]]
            [classification_checker.middleware :refer [wrap-middleware]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.json :refer [wrap-json-params]]
            [clojure.data.csv :as csv]
            [clojure.core.async :refer [go-loop chan put!]]
            [classification_checker.example :as example]
            [config.core :refer [env]]))

(use 'ring.middleware.session.cookie)

(def mount-target [:div#app])

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css (if (env :dev) "https://cdnjs.cloudflare.com/ajax/libs/antd/3.2.3/antd.css" "https://cdnjs.cloudflare.com/ajax/libs/antd/3.2.3/antd.min.css"))
   (include-css (if (env :dev) "/css/site.css" "/css/site.min.css"))])

(defn main-page []
  (html5
    (head)
    [:body
     mount-target
     (include-js "/js/app.js")]))











(defn if-login [session ok-response]
  (if (and (contains? session :user) (some? ((:user session) :email))) (ok-response) (forbidden)))

(defn login! [user]
  (-> (see-other "/paraphrase/current")
      (assoc-in [:session :user] user)))

(defn csv-data->maps [csv-data]
  (map zipmap
       (->> (first csv-data) ;; First row is the header
            (map keyword) ;; Drop if you want string keys instead
            repeat)
       (rest csv-data)))

(def batch-size 2)
(def flush-timeout 1000)

(def input-queue (atom '[]))
(def output-queue (atom '[]))

(go-loop []
  (do
    (Thread/sleep flush-timeout)
    (if (> (count @output-queue) batch-size)
      (do
        (with-open [writer (io/writer "checked.csv" :append true)]
          (csv/write-csv writer (map vals @output-queue)))
        (reset! output-queue '[])
        ))
    (recur)))

(with-open [reader (io/reader "input.csv")]
  (reset! input-queue (map (fn [ex]
                             (example/paraphrase-example {:utterance1 (:question ex) :utterance2 (:class ex) }))
                           (apply vector (csv-data->maps (csv/read-csv reader))))))

(defn next-batch []
  (defn take-rand [n coll]
    (take n (shuffle coll)))
  (take-rand batch-size @input-queue))






(defonce examples (chan))

(defroutes routes
  (GET "/" [] (main-page))
  (GET "/paraphrase/current" {session :session} (if-login session main-page))
  (GET "/session/new" [] (main-page))
  (POST "/session/new" [& req] (login! (:user req)))
  (GET "/batch" {session :session} (if-login session #(response {:batch (next-batch)} )))
  (POST "/batch" {session :session body :params}
    (if-login session #(do
                         (swap! output-queue concat (map (partial conj {:assessor (:email (:user session))}) (:batch body)))
                         (accepted))))

  (resources "/")
  (not-found "Not Found"))

(def app [reader writer]
  (->> (csv/read-csv reader)
       (first)
       (put! examples)

       (map #(rest (butlast %)))
       (csv/write-csv writer))

  (wrap-middleware #'routes))

