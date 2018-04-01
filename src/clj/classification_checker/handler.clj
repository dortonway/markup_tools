(ns classification-checker.handler
    (:require [compojure.core :refer [GET POST defroutes]]
      [compojure.route :refer [not-found resources]]
      [ring.util.response :refer [response]]
      [ring.util.http-response :refer [created forbidden]]
      [clojure.core.async :refer [<!! <! >! go]]
      [clj-time.core :as time]
      [clojure.data.csv :as csv]
      [clojure.java.io :as io]
      [clj-time.coerce :as tc]
      [clj-http.client :as client]
      [clojure.data.json :as json]
      [classification-checker.pages :refer [main-page]]
      [classification_checker.middleware :refer [wrap-middleware]]
      [taoensso.sente :as sente]
      [taoensso.sente.server-adapters.http-kit :refer (get-sch-adapter)]
      [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
      [clojure.set :as set]))

(def client-id 156944108305904)
(def client-secret "b18a88c9654d05b58ee833ad61bf8a63")
(def redirect-uri "https://localhost:3449/facebook-oauth")

(use 'ring.middleware.session.cookie)

(defn if-login [ring-req ok-response]
      (let [{:keys [session]} ring-req
            {:keys [uid]} session]
           (if (some? uid) (ok-response) (forbidden))))

(defn login-handler [ring-req]
      (let [{:keys [session params]} ring-req
            {:keys [user]} params
            {:keys [email]} user]
           {:status 201 :session (assoc session :uid email)}))

(defn get-facebook-credentials [code] (client/get (str "https://graph.facebook.com/v2.12/oauth/access_token?client_id=" client-id "&redirect_uri=" redirect-uri "&client_secret=" client-secret "&code=" code)))
(defn get-facebook-token [code] ((json/read-str ((get-facebook-credentials code) :body) :key-fn keyword) :access_token))

(defn get-facebook-user [code] (client/get (str "https://graph.facebook.com/v2.10/me?fields=email&access_token=" (get-facebook-token code))))
(defn get-email [code] ((json/read-str ((get-facebook-user code) :body) :key-fn keyword) :email))

(defn app [fin fout]
      (let [packer :edn
            {:keys [ch-recv send-fn connected-uids ajax-post-fn ajax-get-or-ws-handshake-fn]} (sente/make-channel-socket! (get-sch-adapter) {:packer packer})
            ring-ajax-post ajax-post-fn
            ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn
            ch-markup ch-recv
            data-send! send-fn
            connected-uids connected-uids]

           (defonce busy-users (atom (set [])))
           (defn free-users [] (set/difference (set (get-in @connected-uids [:any])) @busy-users))

           ;TODO graceful shutdown
           (go
             (with-open [writer (io/writer fout)]
                        (loop []
                              (let [event (<! ch-markup)
                                    {:keys [id ?data uid]} event
                                    time (str (tc/to-long (time/now)))]
                                   (cond
                                     (= id :chsk/uidport-open) (prn (str "user " ?data " connected!"))
                                     (= id :data/checked) (let [params [(concat [time uid] ?data)]]
                                                               (csv/write-csv writer params)
                                                               (.flush writer)
                                                               (swap! busy-users disj uid))))
                              (recur))))

           ;TODO messages may will lose
           (go
             (with-open [reader (io/reader fin)]
                        (loop []
                              (if-let [uid (first (shuffle (free-users)))]
                                      (if-let [row
                                               (->> (csv/read-csv reader)
                                                    (first))]
                                              (do
                                                (swap! busy-users conj uid)
                                                (data-send! uid [:data/item-received row])) ; TODO: do we need to know when the task was sent?
                                              (System/exit 0))
                                      (Thread/sleep 1000))
                              (recur))))

           (defroutes routes
                      (GET "/data" req (if-login req #(ring-ajax-get-or-ws-handshake req)))
                      (POST "/data" req (if-login req #(ring-ajax-post req)))

                      (GET "/" [] (main-page *anti-forgery-token*))
                      (POST "/session/new" req (login-handler req))

                      (GET "/facebook-oauth" ring-req
                           (let [
                                 {:keys [session query-params]} ring-req
                                 email (get-email (query-params "code"))]
                                {
                                 :status 302
                                 :session (assoc session :uid email :user (assoc (session :user) :email email))
                                 :headers {"Location" "/#"}
                                 }
                                )
                           )

                      (GET "/show-session" ring-req
                           (let [
                                 {:keys [session]} ring-req] {:status 200 :body session}
                                )
                           )

                      (resources "/")
                      (not-found "Not Found"))

           (wrap-middleware #'routes)))