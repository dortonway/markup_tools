(ns classification_checker.controls
  (:require
    [antizer.reagent :as ant]
    [reagent.core :as r]
    [keybind.core :as key]
    [taoensso.timbre :as timbre
     :refer-macros [log  trace  debug  info  warn  error  fatal  report
                    logf tracef debugf infof warnf errorf fatalf reportf
                    spy get-env]]
    [classification_checker.user :as user]
    [classification_checker.dispatcher :as dispatcher]
    [clojure.string :as str]))

(defn buttons [on-ok on-cancel on-skip]
  (key/bind! "r" ::next on-ok)
  (key/bind! "w" ::next on-cancel)
  (key/bind! "space" ::next on-skip)

  [ant/row
   [ant/col {:span 2 :offset 9} [ant/button {:class "skip-example" :size "large" :icon "question" :on-click on-skip}]]
   [ant/col {:span 2 :offset 1} [ant/button {:class "wrong-example" :size "large" :type "danger" :icon "close" :on-click on-cancel}]]
   [ant/col {:span 2} [ant/button {:class "right-example" :size "large" :type "primary" :icon "check" :on-click on-ok}]]])

(defn paraphrase-view [title example]
  (if (= 2 (count example))
    (letfn [(click-right [] (dispatcher/emit :example-updated [(first example) (second example) true]))
            (click-wrong [] (dispatcher/emit :example-updated [(first example) (second example) false]))
            (click-skip [] (dispatcher/emit :example-updated [(first example) (second example) nil]))]

      [ant/locale-provider {:locale (ant/locales "ru_RU")}
       [ant/layout
        [ant/layout-header [:h1 title]]
        [ant/layout-content {:class "content"}
         [:div {:style {:width "100%"}}
          [:div {:class "example"} (first example)]
          [:div {:class "example-class"} (second example)]]]
        [ant/layout-footer {:class "footer"}
         (if (nil? example)
           (r/as-element [buttons nil nil nil])
           (r/as-element [buttons click-right click-wrong click-skip]))]]])
    (warn (str "A paraphrase must have exactly 2 values:" (str example)))))


(defn classification-view [title example]
  (if (= 2 (count example))
    (let [[question answers-str] example
          no-answer "(Нет подходящего ответа)"
          skip-answer "(Пропустить этот вопрос)"
          answers (str/split answers-str #"\|")
          selected-answer (r/atom no-answer)]
      [ant/locale-provider {:locale (ant/locales "ru_RU")}
       [ant/layout
        [ant/layout-header
         [:div title]]
        [ant/layout-content {:class "content"}
         [:div {:class "example"} question]
         [ant/row
          [ant/col {:span 12}
           [ant/select {:default-value no-answer :on-change #(reset! selected-answer %) #_:style #_{:width "1000"}} ; TODO make width 100%
            (for [a (concat [no-answer skip-answer] answers)]
              [ant/select-option {:value a} a])]]]]
        [ant/layout-footer {:class "footer"}
         [ant/button {:class "right-example" :size "large" :type "primary" :icon "check" :on-click #(dispatcher/emit :example-updated [question @selected-answer])}]]]])
    (warn (str "A classification example must have exactly 2 values: question and string of answers joined with \"|\""))))


(defn identification-view []
  (defn submit-form-if-valid [errors values]
    (if (nil? errors)
      (do
        (def email
          (second (first (-> (fn [result key]
                               (let [v (aget values key)]
                                 (if (= "function" (goog/typeOf v)) result (assoc result key v))))
                             (reduce {} (.getKeys goog/object values))))))
        (dispatcher/emit :session-created (user/user-info {:email email})))))

  (ant/create-form (fn [_] (let [form (ant/get-form) submit-handler #(ant/validate-fields form submit-form-if-valid)]
                                 [:div {:style { :display "flex" :align-items "center" :justify-content "center" :height "100%"} }
                                  [ant/form {:layout "horizontal" :on-submit #(do (.preventDefault %) (submit-handler))}
                                   [ant/form-item {:label "Email"}
                                    (ant/decorate-field form "email" {:rules [{:required true} {:type "email"}]} [ant/input])] ;TODO
                                   [ant/form-item
                                    [:div {:style {:text-align "center"}}
                                     [ant/button {:type "primary" :html-type "submit"} "ok"] ]]
                                   ]
                                  [:a {:style {:text-align "center"}}
                                    "facebook" ]
                                  ]))))
