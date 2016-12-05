(ns hypercrud.browser.pages.entity
  (:require [hypercrud.form.q-util :as q-util]
            [hypercrud.ui.form :as form]))


(defn ui [stage-tx! graph result ordered-forms links navigate-cmp param-ctx]
  (let [param-ctx (assoc param-ctx :result result)]
    [:div
     (map (fn [entity form]
            ^{:key (hash [(.-dbid entity) (.-dbid form)])}
            [form/form graph entity form links stage-tx! navigate-cmp param-ctx])
          result ordered-forms)]))


(defn query [dbid form param-ctx]
  (assert false "todo")                         ; this fn should be fixed and used, currently dead code
  (form/query dbid form q-util/build-params-from-formula param-ctx))
