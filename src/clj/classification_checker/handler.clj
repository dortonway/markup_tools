(ns classification-checker.handler
  (:require [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found resources]]
            [hiccup.page :refer [include-js include-css html5]]
            [ring.util.response :refer [response]]
            [ring.util.http-response :refer [accepted created ok see-other forbidden]]
            [clojure.core.async :as async :refer [<!! put!]]
            [clj-time.core :as time]
            [clj-time.coerce :as tc]
            [classification_checker.example :as example]
            [classification_checker.middleware :refer [wrap-middleware]]
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

(defn app [in-ch out-ch]
  (defroutes routes
             (GET "/" [] (main-page))
             (GET "/paraphrase/current" {session :session} (if-login session main-page))
             (GET "/session/new" [] (main-page))
             (POST "/session/new" [& req] (login! (:user req)))
             (GET "/batch" {session :session} (if-login session #(response {:batch (->> (async/take 10 in-ch)
                                                                                        (async/into [])
                                                                                        (<!!)) })))
             (POST "/batch" {session :session body :params}
               (if-login session (fn [] (let [email (:email (:user session))
                                              batch-time (str (tc/to-long (time/now)))]
                                          (doseq [ex (->> (:batch body)
                                                          (map (fn [ex]
                                                                 (example/->ParaphraseExample (:utterance1 ex) (:utterance2 ex) (:is-same? ex) email batch-time))))]
                                            (put! out-ch ex)))
                                   (accepted))))

             (resources "/")
             (not-found "Not Found"))

  (wrap-middleware #'routes))

