(ns bamf.rama.interface "Public API for the neutral Rama runtime/client component.")

(defn- resolve-fn [sym] (requiring-resolve sym))

(defn kind [rama-runtime] ((resolve-fn 'bamf.rama.client/kind) rama-runtime))

(defn handle
  "Return the underlying Rama object usable with foreign-* operations."
  [rama-runtime]
  ((resolve-fn 'bamf.rama.client/handle) rama-runtime))

(defn local-runtime [ipc] ((resolve-fn 'bamf.rama.client/local-runtime) ipc))

(defn cluster-runtime [manager] ((resolve-fn 'bamf.rama.client/cluster-runtime) manager))

(defn open-cluster! [config] ((resolve-fn 'bamf.rama.client/open-cluster!) config))

(defn close! [rama-runtime] ((resolve-fn 'bamf.rama.client/close!) rama-runtime))

(defn foreign-depot
  [rama-runtime module-name depot-name]
  ((resolve-fn 'bamf.rama.client/foreign-depot) rama-runtime module-name depot-name))

(defn foreign-pstate
  [rama-runtime module-name pstate-name]
  ((resolve-fn 'bamf.rama.client/foreign-pstate) rama-runtime module-name pstate-name))

(defn foreign-query
  [rama-runtime module-name query-topology-name]
  ((resolve-fn 'bamf.rama.client/foreign-query) rama-runtime module-name query-topology-name))

(defn foreign-invoke-query [query-client] ((resolve-fn 'bamf.rama.client/foreign-invoke-query) query-client))

(defn foreign-select-one [path pstate & [opts]] ((resolve-fn 'bamf.rama.client/foreign-select-one) path pstate opts))

(defn deployed-module-names [rama-runtime] ((resolve-fn 'bamf.rama.client/deployed-module-names) rama-runtime))

(defn module-deployed?
  [rama-runtime module-name]
  ((resolve-fn 'bamf.rama.client/module-deployed?) rama-runtime module-name))
