#!/usr/bin/env nbb
;; verify-docs-claims — re-derive every number README.md and docs/operator-quickstart.md
;; state, from the tree itself, and fail when the tree and the prose disagree.
;;
;; Before the cljs migration this file's load-bearing claim was a GAP: the Worker
;; that would be deployed was `svelte/.svelte-kit/cloudflare/_worker.js`, a build
;; output that is not in the tree and that `svelte/` could not produce (it cannot
;; be installed -- docs/adr/2608180536). That gap is closed, so the claims now
;; assert the CLOSURE, and they are written so the gap cannot quietly come back:
;; the Svelte/TypeScript appview is asserted ABSENT BY NAME, not merely absent
;; from a byte total.
;;
;; It also pins the two things this repo holds that are NOT the appview and were
;; NOT migrated -- kotoba/ (a TypeScript domain library, its own package, in no
;; bundle) and static/ (a prebuilt wasm audience shell served by a different
;; Worker). They are pinned by file count AND byte total so they cannot grow
;; silently under cover of "the migration touched TypeScript".
;;
;; Usage:  nbb scripts/verify-docs-claims.cljs [<dir>]     (<dir> FIRST, default ".")
;; Exit:   0 every claim holds · 1 a claim is false · 2 could not answer

(require '["node:fs" :as fs]
         '["node:child_process" :as cp]
         '["node:crypto" :as crypto]
         '[cljs.reader :as reader]
         '[kotoba.lang.text :as str])

(def root (or (first (remove #(str/starts-with? % "--") *command-line-args*)) "."))

(def claims
  {:tracked-files 32
   :preserved-bytes 15331          ; the 5 inherited files still carried unchanged
   :svelte-artifacts 0             ; no .svelte / svelte.config / svelte/ path survives
   :sveltekit-compat-flags 0       ; nodejs_compat was adapter-cloudflare's
   :appview-ts-files 0             ; .ts outside kotoba/ and static/ -- the migrated surface
   :appview-canonical-files 4      ; .cljs/.cljc outside scripts/
   ;; NOT migrated, pinned so they cannot grow silently (README.md names both)
   :kotoba-files 7 :kotoba-bytes 19660
   :static-files 5  :static-bytes 400752
   :declared-vars 2
   :declared-routes 0              ; this Worker declares no route patterns at all
   :wrangler-main "dist/worker.js"
   :shadow-output-dir "dist"
   :shadow-export "app-live.worker/handler"})

;; Inherited files this repository still carries BYTE-IDENTICAL.
;; wrangler.jsonc, migration.edn, README.md and docs/operator-quickstart.md are
;; left OUT of this set deliberately: the migration changed all four on purpose,
;; and they are checked by content below instead. That separation is the point --
;; it distinguishes an intended edit from a drifted one.
(def preserved
  {"MIGRATION-TODO.md" "c326734a2f544bb1f3f85525ee1e7035278de1b631271ab302c16ab3e7ad310d"
   "NOTICE" "bae68743feb911cbedcc745b136e444d3595854e4324f59e7cc9438ccda13d49"
   "README.edn" "850570e70b61ef65d69b8016701790428bd8066f125eb4c9c72100cb5440bcea"
   "RESUME.edn" "a53289210cee23888c9fd8871cd255cc08216a4cb9440adf708121e06006531d"
   "docs/adr/2608180536-sdk-is-a-type-only-dependency.edn"
   "5c323572836a377ecafb9b787349ddbc6c59a088f24fc2deb2b3b6afd0a0b741"})

(def undetermined (atom []))
(def failures (atom []))
(defn undet! [m] (swap! undetermined conj m))

(defn tracked-files []
  (try (->> (.execSync cp "git -c core.fsmonitor=false ls-files" #js {:cwd root :encoding "utf8"})
            str/split-lines (remove str/blank?) vec)
       (catch :default e (undet! (str "git ls-files failed: " (.-message e))) nil)))
(defn slurp* [rel] (try (.readFileSync fs (str root "/" rel) "utf8") (catch :default _ nil)))
(defn bytes-of [rel] (try (.-size (.statSync fs (str root "/" rel))) (catch :default _ nil)))
(defn sha256 [rel]
  (try (-> (.createHash crypto "sha256") (.update (.readFileSync fs (str root "/" rel))) (.digest "hex"))
       (catch :default _ nil)))
(defn strip-jsonc [s] (str/replace s #"(?m)^\s*//.*$" ""))

(defn check! [label expected actual]
  (let [ok (= expected actual)]
    (println (str (if ok "PASS" "FAIL") "\t" (name label)
                  "\texpected=" (pr-str expected) "\tactual=" (pr-str actual)))
    (when-not ok (swap! failures conj label))
    ok))

;; The list of removed paths is READ FROM migration.edn, not duplicated here, so
;; the identity declaration and the check are one value. If the declaration is
;; missing or empty the run is UNDETERMINED -- an empty list would otherwise make
;; "nothing came back" trivially true, which is the shape of a check that cannot
;; fail.
(def removed-by-migration
  (let [m (try (reader/read-string (or (slurp* "migration.edn") "")) (catch :default _ nil))
        v (get-in m [:identity :removed-by-migration])]
    (cond
      (nil? m) (do (undet! "migration.edn unreadable") nil)
      (or (not (vector? v)) (empty? v))
      (do (undet! "migration.edn declares no :removed-by-migration -- refusing to report absence") nil)
      :else v)))

(let [files (tracked-files)]
  (when (nil? files) (println "UNDETERMINED\tcould not list tracked files") (js/process.exit 2))
  (println (str "SCANNED\t" (count files)))
  (when (zero? (count files)) (println "UNDETERMINED\tscanned 0 files") (js/process.exit 2))

  (let [sizes (into {} (map (juxt identity bytes-of)) files)]
    (when-let [bad (seq (keep (fn [[f s]] (when (nil? s) f)) sizes))]
      (undet! (str "tracked but unreadable: " (str/join ", " bad))))

    (check! :tracked-files (:tracked-files claims) (count files))
    (check! :preserved-bytes (:preserved-bytes claims)
            (reduce + 0 (keep #(get sizes %) (keys preserved))))
    (check! :preserved-files-unchanged []
            (vec (keep (fn [[f want]] (let [got (sha256 f)]
                                        (when-not (= want got) (str f " " (or got "MISSING")))))
                       preserved)))

    ;; the Svelte appview is gone, by name
    (when removed-by-migration
      (check! :removed-by-migration-absent []
              (vec (filter #(some? (bytes-of %)) removed-by-migration)))
      (check! :removed-by-migration-count 7 (count removed-by-migration)))

    ;; ...and must not come back under ANY name -- a new .svelte file, a
    ;; svelte.config, or anything under a svelte/ directory.
    (check! :svelte-artifacts (:svelte-artifacts claims)
            (count (filter #(or (str/ends-with? % ".svelte")
                                (str/includes? % "svelte.config")
                                (str/starts-with? % "svelte/"))
                           files)))

    ;; language of the appview. Scoped by MEASUREMENT, not by file extension:
    ;; kotoba/ and static/ hold TypeScript that is in no bundle, that nothing
    ;; being replaced referenced, and that the migration deliberately kept.
    (let [appview (remove #(or (str/starts-with? % "kotoba/")
                               (str/starts-with? % "static/")
                               (str/starts-with? % "scripts/"))
                          files)]
      (check! :appview-ts-files (:appview-ts-files claims)
              (count (filter #(str/ends-with? % ".ts") appview)))
      (check! :appview-canonical-files (:appview-canonical-files claims)
              (count (filter #(re-find #"\.(cljs|cljc|clj|kotoba)$" %) appview))))

    ;; the two kept trees, pinned so they cannot grow silently
    (doseq [[prefix cnt-k byte-k] [["kotoba/" :kotoba-files :kotoba-bytes]
                                   ["static/" :static-files :static-bytes]]]
      (let [own (filter #(str/starts-with? % prefix) files)]
        (check! cnt-k (get claims cnt-k) (count own))
        (check! byte-k (get claims byte-k) (reduce + 0 (keep #(get sizes %) own)))))

    ;; the appview build sees only src/ -- that is WHY kotoba/ and static/ are in
    ;; no bundle, and it is a structural fact rather than an assurance.
    (let [dep (slurp* "deps.edn")]
      (if (nil? dep)
        (undet! "deps.edn unreadable")
        (check! :appview-paths-are-src-only true
                (some? (re-find #"\{:paths\s+\[\"src\"\]" dep)))))

    ;; the deployed bundle is built from the source in this tree
    (let [w (some-> (slurp* "wrangler.jsonc") strip-jsonc)
          sh (slurp* "shadow-cljs.edn")]
      (if (or (nil? w) (nil? sh))
        (undet! "wrangler.jsonc or shadow-cljs.edn unreadable")
        (let [j (js->clj (.parse js/JSON w) :keywordize-keys false)]
          (check! :wrangler-main (:wrangler-main claims) (get j "main"))
          (check! :declared-vars (:declared-vars claims) (count (get j "vars")))
          (check! :declared-routes (:declared-routes claims) (count (get j "routes")))
          ;; the old config served a SvelteKit client dir that no longer exists
          (check! :no-stale-assets-binding true (nil? (get j "assets")))
          (check! :sveltekit-compat-flags (:sveltekit-compat-flags claims)
                  (count (filter #{"nodejs_compat" "nodejs_als"}
                                 (or (get j "compatibility_flags") []))))
          (check! :app-framework-names-cljs "cljs-esm-worker" (get-in j ["vars" "APP_FRAMEWORK"]))
          ;; :warnings-as-errors must sit under :compiler-options. Parsed as EDN,
          ;; NOT grepped -- this file's own comments contain the string, so a grep
          ;; would be a check that cannot fail.
          (let [cfg (try (reader/read-string sh) (catch :default _ nil))]
            (if (nil? cfg)
              (undet! "shadow-cljs.edn does not read as EDN")
              (check! :warnings-are-errors-in-compiler-options true
                      (true? (get-in cfg [:builds :worker :compiler-options :warnings-as-errors])))))
          (check! :shadow-builds-that-main true
                  (and (str/includes? sh (str ":output-dir \"" (:shadow-output-dir claims) "\""))
                       (str/includes? sh (:shadow-export claims))
                       (= (get j "main") (str (:shadow-output-dir claims) "/worker.js")))))))

    ;; The page renders the route TABLE rather than a baked count -- the defect the
    ;; pre-migration +page.svelte had was a literal `routeCount: 0` / `routes: []`.
    ;; Asserted structurally (the view takes :routes, the worker passes the real
    ;; table) and NOT by forbidding a substring: a check a comment can fail is a
    ;; check about prose.
    (let [v (slurp* "src/app_live/view.cljc")
          w (slurp* "src/app_live/worker.cljs")]
      (if (or (nil? v) (nil? w))
        (undet! "view.cljc or worker.cljs unreadable")
        (check! :page-renders-route-table true
                (and (str/includes? v "[{:keys [routes vars mcp-url built-at]}]")
                     (str/includes? v "(route-rows routes)")
                     (str/includes? w ":routes route/routes")))))

    ;; the prose names the pipeline it documents
    (let [r (slurp* "README.md") q (slurp* "docs/operator-quickstart.md")]
      (if (or (nil? r) (nil? q))
        (undet! "README.md or docs/operator-quickstart.md unreadable")
        (do (check! :readme-names-the-bundle true
                    (and (str/includes? r "dist/worker.js")
                         (str/includes? r "src/app_live/worker.cljs")))
            (check! :quickstart-names-the-build true
                    (and (str/includes? q "shadow-cljs release worker")
                         (str/includes? q "scripts/smoke-worker.cljs"))))))))

(let [u @undetermined f @failures]
  (when (seq u)
    (doseq [m u] (println (str "UNDETERMINED\t" m)))
    (println "Refusing to report a pass: the tree could not be read completely.")
    (js/process.exit 2))
  (if (seq f)
    (do (println (str "FAILED\t" (count f) " claim(s): " (str/join ", " (map name f)))) (js/process.exit 1))
    (do (println "OK\tevery claim in README.md and docs/operator-quickstart.md holds") (js/process.exit 0))))
