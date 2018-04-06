(ns hypercrud.browser.browser-ui
  (:require [cats.core :as cats :refer [mlet]]
            [cats.monad.either :as either]
            [contrib.css :refer [css-slugify classes]]
            [contrib.data :as util :refer [unwrap or-str]]
            [contrib.reactive :as r]
    #?(:cljs [contrib.reagent :refer [fragment]])
            [contrib.string :refer [memoized-safe-read-edn-string]]
            [contrib.try :refer [try-either]]
            [hypercrud.browser.base :as base]
            [hypercrud.browser.context :as context]
            [hypercrud.browser.link :as link]
            [hypercrud.browser.routing :as routing]
            [hypercrud.types.Err :as Err]
    #?(:cljs [hypercrud.ui.control.markdown-rendered :refer [markdown-rendered*]])
            [hypercrud.ui.native-event-listener :refer [native-on-click-listener]]
    #?(:cljs [hypercrud.ui.safe-render :refer [safe-reagent-call]])
            [hypercrud.ui.stale :as stale]
    ;#?(:cljs [hypercrud.ui.form :as form])
            [hyperfiddle.foundation :as foundation]
            [hyperfiddle.foundation.actions :as foundation-actions]
            [hyperfiddle.runtime :as runtime]))


(declare ui-from-link)

(defn fiddle-css-renderer [s]
  [:style {:dangerouslySetInnerHTML {:__html s}}])

(defn auto-ui-css-class [ctx]
  (classes (let [ident @(r/cursor (:hypercrud.browser/fiddle ctx) [:fiddle/ident])]
             [(css-slugify (some-> ident namespace))
              (css-slugify ident)])))

; defn because hypercrud.ui.result/view cannot be required from this ns
(defn f-mode-config []
  {:from-ctx :user-renderer
   :from-fiddle (fn [fiddle] @(r/cursor fiddle [:fiddle/renderer]))
   :with-user-fn #?(:clj  (assert false "todo")
                    :cljs (fn [user-fn]
                            (fn [ctx]
                              #_(fragment :_) #_(list)
                              [:div
                               [safe-reagent-call user-fn ctx (auto-ui-css-class ctx)]
                               [fiddle-css-renderer @(r/cursor (:hypercrud.browser/fiddle ctx) [:fiddle/css])]])))
   ; todo ui binding should be provided by a RT
   :default #?(:clj  (assert false "todo")
               :cljs (fn [ctx]
                       #_(fragment :_) #_(list)
                       [:div
                        [hypercrud.ui.result/view ctx (auto-ui-css-class ctx)]
                        [fiddle-css-renderer @(r/cursor (:hypercrud.browser/fiddle ctx) [:fiddle/css])]]))})

(letfn [(browse [rel #_dependent? path ctx & args]
          (let [{[user-renderer & args] nil :as kwargs} (util/kwargs args)
                {:keys [:link/dependent? :link/path] :as link} @(r/track link/rel->link rel path ctx)
                ctx (-> (context/relation-path ctx (into [dependent?] (unwrap (memoized-safe-read-edn-string (str "[" path "]")))))
                        (as-> ctx (if user-renderer (assoc ctx :user-renderer user-renderer #_(if f #(apply f %1 %2 %3 %4 args))) ctx)))]
            [ui-from-link link ctx (:class kwargs)]))
        (anchor [rel #_dependent? path ctx label & args]
          (let [kwargs (util/kwargs args)
                {:keys [:link/dependent? :link/path] :as link} @(r/track link/rel->link rel path ctx)
                ctx (context/relation-path ctx (into [dependent?] (unwrap (memoized-safe-read-edn-string (str "[" path "]")))))
                props (-> (link/build-link-props link ctx)
                          #_(dissoc :style) #_"custom renderers don't want colored links")]
            [(:navigate-cmp ctx) props label (:class kwargs)]))
        (cell [[d i a] ctx & args]                          ; form only
          #?(:clj nil
             :cljs [hypercrud.ui.form/Cell (context/relation-path ctx [d i a])]))
        (value [path ctx & args]
          (let [{[f & args] nil :as kwargs} (util/kwargs args)
                ctx (context/relation-path ctx path)
                ; Awful hacks to solve a circular reference by using legacy auto-control interface
                ;control (or f (hypercrud.ui.auto-control/auto-control' ctx))
                field (:hypercrud.browser/field ctx)
                ;control-props (merge (hypercrud.ui.auto-control/control-props ctx) kwargs)
                ]
            #?(:cljs [hypercrud.ui.auto-control/auto-control field {} nil ctx])))
        (browse' [rel #_dependent? path ctx]
          (->> (base/data-from-link @(r/track link/rel->link rel path ctx) ctx)
               (cats/fmap :hypercrud.browser/result)
               (cats/fmap deref)))
        (anchor* [rel #_dependent? path ctx]
          (link/build-link-props @(r/track link/rel->link rel path ctx) ctx))]
  ; convenience functions, should be declared fns in this or another ns and accessed out of band of ctx
  (defn ui-bindings [ctx]
    (assoc ctx
      :anchor anchor
      :browse browse
      :cell cell
      :value value
      :anchor* anchor*
      :browse' browse')))

(defn e->map [e]
  (cond
    (Err/Err? e) {:message (:msg e)
                  :data (:data e)}
    (map? e) e
    (string? e) {:message e}
    :else {:message #?(:clj  (.getMessage e)
                       :cljs (ex-message e))
           :data (ex-data e)
           :cause #?(:clj  (.getCause e)
                     :cljs (ex-cause e))}))

(defn ex-data->human-detail [{:keys [ident human soup] :as data}]
  (or human soup (util/pprint-str data)))

(defn ui-error-inline [e ctx]
  (let [dev-open? true
        {:keys [cause data message]} (e->map e)]
    [:code message " " (if dev-open? (str " -- " (ex-data->human-detail data)))]))

(defn ui-error-block [e ctx]
  (let [dev-open? true
        {:keys [cause data message]} (e->map e)]
    ; todo we don't always return an error with a message
    [:pre
     (if message
       [:h4 message]
       [markdown-rendered* "#### Unrecognized error (please comment on [#170](https://github.com/hyperfiddle/hyperfiddle/issues/170))"])
     (if dev-open? [:p (ex-data->human-detail data)])
     (if (= :hyperfiddle.error/unrecognized (:ident data))
       [markdown-rendered* "Please comment this error at [hyperfiddle/170](https://github.com/hyperfiddle/hyperfiddle/issues/170) so we can match it"])]))

(defn ui-error [e ctx]
  ; :find-element :attribute :value
  (let [C (cond
            (:hypercrud.ui/ui-error ctx) (:hypercrud.ui/ui-error ctx)
            (:hypercrud.browser/attribute ctx) ui-error-inline ; table: header or cell, form: header or cell
            (:hypercrud.browser/find-element ctx) ui-error-inline
            :else ui-error-block)]                          ; browser including inline true links
    [C e ctx]))

(defn page-on-click [rt branch branch-aux route event]
  (when (and route (.-altKey event))
    (runtime/dispatch! rt (fn [dispatch! get-state]
                            (when (foundation/navigable? route (get-state))
                              (foundation-actions/set-route rt route branch false dispatch! get-state))))
    (.stopPropagation event)))

(defn wrap-ui [either-v route ctx & [class]]
  (let [on-click (r/partial (or (:hypercrud.browser/page-on-click ctx) (constantly nil))
                            route)
        either-v (or (some-> @(runtime/state (:peer ctx) [::runtime/partitions (:branch ctx) :error]) either/left)
                     either-v)]
    [native-on-click-listener {:on-click on-click}
     [stale/loading (stale/can-be-loading? ctx) either-v
      (fn [e] [:div {:class (classes "ui" class "hyperfiddle-error")} (ui-error e ctx)])
      (fn [v] [:div {:class (classes "ui" class)} v])
      (fn [v] [:div {:class (classes "ui" class "hyperfiddle-loading")} v])]]))

(defn hf-ui [route ctx]                                     ; returns an Either[Error, DOM]
  (mlet [ctx (base/data-from-route route ctx)
         ui-fn (base/fn-from-mode (f-mode-config) (:hypercrud.browser/fiddle ctx) ctx)]
    (cats/return (ui-fn (ui-bindings ctx)))))

(defn ui-from-route [route ctx & [class]]
  [wrap-ui (hf-ui route ctx) route ctx class])

(defn ui-from-link [link ctx & [class]]
  (let [link-props' (try-either (link/build-link-props link ctx))
        v' (mlet [link-props link-props']
             ; todo should filter hidden links out before recursing (in render-inline-links)
             (if (:hidden link-props)
               (either/right [:noscript])
               (cats/bind (routing/build-route' link ctx)
                          #(hf-ui % (context/clean ctx)))))
        route (unwrap (cats/fmap :route link-props'))]
    [wrap-ui v' route ctx (classes class (css-slugify (:link/rel link)))]))
