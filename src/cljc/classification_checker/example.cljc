(ns classification_checker.example)

(defprotocol ParaphraseClassification
  (id [this])
  (right [this])
  (wrong [this])
  (unknown [this])
  (reg-mark [this assessor timestamp]))

(defrecord ParaphraseExample [utterance1 utterance2 is-same? assessor mark-time]
  ParaphraseClassification
  (id [this] (if (some? this) (hash this)))
  (right [this] (->ParaphraseExample utterance1 utterance2 true assessor mark-time))
  (wrong [this] (->ParaphraseExample utterance1 utterance2 false assessor mark-time))
  (unknown [this] (->ParaphraseExample utterance1 utterance2 nil assessor mark-time))
  (reg-mark [this assr timestamp] (->ParaphraseExample utterance1 utterance2 is-same? assr timestamp)))

(defn paraphrase-example
  "creates example"
  [{:keys [utterance1 utterance2]}]
  (->ParaphraseExample utterance1 utterance2 nil nil nil))
