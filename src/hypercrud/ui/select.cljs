(ns hypercrud.ui.select
  (:require [cats.monad.exception :as exception]
            [hypercrud.client.tx :as tx]
            [hypercrud.form.option :as option]
            [hypercrud.types :refer [->DbId]]))


(defn select-boolean* [value maybe-field props param-ctx]
  (let [props {;; normalize value for the dom - value is either nil, an :ident (keyword), or eid
               :value (if (nil? value) "" (str value))
               ;; reconstruct the typed value
               :on-change #(let [v (case (.-target.value %)
                                     "" nil
                                     "true" true
                                     "false" false)]
                             ((:user-swap! param-ctx) {:tx (tx/update-entity-attr (:entity param-ctx) (:attribute param-ctx) v)}))
               :disabled (if (:read-only props) true false)}]
    [:select props
     [:option {:key true :value "true"} "True"]
     [:option {:key false :value "false"} "False"]
     [:option {:key :nil :value ""} "--"]]))


(defn select* [value maybe-field props param-ctx]
  ; value :: {:db/id #DbId[17592186045891 17592186045422]}
  (let [props {;; normalize value for the dom - value is either nil, an :ident (keyword), or eid
               :value (cond
                        (nil? value) ""
                        :else (-> value :db/id :id str))

               ;; reconstruct the typed value
               :on-change #(let [select-value (.-target.value %)
                                 dbid (cond
                                        (= "" select-value) nil
                                        :else-hc-select-option-node (->DbId (js/parseInt select-value 10) (-> value :db/id :conn-id)))]
                             ((:user-swap! param-ctx) {:tx (tx/update-entity-attr (:entity param-ctx) (:attribute param-ctx) dbid)}))
               :disabled (if (:read-only props) true false)}]

    ; Having options is not required e.g. raw mode.
    (let [options (if maybe-field (option/hydrate-options maybe-field param-ctx) (exception/success []))]
      (if (exception/failure? options)
        [:span {:on-click #(js/alert (pr-str (.-e options)))} "Failed to hydrate"]
        (let [option-records (.-v options)]
          [:select.select props
           (concat
             (->> (sort-by second option-records)
                  (mapv (fn [[dbid label]]
                          ^{:key dbid}
                          [:option {:value (.-id dbid)} label])))
             [[:option {:key :blank :value ""} "--"]])])))))
