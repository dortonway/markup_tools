(ns classification_checker.middleware
  (:require [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [prone.middleware :refer [wrap-exceptions]]
            [ring.middleware.json :refer [wrap-json-params wrap-json-response]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.reload :refer [wrap-reload]]))

(defn wrap-middleware [handler]
  (-> handler
      wrap-keyword-params
      wrap-json-params
      wrap-json-response
      wrap-session
      (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))
      wrap-exceptions
      wrap-reload))
