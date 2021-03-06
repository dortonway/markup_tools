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
            [classification-checker.pages :refer [main-page]]
            [classification_checker.middleware :refer [wrap-middleware]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (get-sch-adapter)]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [clojure.set :as set]))

(use 'ring.middleware.session.cookie)

(defn if-login [ring-req ok-response]
  (let [{:keys [session]} ring-req
        {:keys [uid]} session]
    (if (some? uid) (ok-response) (forbidden))))

(defn login-handler [ring-req]
  (let [{:keys [session params]} ring-req
        {:keys [user]} params
        {:keys [email]} user]
    {:status (created) :session (assoc session :uid email)}))

(defn app [fin fout]
  (let [packer :edn
        {:keys [ch-recv send-fn connected-uids ajax-post-fn ajax-get-or-ws-handshake-fn]} (sente/make-channel-socket! (get-sch-adapter) {:packer packer})]
    (def ring-ajax-post                ajax-post-fn)
    (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
    (def ch-markup                     ch-recv)
    (def data-send!                    send-fn)
    (def connected-uids                connected-uids)

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
              (= id :data/checked) (let [params [[time uid (:utterance1 ?data) (:utterance2 ?data) (:is-same? ?data)]]]
                                     (csv/write-csv writer params)
                                     (.flush writer)
                                     (swap! busy-users disj uid))))
          (recur))))

    ;TODO messages may will lose
    (go
      (with-open [reader (io/reader fin)]
        (loop []
          (if-let [uid (first (shuffle (free-users)))]
            (if-let [[utterance1 utterance2]
                     (->> (csv/read-csv reader)
                          (first))]
              (do
                (swap! busy-users conj uid)
                (data-send! uid [:data/item-received {:utterance1 utterance1 :utterance2 utterance2}]))
              (System/exit 0))
            (Thread/sleep 1000))
          (recur)))))

  (defroutes routes
             (GET  "/data" req (if-login req #(ring-ajax-get-or-ws-handshake req)))
             (POST "/data" req (if-login req #(ring-ajax-post req)))

             (GET "/" [] (main-page *anti-forgery-token*))
             (POST "/session/new" req (login-handler req))

             (resources "/")
             (not-found "Not Found"))

  (wrap-middleware #'routes))
