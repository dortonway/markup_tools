(ns classification_checker.core
  (:require [classification_checker.controls :refer [paraphrase-view identification-view]]
            [classification_checker.store :refer [current-task]]
            [classification_checker.services :refer [create-session! upload! redirect!]]
            [classification_checker.dispatcher :as dispatcher]
            [reagent.session :as session]
            [reagent.core :as reagent]
            [secretary.core :as secretary :include-macros true]
            [accountant.core :as accountant]
            [clojure.string :as string]))

(enable-console-print!)

;; -------------------------
;; Views

(defn check-markup-page [] (paraphrase-view "Примеры значат одно и то же?" @current-task))
(defn login-page [] (identification-view))

;; -------------------------
;; Routes

(defn current-page [] [(session/get :current-page)])

(secretary/defroute "/" [] (session/put! :current-page login-page))

;; -------------------------
;; View handlers

(dispatcher/register :example-updated (fn [example] (reset! current-task nil) (upload! example)))
(dispatcher/register :downloaded (fn [example] (reset! current-task example)))

;TODO cors
(dispatcher/register :login-needed (fn [_] (session/put! :current-page login-page)))
(dispatcher/register :email-received (fn [user] (create-session! user (fn [] (session/put! :current-page check-markup-page)))))

;; -------------------------
;; Initialize app

(defn init! []
  (accountant/configure-navigation!
    {:nav-handler (fn [path] (secretary/dispatch! path))
     :path-exists? (fn [path] (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (reagent/render [current-page] (.getElementById js/document "app")))
