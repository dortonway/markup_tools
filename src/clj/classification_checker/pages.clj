(ns classification-checker.pages
  (:require
    [config.core :refer [env]]
    [hiccup.page :refer [include-js include-css html5]]))

(defn main-page [csrf-token]
  (def head
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:meta {:name "csrf-token" :content csrf-token}]

     (include-css (if (env :dev) "https://cdnjs.cloudflare.com/ajax/libs/antd/3.2.3/antd.css" "https://cdnjs.cloudflare.com/ajax/libs/antd/3.2.3/antd.min.css"))
     (include-css (if (env :dev) "/css/site.css" "/css/site.min.css"))])

  (def mount-target [:div#app])

  (html5
    head
    [:body
     mount-target
     (include-js "/js/app.js")]))
