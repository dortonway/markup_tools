(ns classification-checker.pages
  (:require
    [config.core :refer [env]]
    [adzerk.env :as aenv]
    [hiccup.page :refer [include-js include-css html5]]))

(aenv/def
  CLIENT_ID nil
  REDIRECT_URI nil
)

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
     (include-js "/js/app.js")
     str "<script>document.querySelector('a').onclick = function (e) { e.preventDefault(); window.location.href = \"https://www.facebook.com/v2.12/dialog/oauth?client_id=" CLIENT_ID "&redirect_uri=" REDIRECT_URI "&scope=email\"; }</script>"
     ]))
