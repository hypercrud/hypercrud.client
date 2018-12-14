(ns fixtures.domains
  (:require
    [contrib.data :refer [group-by-unique]]
    [contrib.reader]))


(def schema
  (group-by-unique :db/ident
    '({:db/ident :attribute/ident, :db/valueType {:db/ident :db.type/keyword}, :db/cardinality {:db/ident :db.cardinality/one}, :db/unique {:db/ident :db.unique/identity}, :db/doc "FK to schema, they can't be directly on $ schema because attribute renderers are a \"source code\" concern. TODO: move these off domain and into the fiddle repo."}
       {:db/ident :attribute/renderer, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "Default attribute renderer, a CLJS var like `hyperfiddle.ui.controls/code`."}
       {:db/ident :database/custom-write-sec, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/one}}
       {:db/ident :database/uri, :db/valueType {:db/ident :db.type/uri}, :db/cardinality {:db/ident :db.cardinality/one}, :db/unique {:db/ident :db.unique/identity}, :db/doc "Datomic connection URI"}
       {:db/ident :database/write-security, :db/valueType {:db/ident :db.type/ref}, :db/cardinality {:db/ident :db.cardinality/one}}
       {:db/ident :database.custom-security/client, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/one}}
       {:db/ident :database.custom-security/server, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/one}}
       {:db/ident :domain/aliases, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/many}, :db/unique {:db/ident :db.unique/value}, :db/doc "Register production hostname here and point it at the hyperfiddle.net IP. In production, server-side rendering is enabled, auto-transact is always on, and the hyperfiddle toolbar is not served."}
       {:db/ident :domain/code, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "Do not use and undocumented. CLJS namespace for storing view functions, evaluated on page load. Todo: clean this up."}
       {:db/ident :domain/css, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "CSS which is always loaded. Todo: expose a way to load foreign assets."}
       {:db/ident :domain/databases, :db/valueType {:db/ident :db.type/ref}, :db/cardinality {:db/ident :db.cardinality/many}, :db/isComponent true, :db/doc "Datomic databases available for query from this domain."}
       {:db/ident :domain/disable-javascript, :db/valueType {:db/ident :db.type/boolean}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "Don't serve hyperfiddle javascript on aliased domains. Today, this is good for static sites, but in the future may make things slower becasuse it limits our ability to use `Cache-Control: Immutable`. Todo: expose more I/O configuration choices here."}
       {:db/ident :domain/environment, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "EDN map of constants available to your fiddles, for example API keys."}
       {:db/ident :domain/fiddle-database, :db/valueType {:db/ident :db.type/ref}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "Database to store your fiddles. It is probably also assigned a name above, so your fiddles can query it, for example to generate site maps."}
       {:db/ident :domain/home-route, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "Index hyperfiddle route like `[:demo/shirt-sizes [#entity[\"$\" :gender/male]]]`, copy it from data mode"}
       {:db/ident :domain/ident, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/one}, :db/unique {:db/ident :db.unique/identity}, :db/doc "Hyperfiddle Cloud subdomain."}
       {:db/ident :domain/router, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "Experimental and undocumented userland router definition"}
       {:db/ident :domain.database/name, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "Name of this database in Datomic query :in clause"}
       {:db/ident :domain.database/record, :db/valueType {:db/ident :db.type/ref}, :db/cardinality {:db/ident :db.cardinality/one}}
       {:db/ident :fiddle/cljs-ns, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "Undocumented, pending cleanup"}
       {:db/ident :fiddle/css, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "CSS for this fiddle which is in the document only when this fiddle is visible. The default form renderers insert automatic css classes based on :fiddle/ident, :link/rel, :db.valueType, etc. CSS is not scoped, so be careful to write targetted CSS for this fiddle."}
       {:db/ident :fiddle/hydrate-result-as-fiddle, :db/valueType {:db/ident :db.type/boolean}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "Experimental flag for higher-order fiddles. When set, data-sync will interpret this fiddle's arguments as a fiddle, which is a recursion mechanic. We're not sure if this is a good idea, but the docs site uses it for embedding examples."}
       {:db/ident :fiddle/ident, :db/valueType {:db/ident :db.type/keyword}, :db/cardinality {:db/ident :db.cardinality/one}, :db/unique {:db/ident :db.unique/identity}, :db/doc "Fiddle identifier used in default URL router"}
       {:db/ident :fiddle/links, :db/valueType {:db/ident :db.type/ref}, :db/cardinality {:db/ident :db.cardinality/many}, :db/isComponent true, :db/doc "Links to other fiddles, used for data-sync, automatic UI and business logic."}
       {:db/ident :fiddle/markdown, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "Optional place to store markdown, your :fiddle/renderer may render this."}
       {:db/ident :fiddle/pull, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "Datomic pull expression, the pulled entity is passed by URL. Pull :db/id or :db/ident for an editable form."}
       {:db/ident :fiddle/pull-database, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "Argument to `datomic.api/pull`"}
       {:db/ident :fiddle/query, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "Datomic query. Database inputs are resolved by name through the `:domain/environment`. Pull `:db/id` for editable forms. Currently no support yet for rules, d/history or d/log."}
       {:db/ident :fiddle/renderer, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "Reagent expression for the view. Clear to restore default. There are some bugs related to default values, so if Hyperfiddle generates a datoms conflict, just fix it at the stage."}
       {:db/ident :fiddle/type, :db/valueType {:db/ident :db.type/keyword}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "Datomic API for data fetching, blank means nothing"}
       {:db/ident :hyperfiddle/owners, :db/valueType {:db/ident :db.type/uuid}, :db/cardinality {:db/ident :db.cardinality/many}, :db/doc "FK to users who have administrator role on this domain."}
       {:db/ident :link/class, :db/valueType {:db/ident :db.type/keyword}, :db/cardinality {:db/ident :db.cardinality/many}, :db/doc "App-specific semantic class of the link, like HTML's css classes. Fiddle views and API clients should select links by class with: \n* `hyperfiddle.data/select`\n* `hyperfiddle.data/select-all`\n* `hyperfiddle.data/select-here`\n* `hyperfiddle.data/browse`"}
       {:db/ident :link/fiddle, :db/valueType {:db/ident :db.type/ref}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "Links point to fiddles. Any fiddle dependencies needed for the query or pull are passed by URL and encoded as \"ednish\". Allowed parameters are entity identifiers and scalars."}
       {:db/ident :link/formula, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "Deprecated – this is fully managed based on :link/rel. You can override it, but there isn't a good reason to do that anymore."}
       {:db/ident :link/path, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "E.g. `0 :reg/gender :db/ident`. Associates this link with a pulled entity by pull path, accounting for data cardinality. Find element index must be specified only for relation queries."}
       {:db/ident :link/rel, :db/valueType {:db/ident :db.type/keyword}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "Set the rel to opt-in to automatic CRUD functionality. The set of rels is open for extension, but there is no admin panel for that yet. The integrated Datomic console's entity navigation and CRUD ensures that these rels exist for every entity pulled. Builtin rels:\n* *:hf/self* – Editor for the entity at a pulled path, accounting for cardinality. There must only be one :self link.\n* *:hf/rel* – Like :self but optional and can have more than one, for linking to related data.\n* *:hf/iframe* – Like :rel but data loads inline with this fiddle, use this for picker options\n* *:hf/new* – Like :rel but manufactures an entity tempid\n* *:hf/affix* – Like :new but affixes the new entity as a child to self.\n* *:hf/remove* – Retracts the entity\n* *:hf/detach* – Retract only the parent-child reference to this entity"}
       {:db/ident :link/tx-fn, :db/valueType {:db/ident :db.type/string}, :db/cardinality {:db/ident :db.cardinality/one}, :db/doc "Optional CLJS function which generates a Datomic transaction value. Turns the link into a button which calls the :tx-fn and stages the result. If there is a :link/fiddle, the link will render as a popover and the tx-fn will be called with the popover form's value when it stages. Some rels provide a default tx-fn which you can override. TODO: clean this up."})))