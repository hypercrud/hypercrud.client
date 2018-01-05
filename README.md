# Hyperfiddle — full-stack CRUD data sync for Clojure & Datomic

This is the open source library powering <http://www.hyperfiddle.net>.

If React.js is managed DOM, Hyperfiddle is managed database and network.

# Dependency coordinates — Todo

    [com.hyperfiddle/hyperfiddle "0.0.0"]

# Overview

Hyperfiddle abstracts over client/server data sync. Userland code does not know the difference between client or server,
the application runs simultaneously in both places.

Abstracting out the network brings a lot of interesting opportunities. Unlike REST/GraphQL/whatever, Hyperfiddle's 
data sync *composes*. Userland is simple Clojure functions and Clojure data; all network I/O is managed.

There is a builtin library of UI components for forms and such, but they are very
easy to write since they are just pure functions, you can just bring your own. The builtin forms are very good and 
highly dynamic, as seen on hyperfiddle.net, there are control points to override all markup.

Hyperfiddle is currently coupled to Reagent but only superficially, the data sync is separate (it has to be as it runs 
in JVM). It should be straightforward to use with any managed dom strategy. 

# Documentation and community

<https://www.reddit.com/r/hyperfiddle/> will aggregate all our scattered blog posts, tutorials
and documentation.

* Slack: #Hyperfiddle @ [clojurians](http://clojurians.net/), come say hi, tell us why you care, and hang out! 
* Twitter: <https://twitter.com/dustingetz>
* [developer mailing list](https://groups.google.com/forum/#!forum/hyperfiddle)

# Roadmap

We're nearing a 0.1 open-source release in Q1 2018.

### Blocking 0.1.0: 

Performance (Hyperfiddle must respond as fast as a Clojure repl)

- [x] data loop running in JVM
- [ ] partition data hydrates, using hyperfiddle link graph, so only what changed gets reloaded

User experience

- [ ] improve popovers, finish stage/discard UX
- [ ] Human readable URLs and customizable URL router
- [ ] Fix query param tooltips when you hover an anchor

Onboarding

- [ ] Hello-world usage and tutorial repo

### 0.2.0

- [ ] Edit React.js/Reagent expressions side-by-side with the running app (like JSFiddle)
- [ ] Links panel user experience

# Philosophy 

Hyperfiddle is built in layers.

* app-as-a-function: Low level I/O runtime for managed client/server data sync, userland is a fn
* app-as-a-value: High level data-driven interpreter function, userland is a value

# App-as-a-Function

UI, at its essense, is about two concerns:

* a view (pixels) — a function returning the virtual dom
* the data backing it — a function returning the query

Hyperfiddle UI components work like this. Here is a simple request function, with two database queries:

```clojure
(def datomic-samples-blog #uri "datomic:free://datomic:4334/samples-blog")
(def db-branch nil) ; This is the datomic time-basis; nil is HEAD

(def race-registration-query
  '[:find (pull ?e [:db/id :reg/email :reg/gender :reg/shirt-size])
    :in $ :where [?e :reg/email]])

(def gender-options-query
  '[:find (pull ?e [:db/id :reg.gender/ident])
    :in $ :where [?e :reg.gender/ident]])

(defn request [state peer]
  (let [$ (hc/db peer datomic-samples-blog db-branch)]            
    [(->QueryRequest race-registration-query {"$" $})
     (->QueryRequest gender-options-query {"$" $})]))
```

Here is the corresponding view function, it is simple and just prettyprints the resultset into a :pre:

```clojure
(defn view [state peer]
  (let [$ (hc/db peer datomic-samples-blog nil)]
    [:ul
     (->> @(hc/hydrate peer (request state $))              ; synchronous and reactive
          (map (fn [relation]
                 (let [e (get relation "?e")]
                   [:li {:key (:db/id e)}
                    [:pre (with-out-str (pprint e))]]))))]))
```
                        
Notes about these functions
* Request fns return seq; you can hydrate many queries in bulk.
* Both are CLJC, runs in both JVM and browser
* I/O runtime manages these fns to load data and then render views
* Request fn is called repeatedly until its return value stabalizes
* This permits queries that depend on the results of earlier queries  
* Data loop typically runs in JVM Datomic Peer, so no network hops or N+1 problem
* Sometimes the runtime chooses to loop in the browser, i.e. a little, incremental 
data load in response to a UI state change

App-as-a-fn with managed I/O is a better way to write web dashboards:

### Programming model is higher level

* application programmer fully insulated from I/O–real functional programming
* No async in userland
* Unified backend/frontend, same codebase runs in both places
* No manual conversion between backend types and frontend types
* No REST, no low level HTTP boilerplate 
* no repeated browser/service round trips
* API built-in (think OData or Swagger)
* No more fiat APIs — all fiddles speak the same API
* Integrated high level tooling (form-builder, app-builder)

### Immutability in database (Datomic) makes SQL/ORM problems go away

* No monstrous JOINs to avoid database round trips
* no service/database round trips at all
* No batching and caching
* No GraphQL resolver hiding complexity in the closet
* No eventual consistency
* No thinking about network cost
* Program as if all data is local–real functional programming

### The machine does all the boilerplate

* Exact data sync in one request, including dynamic dependencies
* Built-in server side rendering, no integration glue code
* Works with browser javascript disabled (whole app can run #{Browser Node JVM} including transactions and business logic)

### Infrastructure benefits

* all requests have a time-basis, all responses are immutable
* CDN integration that understands your app (serve APIs like static sites)
* Dashboards work offline, from disk cache
* Reads continue to be serviced from cache during deployments
* Massively parallelizable, serverless, elastic

Basically, we think Datomic fully solves CRUD apps permanently.

Now that I/O is solved, we can start building *real, composable abstractions:*

# App-as-a-Value

Here is the above functions, represented as a Hyperfiddle EDN value. Actually the below values do a lot more 
than the above functions do, in fewer lines. Data is more information-dense than code, kind of like how a picture is 
worth 1000 words.

```clojure
; Main query
{:db/id        17592186045418,
 :fiddle/type  :query,
 :fiddle/query "[:find (pull ?e [:db/id :reg/email :reg/gender :reg/shirt-size])
                 :in $ :where [?e :reg/email]]",
 :fiddle/links #{
                 ; link to shirt-size options
                 {:db/id               17592186045434,
                  :link/rel            :options,
                  :link/fiddle         #:db{:id 17592186045435},
                  :link/render-inline? true,                ; embedded like an iframe
                  :link/dependent?     true,
                  :link/path           "0 :reg/shirt-size",
                  :link/formula        "(fn [ctx] {:request-params {\"?gender\" (get-in ctx [:cell-data :reg/gender])}})"}

                 ; link to gender options
                 {:db/id               17592186045427,
                  :link/rel            :options,
                  :link/fiddle         #:db{:id 17592186045428},
                  :link/render-inline? true,                ; embedded like an iframe
                  :link/path           "0 :reg/gender"}

                 ; link to new-registration form
                 {:db/id 17592186045481, :link/rel :sys-new-?e, :link/fiddle #:db{:id 17592186045482}}}}

; select options query
{:db/id        17592186045428,
 :fiddle/type  :query,
 :fiddle/query "[:find (pull ?e [:db/id :reg.gender/ident])
                 :in $ :where [?e :reg.gender/ident]]",
 :fiddle/links #{
                 ; link to new-gender form
                 {:db/id 17592186045474, :link/rel :sys-new-?e, :link/fiddle #:db{:id 17592186045475}}}}
```

Fiddles have links to other fiddles, this forms a graph.

Like all Hyperfiddle applications, hyperfiddle EDN values are interpreted by two functions. In this case, 
these two functions are provided by Hyperfiddle, together they comprise the *Hyperfiddle Browser*. The Browser 
is a generic app-as-a-function which interprets hyperfiddle app-values, by navigating the link graph.

*web browser : HTML documents :: hyperfiddle browser :: hyperfiddle EDN values*

Obviously, data is better in every possible way (structural tooling, decoupled from platform, decoupled from 
performance, tiny surface area for bugs, an intelligent child is able to figure it out, like how we all taught 
ourselves HTML at age 13.)

> [![](https://i.imgur.com/iwOvJzA.png)](http://dustingetz.hyperfiddle.net/ezpjb2RlLWRhdGFiYXNlICJzYW5kYm94IiwgOmxpbmstaWQgMTc1OTIxODYwNDU0MTh9)
>
> *Hyperfiddle Browser, navigating a hyperfiddle edn value. Both select options queries are defined as :fiddle/links 
> in the above EDN. The shirt-size query depends on the value of gender. For brevity, the above snippets omit about 
> half of the EDN comprising this demo. [gender/shirt-size demo](http://dustingetz.hyperfiddle.site/ezpjb2RlLWRhdGFiYXNlICJzYW5kYm94IiwgOmxpbmstaWQgMTc1OTIxODYwNDU0MTh9).*

Hyperfiddles are graphs, not documents, so they are stored in databases. Databases storing hyperfiddles are like 
git repos storing the "source code" (data) of your hyperfiddles. Like git and the web, there is no central database.

# App as graph permits optimizations that human-coded I/O cannot do:  

* Automatic I/O partitioning and batching, optimized for cache hits
* Server preloading, prefetching and optimistic push
* Machine learning can analyze the graph to do optimzations

We don't do all of this today, but we will.

# Hyperfiddle.net is 100% built in Hyperfiddle (EDN style)

The following screenshot is fully defined as Hyperfiddle EDN and simple Reagent expressions that a web 
designer could write.

> <kbd><img src="https://i.imgur.com/DCPtHN3.png"></kbd>
> 
> *The radio control on :fiddle/type is a custom attribute renderer, `qe-picker-control`, which is defined 
> in the view menu and eval'ed at runtime. :fiddle/query's custom renderer is a CodeMirror with syntax 
> highlighting, balanced parens etc. The fiddle's own layout (including button labels and local state) 
> is a fiddle renderer, about 100 lines of Reagent markup, defined in the view menu and eval'ed at runtime.
> Each menu is a :button link to another fiddle, with its own data dependencies and renderers.*

# FAQ and anti-features

Integration with REST/fiat APIs: Hyperfiddle can't help you with foreign data sync but it doesn't get in the way either.
Do what you'd do in React. You'll lose out-of-the-box SSR.

Local state in UI: Hyperfiddle is about data sync; Do what you'd do in React. Keep in mind that Hyperfiddle data sync
is so fast (on par with static sites) that you may not need much local state; just use page navigation (the URL is a 
perfectly good place to store state). Local state is perfectly allowable and idiomatic

Reframe: Reframe does not support SSR, and we want SSR by default. It should be straightforward to swap in
reframe or any other rendering strategy.

Why Reagent? Vanilla React is memoized rendering oriented around values. It's not fast enough; Hyperfiddle UIs 
can get very sophisticated very fast, the reactive abstraction is required to feel snappy.

Query subscription issues: Relational/graph databases aren't designed for this, because in the general case you'd
need to re-run all the queries on a page to know if new data impacts them (and on Facebook there are hundreds
of queries per page). However in the average case, we just want 
subscriptions on simple queries for which a heuristic works, or for only specific queries, which I believe the Posh 
project has working, see this [reddit thread](https://www.reddit.com/r/Clojure/comments/6rncgw/arachneframeworkfactui/dl7j2g9/?context=2)

* Eric: I would like to understand where caching actually happens
* Eric: What happens from top to bottom when a form is rendered
* Eric: synchronous and reactive - what does this mean?

# How far will it scale?

Without immutability in the database, all efforts to abstract higher will fail; because to achieve the 
necessary performance, requires writing imperative optimizations to imperative platforms (object-relational 
impedance mismatch, N+1 problems, deeply nested JOINs, caching vs batching tradeoffs). Hyperfiddle cannot 
be built on RDBMS or Neo4J; their imperative nature leaks through all attempts to abstract. This is why all 4GLs 
historically fail. Immutability, as always, to the rescue.

How high can we abstract? We aren't sure yet. It will scale until Datomic's app-as-a-value abstraction breaks down, 
at which point programmers will manually optimize their Datomic services. Datomic Peer vs Client vs Cloud makes a 
difference here as they all have different stories for code/data locality. We think it will scale a lot farther 
than imperative systems.
