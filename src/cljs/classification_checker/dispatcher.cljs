(ns classification_checker.dispatcher
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]])
  (:require
    [cljs.core.async :refer [chan put! <! >!]]
    [taoensso.timbre :as timbre
     :refer-macros [log  trace  debug  info  warn  error  fatal  report
                    logf tracef debugf infof warnf errorf fatalf reportf
                    spy get-env]]))

(def actions (atom {}))

(defn register [action callback] (swap! actions conj {action callback}))

(defonce event-queue (chan))

(go-loop []
  (let [
            {action :action data :payload} (<! event-queue)
            callback (get @actions action)]
    (if (some? callback) (callback data)))
    (recur))

(defn emit [action payload]
  (debug "event raised" action payload)
  (put! event-queue {:action action :payload payload}))
