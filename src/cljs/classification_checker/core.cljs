(ns classification_checker.core
  (:require [classification_checker.controls :refer [paraphrase-view identification-view]]
            [classification_checker.store :refer [current-task checked-tasks unchecked-tasks]]
            [classification_checker.services :refer [upload-batch! download-batch! create-session! redirect!]]
            [classification_checker.dispatcher :as dispatcher]
            [classification_checker.example :as example]
            [cljs-time.core :as time]
            [reagent.core :as reagent :refer [atom]]
            [secretary.core :as secretary :include-macros true]
            [accountant.core :as accountant]))

(enable-console-print!)

;; -------------------------
;; Views

(defn check-markup-page [] (paraphrase-view "Примеры значат одно и то же?" @current-task))
(defn login-page [] (identification-view))

(defn next-page! [] (let [example (first (vals @unchecked-tasks))] (reset! current-task example)))

(defn check! [setter id] (if-let [
                                  task (get @unchecked-tasks id)] (do
                                                                    (swap! checked-tasks conj (setter task nil (time/epoch)))
                                                                    (swap! unchecked-tasks #(dissoc % id))
                                                                    (next-page!))))

(dispatcher/register :marked-right (fn [id] (check! example/right id) ))
(dispatcher/register :marked-wrong (fn [id] (check! example/wrong id) ))
(dispatcher/register :skipped (fn [id]
                                (swap! unchecked-tasks #(dissoc % id))
                                (next-page!)))

(dispatcher/register :login-needed (fn [_] (if (not= (.-location js/window) "/session/new") (redirect! "/session/new"))))
(dispatcher/register :email-received (fn [user] (create-session! user)))
(dispatcher/register :downloaded (fn [examples]
                                   (swap! unchecked-tasks conj examples)
                                   (if (nil? @current-task) (next-page!))))

;TODO
(js/setInterval (fn [] (if (empty? @unchecked-tasks) (download-batch!))) 10000)
(js/setInterval (fn [] (when (not-empty @checked-tasks)
                         (upload-batch! @checked-tasks (fn [] (reset! checked-tasks []))))) 10000)

;; -------------------------
;; Routes

(defonce page (atom #'login-page))

(defn current-page [] @page)

(secretary/defroute "/" [] (set! (.-location js/window) "/session/new"))
(secretary/defroute "/session/new" [] (reset! page #'login-page))
(secretary/defroute "/paraphrase/current" [] (reset! page #'check-markup-page))

;; -------------------------
;; Initialize app

(defn init! []
  (accountant/configure-navigation!
    {:nav-handler
     (fn [path]
       (secretary/dispatch! path))
     :path-exists?
     (fn [path]
       (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (reagent/render [current-page] (.getElementById js/document "app")))
