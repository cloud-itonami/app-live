(ns app-live.route-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [app-live.route :as route]
            [app-live.view :as view]))

(deftest dispatch-page-and-health
  (is (= :page (:action (route/dispatch "GET" "/"))))
  (is (= :health (:action (route/dispatch "GET" "/health"))))
  (is (= :method-not-allowed (:action (route/dispatch "POST" "/health"))))
  (is (= :not-found (:action (route/dispatch "GET" "/nope"))))
  (testing "移行前は SvelteKit の static asset が /live-v1/… を答えていたが、
            それは別 Worker の話で、この設定では assets が SvelteKit の client
            ディレクトリを指していた。ここでは 404 になる。"
    (is (= :not-found (:action (route/dispatch "GET" "/live-v1/kami_app_live.js"))))))

(deftest dispatch-xrpc
  (testing "nsid をそのまま通す"
    (is (= {:action :xrpc :nsid "com.etzhayyim.apps.live.joinRoom"}
           (route/dispatch "POST" "/xrpc/com.etzhayyim.apps.live.joinRoom"))))
  (testing "空だけが 400。多段は移行前と同じく転送する（絞るのは方針変更）"
    (is (= :bad-request (:action (route/dispatch "POST" "/xrpc/"))))
    (is (= "Missing XRPC method" (:reason (route/dispatch "POST" "/xrpc/"))))
    (is (= {:action :xrpc :nsid "a/b"} (route/dispatch "POST" "/xrpc/a/b"))))
  (testing "preflight と method"
    (is (= :cors-preflight (:action (route/dispatch "OPTIONS" "/xrpc/x"))))
    (is (= :method-not-allowed (:action (route/dispatch "GET" "/xrpc/x"))))))

(deftest mcp-url-resolution
  (is (= "https://mcp.etzhayyim.com/xrpc/com.etzhayyim.mcp.message"
         (route/mcp-router-url {})))
  (is (= "https://a.example/x" (route/mcp-router-url {:AGENTGATEWAY_MCP_ROUTER_URL "https://a.example/x/"})))
  (testing "空白だけの設定は未設定として扱う"
    (is (= "https://b.example" (route/mcp-router-url {:AGENTGATEWAY_MCP_ROUTER_URL "   "
                                                      :MCP_ROUTER_URL "https://b.example"})))))

(deftest unwrap
  (is (= {:ok? true :value {:a 1}} (route/unwrap-mcp {:result {:structuredContent {:a 1}}})))
  (is (= {:ok? true :value {:a 1}} (route/unwrap-mcp {:result {:a 1}})))
  (is (false? (:ok? (route/unwrap-mcp {:error {:message "boom"}})))))

(deftest page-shows-the-real-routes
  (testing "ページは route 表から描く。0 を焼かない（移行前の +page.svelte の欠陥）"
    (let [html (view/render {:css "/*x*/" :routes route/routes
                             :vars [:APP_FRAMEWORK :AGENTGATEWAY_MCP_ROUTER_URL]
                             :mcp-url "https://mcp.example/x"})]
      (doseq [r route/routes]
        (is (str/includes? html (:route/path r))
            (str (:route/path r) " がページに出ていない")))
      (is (str/includes? html "APP_FRAMEWORK"))
      (is (str/includes? html "https://mcp.example/x"))
      (testing "移行前の literal 文言が残っていない"
        (is (not (str/includes? html "No public route is declared")))
        (is (not (str/includes? html "No public vars are declared")))))))

(deftest page-renders-what-it-is-handed
  (testing "route 表を差し替えると、ページの中身もそれに従う（焼いていない証拠）"
    (let [html (view/render {:css "" :routes [{:route/path "/only-this"
                                               :route/method :get
                                               :route/kind :page
                                               :route/doc "差し替えた表"}]
                             :vars [] :mcp-url "https://x.invalid"})]
      (is (str/includes? html "/only-this"))
      (is (not (str/includes? html "/health"))))))
