(ns app-live.worker
  "Cloudflare Worker の入口。**この repo の appview で唯一 Request/Response に
  触る層。**

  ここには判断を置かない —— どのハンドラが答えるかは `app-live.route/dispatch`
  が決め、ページの中身は `app-live.view` が組む。どちらも `.cljc` なので、
  ブラウザもビルドも無しにテストできる。

  wrangler.jsonc の `main` は `dist/worker.js` を指し、それはこの名前空間を
  コンパイルしたものである。移行前は SvelteKit のビルド出力
  （`svelte/.svelte-kit/cloudflare/_worker.js`）を指しており、それは tree に
  無く、`svelte/` は install できないので作ることもできなかった
  （docs/adr/0001）。

  `aget` を使うのは `:advanced-optimization` 下で env のキーが潰れないため
  （先例 `listingops.edge.worker` と同じ約束）。"
  (:require [app-live.route :as route]
            [app-live.view :as view]
            [shadow.resource :as rc]
            [clojure.string :as str]))

(def ^:private dds-css
  "DADS の CSS はビルド時に bundle へ焼く。外部リクエストゼロが design system
  の方針で、Worker から resource を読む経路も無い。"
  (rc/inline "jp_go_dds/dds.css"))

(defn- ->response [body {:keys [status content-type cache extra]}]
  (js/Response.
   body
   #js {:status status
        :headers (clj->js (merge {"content-type" content-type
                                  "cache-control" (or cache "no-store")}
                                 extra))}))

(defn- json [body status]
  (->response (js/JSON.stringify (clj->js body))
              {:status status :content-type "application/json; charset=utf-8"}))

(defn- env->map
  "env を keyword キーの map にする。**ページに渡すのはキーだけ**
  （`page-response` を見よ）。値をここで落とさないのは
  `route/mcp-router-url` が値を要るからで、外へ出す判断は呼び出し側にある。"
  [env]
  (if env
    (into {} (map (fn [k] [(keyword k) (aget env k)])) (js/Object.keys env))
    {}))

(defn- cors-headers []
  {"access-control-allow-origin" "*"
   "access-control-allow-methods" "POST,OPTIONS"
   "access-control-allow-headers" "content-type,authorization"
   "access-control-max-age" "86400"})

(defn- proxy-xrpc
  "XRPC を MCP router へ中継する。移行前に deploy されていた SvelteKit の
  route と同じ形（jsonrpc の封筒に包み、result/structuredContent を剥がす）。

  `x-etzhayyim-bff` の値だけは移行前の `sveltekit-edge-bff` から
  `cljs-worker` に変えた —— このヘッダは中継元が何であるかを名乗るもので、
  SvelteKit でなくなった後も SvelteKit と名乗るのは嘘になる。上流はこれを
  ルーティングに使っていない（jsonrpc の封筒だけを読む）。docs/adr/0001。"
  [req env nsid]
  (let [url (route/mcp-router-url (env->map env))]
    (-> (.json req)
        (.catch (fn [_] #js {}))
        (.then
         (fn [input]
           (js/fetch url
                     #js {:method "POST"
                          :headers #js {"content-type" "application/json"
                                        "x-etzhayyim-bff" "cljs-worker"
                                        "x-etzhayyim-xrpc-method" nsid}
                          :body (js/JSON.stringify
                                 #js {:jsonrpc "2.0"
                                      :id (.randomUUID js/crypto)
                                      :method "tools/call"
                                      :params #js {:name nsid :arguments input}})})))
        (.then (fn [resp]
                 (-> (.text resp)
                     (.then (fn [text]
                              (let [payload (try (when (seq text) (js/JSON.parse text))
                                                 (catch :default _ text))
                                    clj-payload (js->clj payload :keywordize-keys true)]
                                (if-not (.-ok resp)
                                  (json {:error "MCP router request failed"
                                         :upstream clj-payload}
                                        (.-status resp))
                                  (let [{:keys [ok? value error upstream]} (route/unwrap-mcp clj-payload)]
                                    (if ok?
                                      (json (or value {}) 200)
                                      (json {:error error :upstream upstream} 502))))))))))
        (.catch (fn [e]
                  ;; 到達できなかったことを 200 で隠さない。移行時点で
                  ;; mcp.etzhayyim.com は A レコードを返さないので、これは
                  ;; 想像上の経路ではなく今日の既定の結末である。
                  (json {:error "MCP router unreachable"
                         :detail (str (.-message e))
                         :url url}
                        502))))))

(defn- page-response [env]
  (let [e (env->map env)]
    (->response
     (view/render {:css dds-css
                   :routes route/routes
                   :vars (sort (keys e))
                   :mcp-url (route/mcp-router-url e)
                   :built-at nil})
     {:status 200
      :content-type "text/html; charset=utf-8"
      :cache "public, max-age=60"})))

(defn fetch-handler [req env _ctx]
  (let [url (js/URL. (.-url req))
        path (.-pathname url)
        {:keys [action nsid allow reason]} (route/dispatch (.-method req) path)]
    (case action
      :page   (page-response env)
      :health (json {:ok true :app "app-live" :runtime "cljs"
                     :routes (mapv :route/path route/routes)}
                    200)
      :xrpc   (proxy-xrpc req env nsid)
      :cors-preflight (->response nil {:status 204 :content-type "text/plain"
                                       :extra (cors-headers)})
      :bad-request (json {:error reason} 400)
      :method-not-allowed (->response (js/JSON.stringify #js {:error "Method Not Allowed"})
                                      {:status 405
                                       :content-type "application/json; charset=utf-8"
                                       :extra {"allow" allow}})
      (json {:error "Not Found"
             :routes (mapv (fn [r] (str (str/upper-case (name (:route/method r)))
                                        " " (:route/path r)))
                           route/routes)}
            404))))

(def handler #js {:fetch fetch-handler})
