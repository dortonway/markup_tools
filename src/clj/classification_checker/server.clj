(ns classification-checker.server
  (:require [classification-checker.handler :refer [app]]
            [clojure.java.io :as io]
            [config.core :refer [env]]
            [ring.adapter.jetty :refer [run-jetty]])
  (:gen-class))

 (defn -main [& args]
   (with-open [reader (io/reader *in*)
               writer (io/writer *out*)]
     (let [port (Integer/parseInt (or (env :port) "3000"))]
       (run-jetty (partial app reader writer) {:port port :join? false}))))
