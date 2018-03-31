(ns classification_checker.services
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
    [classification_checker.dispatcher :as dispatcher]
    [cljs-http.client :as http]
    [taoensso.sente  :as sente :refer (cb-success?)]
    [taoensso.timbre :as timbre
     :refer-macros [log  trace  debug  info  warn  error  fatal  report
                    logf tracef debugf infof warnf errorf fatalf reportf
                    spy get-env]]
    [cljs.core.async :refer [<!]]))

(enable-console-print!)

(defonce router_ (atom nil))
(defn  stop-receive-loop! [] (when-let [stop-f @router_] (stop-f)))

(defonce upload_ (atom nil))

(defn open-socket! []
  (let [packer :edn
        {:keys [chsk ch-recv send-fn state]} (sente/make-channel-socket! "/data" {:type :auto :packer packer})]
    (def chsk       chsk)
    (def ch-items   ch-recv) ; ChannelSocket's receive channel
    (def chsk-send! send-fn) ; ChannelSocket's send API fn
    (def chsk-state state)   ; Watchable, read-only atom


    (stop-receive-loop!)
    (reset! router_
            (sente/start-client-chsk-router! ch-items (fn [{:keys [?data]}]
                                                        (debug (str "Received" ?data))
                                                        (let [[id item] ?data]
                                                          (cond
                                                            (not (nil? (:last-ws-error (first ?data)))) (dispatcher/emit :session-needed nil)
                                                            (= id :data/item-received) (dispatcher/emit :downloaded item))))))
    (reset! upload_ chsk-send!)))


(defn upload! [example] (@upload_ [:data/checked example]))

(defn redirect! [loc] (set! (.-location js/window) loc))

;TODO
(defn create-session! [user callback]

  (defn process-response! [response ok-callback]
    (cond
      (<= 200 (:status response) 299) (do (open-socket!) (ok-callback))
      (= (:status response) 403) (redirect! "/")
      :else (binding [*print-fn* *print-err-fn*] (println (str "error code " (:status response))))))

  (stop-receive-loop!)

  (go (let [marshaled-user {:user (clj->js user)}
            csrf-token (.getAttribute (.querySelector js/document "meta[name=csrf-token]") "content")
            response (<! (http/post "/session/new"
                                    {:with-credentials? false
                                     :json-params marshaled-user
                                     :headers {"x-csrf-token" csrf-token}}))]
        (process-response! response callback))))
