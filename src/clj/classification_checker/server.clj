(ns classification-checker.server
  (:require [classification-checker.handler :refer [app]]
            [config.core :refer [env]]
            [org.httpkit.server :refer [run-server]])
  (:gen-class))

(def application (app "classification_zpp_input.csv" "classification_zpp_output.csv"))

(defn -main [& args]
  (let [port (Integer/parseInt (or (env :port) "3000"))]
    (run-server application {:port port})))
