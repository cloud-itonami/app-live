(ns app-live.view
  "この appview の説明ページ。純 hiccup。

  基盤は `jp-go-dds`(デジタル庁デザインシステム) —— superproject の
  skill `kotoba-uiux` が定める新規 UI の base。色・寸法は `--hig-*` トークン
  契約で書き、raw hex も px フォントサイズも置かない。

  **表示する事実は引数で受け取る。ページの中に焼かない。**
  これは装飾の都合ではなく、移行前のページが持っていた欠陥そのものへの答えで
  ある —— `+page.svelte` は `routeCount: 0` / `routes: []` / `vars: []` を
  literal で持っており、同じ repo の `wrangler.jsonc` が 2 つの var を宣言し、
  隣の `+server.ts` が XRPC を実際に処理していることに気づけなかった。ここでは
  route 表と設定を渡す側が持ち、ページは描くだけなので、両者がずれる余地が無い。"
  (:require [jp-go-dds.core :as dds]
            [jp-go-dds.page :as page]
            [jp-go-dds.tokens :as tokens]
            [clojure.string :as str]))

(def app-css
  "app 固有の最小 CSS。`--hig-*` 契約だけを使う(bridge が DADS の上に再定義する)。
  DADS を base にした app の下には `shitsuke.hig` が居ないので、bridge が運んで
  いないトークンは何にも解決しない —— 使うのは運ばれている 71 個の中だけ。"
  (str/join
   "\n"
   [".live-lede { color: var(--hig-color-secondary-label); max-width: 42rem; }"
    ".live-note { color: var(--hig-color-secondary-label); font-size: var(--hig-text-footnote-font-size); }"
    ".live-mono { font-family: var(--hig-font-mono); }"]))

(defn- route-rows [routes]
  (mapv (fn [r]
          [(str/upper-case (name (:route/method r)))
           [:span {:class "live-mono"} (:route/path r)]
           (:route/doc r)])
        routes))

(defn body
  "opts:
   :routes    app-live.route/routes（この Worker が実際に答えるもの）
   :vars      wrangler が渡した env のキー（**キー名だけ**。値は出さない）
   :mcp-url   XRPC の中継先（route/mcp-router-url の戻り値。**値そのもの**）
   :built-at  bundle のビルド時刻（不明なら nil）"
  [{:keys [routes vars mcp-url built-at]}]
  (dds/container
   (dds/section
    {}
    (dds/heading 1 "app-live — ライブ配信ルームのカタログ")
    [:p {:class "live-lede"}
     "配信ルームと配信予定（room / schedule）の公開カタログの appview である。"
     "投げ銭（sendCheer）と AI VTuber のアバター生成はここには無く、"
     "etzhayyim 側に残っている。"])

   (dds/section
    {:title "この面が答えるもの"}
    (dds/table {:caption "公開ルート"
                :headers ["METHOD" "PATH" "何をするか"]
                :rows (route-rows routes)})
    [:p {:class "live-note"}
     "この表は Worker の route 表そのものから描いている。ページに焼いた値では"
     "ないので、実際に答えるものと表示がずれない。"])

   (dds/section
    {:title "実行時の設定"}
    (if (seq vars)
      [:div (into [:p] (interpose " "
                                  (map (fn [k] (dds/chip-label (name k))) vars)))
       [:p {:class "live-note"}
        "キー名のみ。**ただし下の中継先だけは値そのもの**（"
        [:span {:class "live-mono"} "AGENTGATEWAY_MCP_ROUTER_URL"]
        "）—— どこへ中継するかは運用者が見る必要があるので意図的に出している。"
        "それ以外の値は出さない。"]]
      [:p {:class "live-note"} "env が渡されていない（ローカル描画）。"])
    [:p {:class "live-note"} "XRPC の中継先: "
     [:span {:class "live-mono"} mcp-url]])

   (dds/section
    {:title "現在地"}
    [:p {:class "live-lede"}
     "この appview は TypeScript/Svelte から ClojureScript へ移行済み。"
     "deploy される bundle は、いま読んでいるソースからコンパイルされたもので"
     "ある（docs/adr/0001）。"]
    [:p {:class "live-note"}
     "この repo にはこの Worker が配信しないものも入っている —— "
     [:span {:class "live-mono"} "kotoba/"]
     "（room/schedule レジストリの TypeScript 実装。独立した package で、"
     "この bundle には 1 バイトも入らない）と "
     [:span {:class "live-mono"} "static/live-v1/"]
     "（別 Worker が配信していた wasm の観客シェル）。README.md を参照。"]
    (when built-at
      [:p {:class "live-note"} "bundle build: " built-at]))))

(defn render
  "完全な HTML 文書。`css` は呼び出し側が渡す(ライブラリは I/O を持たない)。"
  [{:keys [css] :as opts}]
  (page/->page
   {:title "app-live — ライブ配信ルームのカタログ"
    :description "配信ルームと配信予定の公開カタログの appview。"
    :lang "ja"
    :css css
    :app-css (str tokens/bridge-css "\n" app-css)}
   (body opts)))
