; Copyright 2025 Nubank NA

; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0)
; which can be found in the file epl-v10.html at the root of this distribution.
;
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
;
; You must not remove this notice, or any other, from this software.

(ns io.pedestal.interceptor.component
  "Streamline the use of Components as interceptors.

  The [[definterceptor]] macro is used to implement a record that extends
  [[IntoInterceptor]] and so can be used an interceptor."
  {:added "0.8.0"}
  (:require [io.pedestal.interceptor :as i]
            [io.pedestal.interceptor.impl :as impl]))

(defprotocol Handler
  (handle [_ request]
    "Handles a request map and returns a response map, or a channel that conveys the response map."))

(defprotocol OnEnter
  (enter [_ context]
    "Corresponds to the :enter phase of a standard interceptor; passed the context and returns the (new) context,
    or a core.async channel that will convey the new context."))

(defprotocol OnLeave
  (leave [_ context]
    "Corresponds to the :leave phase of a standard interceptor; passed the context and returns the (new) context,
    or a core.async channel that will convey the new context."))

(defprotocol OnError
  (error [_ context exception]
    "Corresponds to the :error phase of a standard interceptor; passed the context and an exception, and returns the (new) context,
    or a core.async channel that will convey the new context."))

(defn component->interceptor
  "Converts a component into an interceptor.   The component must implement
  at least one of the [[Handler]], [[OnEnter]], [[OnLeave]], or [[OnError]] protocols.

  A component can implement the [[IntoInterceptor]] protocol and base the
  `-interceptor` method on this function.

  Returns an interceptor record."
  [interceptor-name component]
  (assert (keyword? interceptor-name))
  (let [enter-fn        (or
                          (when (satisfies? OnEnter component)
                            (fn [context]
                              (.enter component context)))
                          (when (satisfies? Handler component)
                            (impl/wrap-handler
                              (fn [request]
                                (.handle component request)))))
        leave-fn        (when (satisfies? OnLeave component)
                          (fn [context]
                            (.leave component context)))
        error-fn        (when (satisfies? OnError component)
                          (fn [context exception]
                            (.error component context exception)))
        interceptor-map (cond-> {:name interceptor-name}
                          enter-fn (assoc :enter enter-fn)
                          leave-fn (assoc :leave leave-fn)
                          error-fn (assoc :error error-fn))]
    (i/interceptor interceptor-map)))

(def ^:private method->protocol
  {'handle `Handler
   'enter   `OnEnter
   'leave   `OnLeave
   'error   `OnError})

(defn- expand-opts+specs
  "Decomposes the opts+specs to opts (an initial map),
  and specs, a sequence compatible with defrecord.

  Between opts and proper specs may come methods for the four
  protocols defined in this namespace, in which case a proper protocol/method
  is generated.  The point is that a definterceptor can specify a component method
  without having to know about the component protocol, it is supplied automatically."
  [opts+specs]
  (let [head (first opts+specs)
        opts (when (map? head)
               head)]
    (loop [output (if opts
                    [opts]
                    [])
           [head & rest :as remaining] (if opts (rest opts+specs) opts+specs)]
      (cond
        (nil? head)
        output

        (symbol? head)
        ;; We've reached formal protocol/methods
        (into output remaining)

        (not (list? head))
        (throw (ex-info (str "Unexpected value for record spec: " head)
                        {:value     head
                         :remaining rest}))

        :else
        (let [method-name (first head)
              protocol    (get method->protocol method-name)]
          (if protocol
            (recur (conj output protocol head)
                   rest)
            (throw (ex-info (str "Unexpected method: " method-name)
                            {:method        head
                             :valid-methods (keys method->protocol)}))))))))

(defmacro definterceptor
  "Defines an interceptor component, as a Clojure record. The interceptor's name will be
  the record's name as a namespace qualified keyword.

  After the optional options map, and before the normal protocol/method specs,
  additional methods may be added:

  - `(handle [this context])`
  - `(enter [this context])`
  - `(leave [this context])`
  - `(error [this context exception])`

  For each of these, `definterceptor` will provide the matching protocol.

  Example:

  ```
  (definteceptor upcase []

    (enter [_ context] (update-in context [:request :query-params :id] string/upper-case)))
  ```

  The class name will match the record-name (which is typically kebab-cased).
  That is `(definterceptor foo-bar ...)` will generate the same class name as
  `(defrecord foo-bar ...)` even though this is not the normal Pascal Case naming
  convention for most records.

  The record implements the [[IntoInterceptor]] protocol; see [[component->interceptor]].

  The normal `map->record` and `->record` construction functions are generated."
  [record-name fields & opts+specs]
  (assert (simple-symbol? record-name))
  (let [interceptor-name (keyword (-> *ns* ns-name str) (name record-name))
        opts+specs'      (expand-opts+specs opts+specs)]
    `(defrecord ~record-name ~fields

       ~@opts+specs'

       i/IntoInterceptor

       (~'-interceptor [~'this]
         (component->interceptor ~interceptor-name ~'this)))))


