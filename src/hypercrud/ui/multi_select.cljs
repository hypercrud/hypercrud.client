(ns hypercrud.ui.multi-select
  (:require [hypercrud.ui.auto-control :refer [auto-control]]
            [hypercrud.ui.form-util :as form-util]))


(defmulti multi-select-markup (fn [click-add! control-tuples] :default))


(defn multi-select* [markupfn entity add-item! {:keys [local-transact!]
                                                {:keys [:attribute/ident]} :field :as widget-args}]
  (let [value (get entity ident)
        control-tuples (seq (mapv (fn [eid]
                                    (let [click-remove! #(form-util/change! local-transact! (:db/id entity) ident [eid] nil)
                                          new-args (-> widget-args
                                                       (assoc-in [:field :attribute/cardinality] :db.cardinality/one)
                                                       (update :expanded-cur #(% [eid ident] {})))
                                          control [auto-control (assoc entity ident eid) new-args]]
                                      [eid click-remove! control]))
                                  value))]
    (markupfn add-item! control-tuples)))


(defmethod multi-select-markup :default [click-add! control-tuples & [css-class]]
  [:div.multi-select {:class css-class}
   (map (fn [[eid click-remove! control]]
          ^{:key (str eid)}                                 ;(str eid) so this works when eid is nil
          [:div.multi-select-group
           [:button {:on-click click-remove!} "-"]
           control])
        control-tuples)
   [:button {:on-click click-add!} "+"]])
