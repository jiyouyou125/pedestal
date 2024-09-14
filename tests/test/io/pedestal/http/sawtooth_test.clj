; Copyright 2024 Nubank NA
;
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0)
; which can be found in the file epl-v10.html at the root of this distribution.
;
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.saw
;
; You must not remove this notice, or any other, from this software.

(ns io.pedestal.http.sawtooth-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is]]
            [io.pedestal.http.route.definition.table :as table]
            [io.pedestal.http.route.router :as router]
            [com.walmartlabs.test-reporting :refer [reporting]]
            [io.pedestal.http.route.sawtooth :as sawtooth]
            [io.pedestal.http.route.sawtooth.impl :as impl]))

;; Placeholders for handlers in the routing table

(defn- get-users [])
(defn- get-user [])
(defn- create-user [])
(defn- get-user-collection [])
(defn- stats [])
(defn- get-resource [])
(defn- head-resource [])
(defn- shutdown [])
(defn- internal [])
(defn- monitor [])
(defn- root [])

(def routing-table
  (concat
    (table/table-routes
      {:host "example.com" :scheme :https}
      [["/user" :get `get-users]
       ["/user/:user-id" :get `get-user :constraints {:user-id #"[0-9]+"}]
       ["/user/:user-id" :post `create-user :constraints {:user-id #"[0-9]+"}]
       ["/user/:user-id/collection" :get `get-user-collection :constraints {:user-id #"[0-9]+"}]])
    (table/table-routes
      [["/api/stats" :get `stats]
       ["/api/shutdown" :post `shutdown]
       ["/resources/*path" :get `get-resource]
       ["/resources/*path" :head `head-resource]])
    (table/table-routes
      {:port 9999}
      [["/" :get `root]
       ["/internal" :get `internal]
       ["/internal/monitor" :any `monitor]])))

(deftest routing-table-as-expected
  (is (= [{:host       "example.com"
           :method     :post
           :path       "/user/:user-id"
           :route-name :io.pedestal.http.sawtooth-test/create-user
           :scheme     :https}
          {:method     :get
           :path       "/resources/*path"
           :route-name :io.pedestal.http.sawtooth-test/get-resource}
          {:host       "example.com"
           :method     :get
           :path       "/user/:user-id"
           :route-name :io.pedestal.http.sawtooth-test/get-user
           :scheme     :https}
          {:host       "example.com"
           :method     :get
           :path       "/user/:user-id/collection"
           :route-name :io.pedestal.http.sawtooth-test/get-user-collection
           :scheme     :https}
          {:host       "example.com"
           :method     :get
           :path       "/user"
           :route-name :io.pedestal.http.sawtooth-test/get-users
           :scheme     :https}
          {:method     :head
           :path       "/resources/*path"
           :route-name :io.pedestal.http.sawtooth-test/head-resource}
          {:method     :get
           :path       "/internal"
           :port       9999
           :route-name :io.pedestal.http.sawtooth-test/internal}
          {:method     :any
           :path       "/internal/monitor"
           :port       9999
           :route-name :io.pedestal.http.sawtooth-test/monitor}
          {:method     :get
           :path       "/"
           :port       9999
           :route-name :io.pedestal.http.sawtooth-test/root}
          {:method     :post
           :path       "/api/shutdown"
           :route-name :io.pedestal.http.sawtooth-test/shutdown}
          {:method     :get
           :path       "/api/stats"
           :route-name :io.pedestal.http.sawtooth-test/stats}]
         (->> routing-table
              (mapv #(select-keys % [:host :method :path :scheme :port :route-name]))
              (sort-by :route-name)
              vec))))

(defn- request
  [method path & {:as kvs}]
  (merge {:request-method method
          :scheme         :http
          :path-info      path}
         (set/rename-keys kvs {:host :server-name
                               :port :server-port})))

(def requests
  [(request :get "/user/9999" :host "example.com") nil      ; wrong scheme
   (request :get "/user/9999" :host "example.com" :scheme :https) [::get-user {:user-id "9999"}]
   (request :head "/user/9999" :host "example.com" :scheme :https) nil ; wrong method
   (request :get "/user/fred" :host "example.com" :scheme :https) nil ; violates path constraint
   (request :get "/resources") nil                          ; incomplete path
   (request :get "/resources/") nil
   (request :get "/") nil                                   ; wrong port
   (request :get "/" :port 9999) [::root nil]
   (request :get "/resources/assets/style.css") [::get-resource {:path "assets/style.css"}]
   (request :head "/resources/assets/site.js") [::head-resource {:path "assets/site.js"}]
   (request :get "/api/stats") [::stats nil]
   (request :get "/api/stats" :host "other.org" :scheme :https :port 9997) [::stats nil] ; Agnostic to all that
   (request :get "/internal") nil                           ; wrong port
   (request :get "/internal" :port 9999) [::internal nil]   ; correct port
   (request :get "/internal/other" :port 9999) nil
   (request :patch "/internal/monitor" :port 9999) [::monitor nil]
   (request ::anything "/internal/monitor" :port 9999) [::monitor nil]])

(defn- attempt-request
  [router request]
  (when-let [matched (router/find-route router request)]
    [(:route-name matched) (:path-params matched)]))

;; TODO: Get sawtooth to compare with prefix-tree, if we can get that working.
;; prefix-tree keeps blowing up on what looks like valid input.
;; Need this to get timing comparison!

(deftest sawtooth-queries
  (let [sawtooth (sawtooth/router routing-table)]
    (doseq [[request expected] (partition 2 requests)]
      (reporting request
                 (is (= expected
                        (attempt-request sawtooth request)))))))

(defn- route
  [route-name method path & {:as kvs}]
  (merge {:route-name route-name
          :path       path
          :method     method} kvs))

(defn- conflicts
  [& routes]
  (let [[_ conflicts] (impl/create-matcher-from-routes routes)]
    conflicts))


(deftest literal-paths-no-conflict
  (is (=
        nil
        (conflicts
          (route :get-users :get "/users")
          (route :get-pages :get "/pages")
          ;; Not a conflict, not same method
          (route :new-user :post "/users")))))

(deftest literal-path-conflicts

  (is (=
        {:get-users #{:get-pages}}
        (conflicts
          (route :get-users :get "/users")
          (route :get-pages :get "/users")
          ;; Not a conflict, not same method
          (route :new-user :post "/users")))))

(deftest param-path-vs-literal-path-no-conflict
  (is (=
        {:get-user #{:get-user-stats}
         :get-page #{:get-page-stats}}
        (conflicts
          (route :get-users :get "/users")
          (route :get-user :get "/users/:id")
          (route :get-user-stats :get "/users/stats")
          (route :get-page :get "/pages/:id")
          (route :get-page-stats :get "/pages/stats")
          ; Not conflicts, different methods:
          (route :new-user :post "/users")
          (route :update-user :post "/users/:id")))))

