(ns hypercrud.server.api
  (:refer-clojure :exclude [sync])
  (:require [clojure.set :as set]
            [clojure.walk :as walk]
            [cuerdas.core :as str]
            [datascript.parser :as parser]
            [datomic.api :as d]
            [hypercrud.types.DbVal]
            [hypercrud.types.Entity :refer [->Entity]]
            [hypercrud.types.EntityRequest]
            [hypercrud.types.Err :refer [->Err]]
            [hypercrud.types.QueryRequest]
            [hypercrud.util.branch :as branch]
            [hypercrud.util.core :as util]
            [hypercrud.util.identity :as identity])
  (:import (hypercrud.types.DbVal DbVal)
           (hypercrud.types.EntityRequest EntityRequest)
           (hypercrud.types.QueryRequest QueryRequest)))


(defmulti parameter (fn [this & args] (class this)))

(defmethod parameter :default [this & args] this)

(defmethod parameter DbVal [dbval get-secure-db-with]
  (-> (get-secure-db-with (:uri dbval) (:branch dbval)) :db))

(defn recursively-add-entity-types [pulled-tree dbval]
  (walk/postwalk (fn [o]
                   (if (:db/id o)
                     (->Entity dbval o)
                     o))
                 pulled-tree))

(defmulti hydrate-request* (fn [this & args] (class this)))

(defmethod hydrate-request* EntityRequest [{:keys [e a db pull-exp]} get-secure-db-with]
  (let [{pull-db :db} (get-secure-db-with (:uri db) (:branch db))
        pull-exp (if a [{a pull-exp}] pull-exp)
        pulled-tree (if (identity/tempid? e)
                      (if a
                        nil
                        ; todo return a positive id here
                        {:db/id e})
                      (d/pull pull-db pull-exp e))
        pulled-tree (recursively-add-entity-types pulled-tree db)
        pulled-tree (if a (get pulled-tree a) pulled-tree)]
    pulled-tree))

(defn process-result [user-params fe result]
  (condp = (type fe)
    datascript.parser.Variable result
    datascript.parser.Pull (let [dbval (->> (get-in fe [:source :symbol])
                                            str
                                            (get user-params))]
                             (recursively-add-entity-types result dbval))
    datascript.parser.Aggregate result))

(defn process-scalar [user-params qfind result]
  (process-result user-params (:element qfind) result))

(defn process-tuple [user-params qfind result]
  (mapv (partial process-result user-params)
        (:elements qfind)
        result))

(defmethod hydrate-request* QueryRequest [{:keys [query params]} get-secure-db-with]
  (assert query "hydrate: missing query")
  (let [{:keys [qfind]} (parser/parse-query query)
        ordered-params (->> (util/parse-query-element query :in)
                            (mapv #(get params (str %)))
                            (mapv #(parameter % get-secure-db-with)))
        ;todo gaping security hole
        result (apply d/q query ordered-params)]
    (condp = (type qfind)
      ; todo preserve set results
      datascript.parser.FindRel (mapv #(process-tuple params qfind %) result)
      datascript.parser.FindColl (mapv #(process-scalar params qfind %) result)
      datascript.parser.FindTuple (process-tuple params qfind result)
      datascript.parser.FindScalar (process-scalar params qfind result))))

; todo i18n
(def ERROR-BRANCH-PAST "Branching the past is currently unsupported, please update your basis")

(defn build-get-secure-db-with [staged-branches db-with-lookup local-basis]
  (letfn [(get-secure-db-from-branch [{:keys [branch-ident uri tx] :as branch}]
            (or (get @db-with-lookup branch)
                (let [t (get local-basis uri)
                      init-db-with (if branch-ident
                                     (let [parent-ident (branch/decode-parent-branch branch-ident)
                                           parent-branch (or (->> staged-branches
                                                                  (filter #(and (= parent-ident (:branch-ident %))
                                                                                (= uri (:uri %))))
                                                                  first)
                                                             {:branch-ident parent-ident
                                                              :uri uri})]
                                       (get-secure-db-from-branch parent-branch))
                                     {:db (-> (d/connect (str uri))
                                              (d/db)
                                              (d/as-of t))})
                      ; is it a history query? (let [db (if (:history? dbval) (d/history db) db)])
                      project-db-with (-> (if (empty? tx)
                                            init-db-with
                                            (let [{:keys [db id->tempid with?]} init-db-with
                                                  _ (when (and (not with?) (not= t (d/basis-t db)))
                                                      ; can only run this assert once, on the first time a user d/with's
                                                      ; every subsequent d/with, the new db's basis will never again match the user submitted basis
                                                      ; however this is fine, since the original t is already known good
                                                      (throw (RuntimeException. ERROR-BRANCH-PAST)))
                                                  _ (let [validate-tx (constantly true)] ; todo look up tx validator
                                                      (assert (validate-tx db tx) (str "staged tx for " uri " failed validation")))
                                                  ; todo d/with an unfiltered db
                                                  {:keys [db-after tempids]} (d/with db tx)]
                                              ; as-of/basis-t gymnastics:
                                              ; https://gist.github.com/dustingetz/39f28f148942728c13edef1c7d8baebf/ee35a6af327feba443339176d371d9c7eaff4e51#file-datomic-d-with-interactions-with-d-as-of-clj-L35
                                              ; https://forum.datomic.com/t/interactions-of-d-basis-t-d-as-of-d-with/219
                                              {:db (d/as-of db-after (d/basis-t db-after))
                                               :with? true
                                               ; todo this merge is excessively duplicating data to send to the client
                                               :id->tempid (merge id->tempid (set/map-invert tempids))}))
                                          (update :db (let [read-sec-predicate (constantly true)] ;todo look up sec pred
                                                        #(d/filter % read-sec-predicate))))]
                  (swap! db-with-lookup assoc branch project-db-with)
                  project-db-with)))]
    (fn [uri branch-val]
      (let [branch (or (->> staged-branches
                            (filter #(and (= branch-val (:branch-val %))
                                          (= uri (:uri %))))
                            first)
                       {:branch-val branch-val
                        :uri uri})]
        (get-secure-db-from-branch branch)))))

(defn hydrate-requests [staged-branches requests local-basis] ; theoretically, requests are grouped by basis for cache locality
  (println (->> (map (comp #(str/prune % 40) pr-str) [local-basis staged-branches (count requests)]) (interpose ", ") (apply str "hydrate-requests: ")))
  (let [db-with-lookup (atom {})
        get-secure-db-with (build-get-secure-db-with staged-branches db-with-lookup local-basis)
        pulled-trees (->> requests
                          (map #(try (hydrate-request* % get-secure-db-with)
                                     (catch Throwable e (.println *err* (pr-str e)) (->Err (str e))))))
        ; this can also stream, as the request hydrates.
        id->tempid (reduce (fn [acc [branch db]]
                             (assoc-in acc [(:uri branch) (:branch-val branch)] (:id->tempid db)))
                           {}
                           @db-with-lookup)
        ;result (concat pulled-trees id->tempid)
        result {:pulled-trees pulled-trees
                :id->tempid id->tempid}]
    result))

(defn transact! [dtx-groups]
  (let [valid? (every? (fn [[uri tx]]
                         (let [db (d/db (d/connect (str uri)))
                               ; todo look up tx validator
                               validate-tx (constantly true)]
                           (validate-tx db tx)))
                       dtx-groups)]
    (if-not valid?
      (throw (RuntimeException. "user tx failed validation"))
      (let [tempid-lookups (->> dtx-groups
                                (mapv (fn [[uri dtx]]
                                        (let [{:keys [tempids]} @(d/transact (d/connect (str uri)) dtx)]
                                          [uri tempids])))
                                (into {}))]
        {:tempid->id tempid-lookups}))))

(defn sync [dbs]                                            ; sync is the low level datomic call
  ; ordered kv seq
  (->> dbs (map (juxt identity #(-> % str d/connect d/sync deref d/basis-t)))))
