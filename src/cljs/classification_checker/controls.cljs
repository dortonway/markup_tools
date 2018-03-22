(ns classification_checker.controls
  (:require
    [antizer.reagent :as ant]
    [reagent.core :as r]
    [keybind.core :as key]
    [classification_checker.example :as example]
    [classification_checker.user :as user]
    [classification_checker.dispatcher :as dispatcher]))

(defn buttons [on-ok on-cancel on-skip]
  [ant/row
   [ant/col {:span 2 :offset 9} [ant/button {:class "skip-example" :size "large" :icon "question" :on-click on-skip}]]
   [ant/col {:span 2 :offset 1} [ant/button {:class "wrong-example" :size "large" :type "danger" :icon "close" :on-click on-cancel}]]
   [ant/col {:span 2} [ant/button {:class "right-example" :size "large" :type "primary" :icon "check" :on-click on-ok}]] ])

(defn paraphrase-view [title example]
  (defn click-right [] (dispatcher/emit :example-updated (example/right example)))
  (defn click-wrong [] (dispatcher/emit :example-updated (example/wrong example)))
  (defn click-skip [] (dispatcher/emit :example-updated example))

  (key/bind! "r" ::next click-right)
  (key/bind! "w" ::next click-wrong)
  (key/bind! "space" ::next click-skip)

  [ant/locale-provider {:locale (ant/locales "ru_RU")}
   [ant/layout
    [ant/layout-header [:h1 title] ]
    [ant/layout-content {:class "content"}
     (if (nil? example) [:div]
       [:div {:style {:width "100%"}}
        [:div {:class "example"} (:utterance1 example)]
        [:div {:class "example-class"} (:utterance2 example)] ])]
    [ant/layout-footer {:class "footer"}
     (if (nil? example)
       (r/as-element [buttons nil nil nil])
       (r/as-element [buttons click-right click-wrong click-skip]))]]])

(defn identification-view []
  (defn submit-form-if-valid [errors values]
    (if (nil? errors)
      (do
        (def email
          (second (first (-> (fn [result key]
                               (let [v (aget values key)]
                                 (if (= "function" (goog/typeOf v)) result (assoc result key v))))
                             (reduce {} (.getKeys goog/object values))))))
        (dispatcher/emit :email-received (user/user-info {:email email})))))

  (ant/create-form (fn [_] (let [form (ant/get-form) submit-handler #(ant/validate-fields form submit-form-if-valid)]
                                 [:div {:style { :display "flex" :align-items "center" :justify-content "center" :height "100%"} }
                                  [ant/form {:layout "horizontal" :on-submit #(do (.preventDefault %) (submit-handler))}
                                   [ant/form-item {:label "Email"}
                                    (ant/decorate-field form "email" {:rules [{:required true} {:type "email"}]} [ant/input])]
                                   [ant/form-item
                                    [:div {:style {:text-align "center"}}
                                     [ant/button {:type "primary" :html-type "submit"} "ok"] ]]]]))))
