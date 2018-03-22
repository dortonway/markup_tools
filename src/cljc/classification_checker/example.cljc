(ns classification_checker.example)

(defprotocol ParaphraseClassification
  (id [this])
  (right [this])
  (wrong [this]))

(defrecord ParaphraseExample [utterance1 utterance2 is-same? assessor mark-time]
  ParaphraseClassification
  (id [this] (if (some? this) (hash this)))
  (right [this] (->ParaphraseExample utterance1 utterance2 true assessor mark-time))
  (wrong [this] (->ParaphraseExample utterance1 utterance2 false assessor mark-time)))

(defn paraphrase-example
  "creates example"
  [{:keys [utterance1 utterance2 is-same? assessor mark-time]}]
  (->ParaphraseExample utterance1 utterance2 is-same? assessor mark-time))
