(ns classification-checker.server
  (:require [classification-checker.handler :refer [app]]
            [classification_checker.example :as example]
            [clojure.java.io :as io]
            [config.core :refer [env]]
            [clojure.core.async :refer [go chan >! <!] :as async]
            [clojure.data.csv :as csv]
            [org.httpkit.server :refer [run-server]])
  (:gen-class))


(defn input-worker [fin]
  (defonce in-examples (chan))

  (defn csv-data->maps [csv-data]
    (map zipmap
         (->> (first csv-data) ;; First row is the header
              (map keyword) ;; Drop if you want string keys instead
              repeat)
         (rest csv-data)))

  (go
    (defn mpr [example] (example/paraphrase-example {:utterance1 (:question example) :utterance2 (:class example)}))

    (with-open [reader (io/reader fin)]
      (doseq [ex (->> (csv/read-csv reader)
                      (csv-data->maps)
                      (map mpr))]
        (>! in-examples ex))
      ))
  in-examples)

;TODO graceful shutdown
(defn output-worker [fout]
  (defonce out-examples (chan))

  (go
    (with-open [writer (io/writer fout)]
      (loop []
        (let [example (<! out-examples)]
          (csv/write-csv writer (conj [] (vals example)))
          (.flush writer)
          )
        (recur))))
  out-examples)

(def application (app (input-worker "first_classification.csv") (output-worker "checked.csv")))

(defn -main [& args]
  (let [port (Integer/parseInt (or (env :port) "3000"))]
    (run-server application {:port port})))
