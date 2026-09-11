# Operator quickstart — app-live

Every command below was run, and its **real output** is quoted. Where a command
**fails**, that is recorded as the expected result rather than omitted — the
failure is the thing an operator most needs to know in advance.

Measured on: macOS (darwin-arm64), Node **v26.3.0**, npm **11.16.0**,
pnpm **10.26.2**. The npm failure in §6 is version-sensitive; the rest is not.

This repo holds **two independent things**, and they have different toolchains:

| | Toolchain | State |
|---|---|---|
| **the appview** (`src/app_live/`, `wrangler.jsonc`) | ClojureScript + shadow-cljs + nbb | builds, tests, smokes — §1–§5 |
| **the catalog library** (`kotoba/`) | TypeScript + vitest | tests pass, but `npm install` does not — §6–§8 |

`static/live-v1/` is a prebuilt wasm bundle with no source here and no build
step; this Worker does not serve it. See `README.md`.

## 1. Clone and check that the prose is true

```bash
git clone https://github.com/cloud-itonami/app-live
cd app-live
npx --yes kbb --backend sci scripts/verify-docs-claims.cljk .    # <dir> goes FIRST
```

Actual output (tail):

```
SCANNED	31
PASS	tracked-files	expected=31	actual=31
PASS	preserved-files-unchanged	expected=[]	actual=[]
PASS	removed-by-migration-absent	expected=[]	actual=[]
PASS	appview-ts-files	expected=0	actual=0
PASS	appview-canonical-files	expected=4	actual=4
PASS	kotoba-files	expected=7	actual=7
PASS	static-files	expected=5	actual=5
PASS	wrangler-main	expected="dist/worker.js"	actual="dist/worker.js"
PASS	warnings-are-errors-in-compiler-options	expected=true	actual=true
OK	every claim in README.md and docs/operator-quickstart.md holds
```

exit 0 = everything agrees · 1 = a claim is false · **2 = could not answer**
(kept distinct from 0: a verifier that cannot read the tree must not report a
pass).

## 2. Run the appview tests (no build, no browser)

The decision (`route.cljc`) and the page (`view.cljc`) are pure `.cljc`, so nbb
alone runs them.

```bash
K=~/github/com-junkawasaki/orgs/kotoba-lang
CP="src:test:$K/jp-go-digital-design-system/src:$K/html/src:$K/css/src"
cat > /tmp/run.cljs <<'RUN'
(require '[cljs.test :refer [run-tests]] 'app-live.route-test)
(run-tests 'app-live.route-test)
RUN
npx --yes kbb --backend sci --classpath "$CP" /tmp/run.cljs
```

Actual output:

```
Testing app-live.route-test

Ran 6 tests containing 26 assertions.
0 failures, 0 errors.
```

What they pin: `/xrpc/` is a 400 **only when the nsid is empty** (`/xrpc/a/b` is
relayed, exactly as the pre-migration rest parameter did — narrowing it would be
a policy change, not a migration), the MCP router URL resolution (whitespace-only
config counts as unset), how `result` / `structuredContent` are unwrapped, and
**that the page is drawn from the route table** — hand it a different table and
the page changes; it would fail if any count were baked in.

## 3. Render the page and score it

```bash
K=~/github/com-junkawasaki/orgs/kotoba-lang
CP="src:$K/jp-go-digital-design-system/src:$K/html/src:$K/css/src"
cat > /tmp/render.cljs <<'REN'
(require '["node:fs" :as fs] '[app-live.view :as view] '[app-live.route :as route])
(let [css (.readFileSync fs (str (.-DDS js/process.env) "/resources/jp_go_dds/dds.css") "utf8")]
  (.writeFileSync fs "/tmp/live-page.html"
    (view/render {:css css :routes route/routes
                  :vars [:APP_FRAMEWORK :AGENTGATEWAY_MCP_ROUTER_URL]
                  :mcp-url "https://mcp.etzhayyim.com/xrpc/com.etzhayyim.mcp.message"}))
  (println "ok"))
REN
DDS="$K/jp-go-digital-design-system" npx --yes kbb --backend sci --classpath "$CP" /tmp/render.cljs
cd $K/design-quality && npx --yes kbb --backend sci -m design-quality.cli score /tmp/live-page.html --min 95
```

Actual output (tail):

```
  100.00  /tmp/live-page.html
aggregate: 100.00
axes scored: 10 (viewport, safe-area, dynamic-viewport, tap-targets, focus-visible,
  reduced-motion, overflow-guard, color-scheme, responsive, semantics)
NOT scored: input-zoom, contrast — pass --extra-axes to include the optional ones
gate: aggregate 100.00 >= min 95.00 -> PASS
```

With `--extra-axes` (all 12): also **100.00**, PASS.

**Read that "NOT scored" line.** And know what the score does not say: rendering
this same page with the stylesheet stripped entirely still scores **96.63 and
passes the 95 gate** (measured). The design system's presence is asserted by the
smoke in §5, not by this number.

## 4. Build the bundle

**Heavy builds are serialised workspace-wide** (superproject `CLAUDE.md`,
resource governor). Do not call shadow directly:

```bash
node ~/github/com-junkawasaki/scripts/resource-guard.mjs run build -- \
  npx --yes amu compile --target wasm32-browser worker
ls -la dist/worker.js
```

Actual output (tail):

```
[:worker] Build completed. (55 files, 12 compiled, 0 warnings, 9.12s)
-rw-r--r--  1 junkawasaki  staff  246094 dist/worker.js
```

Exit 2 from the guard is **a queue, not a failure** —
`resource-guard: build is already running (pid=…)` means wait, not work around.

### A broken var *fails* the build (measured 2026-08-18)

shadow treats an undeclared or renamed var as a **warning** and exits 0, shipping
the bundle anyway. "The build succeeded" is then not a check at all. This repo
sets `:warnings-as-errors true` **under `:compiler-options`** — not under
`:build-options`, where shadow silently ignores it, which would itself be a fix
that cannot fail. The verifier parses `shadow-cljs.edn` as EDN to check the key's
position; it does not grep, because this file's own comments contain the string.

Demonstrated both ways on 2026-08-18, changing **one thing at a time**.
Renaming `route/dispatch` to `route/dispatchNOPE` in `worker.cljs` and
rebuilding:

```
Use of undeclared Var app-live.route/dispatchNOPE
{:warning :undeclared-var, :line 116, :column 45,
 :msg "Use of undeclared Var app-live.route/dispatchNOPE",
 :shadow.build.compiler/warning-as-error true}
```

Build `rc=1`. Then, with **the same broken var still in place**, moving only the
key to `:build-options`:

```
[:worker] Build completed. (55 files, 1 compiled, 1 warnings, 5.88s)
 Use of undeclared Var app-live.route/dispatchNOPE
```

Build `rc=0` — **shadow shipped the bundle.** What that bundle then does:

```
$ kbb --backend sci scripts/smoke-worker.cljk dist/worker.js
PASS	default export has fetch	expected=true	actual=true
UNDETERMINED	could not exercise the bundle: Cannot read properties of undefined (reading 'h')
exit 2
```

So the misplaced key is not a milder version of the fix — it is no fix at all,
and the only thing that noticed was the smoke.

## 5. Exercise the built bundle

```bash
npx --yes kbb --backend sci scripts/smoke-worker.cljk dist/worker.js
```

This is the **only** check that touches the artifact that gets deployed. The
tests in §2 pin the source's decisions; they cannot say the bundle answers in
the shape of a Worker (the export shape, `:advanced-optimization`, and the CSS
baked in by `shadow.resource/inline` only exist after a build).

Actual output:

```
PASS	default export has fetch	expected=true	actual=true
PASS	GET / status	expected=200	actual=200
PASS	GET / is html	expected=true	actual=true
PASS	page advertises /health	expected=true	actual=true
PASS	page advertises /xrpc/:nsid	expected=true	actual=true
PASS	page shows a var key	expected=true	actual=true
PASS	page hides other var values	expected=false	actual=false
PASS	page shows the relay target it uses	expected=true	actual=true
PASS	page uses the design system components	expected=true	actual=true
PASS	page carries the stylesheet itself	expected=true	actual=true
PASS	GET /health status	expected=200	actual=200
PASS	health names its routes	expected=true	actual=true
PASS	POST /xrpc/ status	expected=400	actual=400
PASS	POST /xrpc/ keeps the pre-migration message	expected=true	actual=true
PASS	OPTIONS preflight	expected=204	actual=204
PASS	unknown path	expected=404	actual=404
PASS	wrong method	expected=405	actual=405
OK	the built bundle answers as the route table says
```

Exit 0 = as expected · 1 = not as expected · **2 = could not judge** (no bundle
present; it prints `Refusing to report a pass` rather than passing quietly).

### 5.1 Run it in the real Workers runtime

`wrangler dev --local` runs workerd, which is stronger evidence than importing
the bundle in Node. This is how the removal of `compatibility_flags:
["nodejs_compat"]` was justified — the flag was `adapter-cloudflare`'s
requirement, and it was deleted only after every route answered without it.

```bash
npx --yes wrangler dev --local --port 8799 --ip 127.0.0.1
# in another shell:
curl -s -o /dev/null -w '%{http_code} %{content_type}\n' http://127.0.0.1:8799/
curl -s http://127.0.0.1:8799/health
curl -s -X POST http://127.0.0.1:8799/xrpc/
curl -s -X POST -H 'content-type: application/json' -d '{}' http://127.0.0.1:8799/xrpc/a/b
curl -s -X OPTIONS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8799/xrpc/x
curl -s http://127.0.0.1:8799/nope
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8799/live-v1/kami_app_live.js
```

Actual output:

```
200 text/html; charset=utf-8          # 81,856 bytes; dads-table x74, --color-primitive-blue x45
{"ok":true,"app":"app-live","runtime":"cljs","routes":["/","/health","/xrpc/:nsid"]}
{"error":"Missing XRPC method"}                                            # 400
{"error":"MCP router unreachable","detail":"internal error; reference = …",
 "url":"https://mcp.etzhayyim.com/xrpc/com.etzhayyim.mcp.message"}         # 502
204
{"error":"Not Found","routes":["GET /","GET /health","POST /xrpc/:nsid"]}  # 404
404                                                                        # static/ is not served
```

`POST /xrpc/a/b` and `POST /xrpc/com.etzhayyim.apps.live.joinRoom` behave
**identically** (both 502, naming the URL they tried): multi-segment paths are
relayed, as they were before the migration. The 502 is today's honest default —
`mcp.etzhayyim.com` has no DNS answer, and an unreachable relay must not be
flattened into a 200.

## 6. `kotoba/` — do not start with `npm install`, it fails

```bash
cd kotoba
npm install
```

Expected result — **this fails**, re-measured 2026-08-18 on npm 11.16.0:

```
npm error code 1
npm error git dep preparation failed
npm error npm error code EALLOWSCRIPTS
npm error npm error --allow-scripts is not allowed in project-scoped installs.
```

`pnpm install --ignore-scripts` does not rescue it either; it fails further down
the same dependency tree:

```
ERR_PNPM_MISSING_PACKAGE_NAME  Can't install
git+https://github.com/kotoba-lang/ipfs.git#main: Missing package name
```

Note this blocks *every* install in that directory, including unrelated
packages — npm prepares the declared git dependencies regardless of what you
asked it to add. `npm install vitest` fails with the same `EALLOWSCRIPTS`.

Why, and why it is not this repo's bug, is in
[`adr/2608180536-sdk-is-a-type-only-dependency.edn`](adr/2608180536-sdk-is-a-type-only-dependency.edn).
The short version: `@etzhayyim/sdk` is needed only for **types**, and it is the
only reason the package will not install.

**The appview does not depend on `kotoba/`**, so this blocker never stood in the
migration's way — §2–§5 run with `kotoba/` uninstalled.

## 7. Build a toolchain outside the project, and run the `kotoba/` suite

Because the project directory cannot be installed into, put the test runner
somewhere else. This directory is disposable.

```bash
mkdir -p /tmp/live-vitest && cd /tmp/live-vitest
printf '{"name":"live-vitest","private":true,"type":"module"}\n' > package.json
npm install vitest@^4.1.0                     # added 44 packages

cd <repo>/kotoba
mkdir -p node_modules/@etzhayyim
cp -R /tmp/live-vitest/node_modules/. node_modules/
git clone https://github.com/etzhayyim/com-etzhayyim-sdk-mock.git \
  node_modules/@etzhayyim/sdk-mock
git -C node_modules/@etzhayyim/sdk-mock checkout \
  c857ff9be5310bf433bfe1e8d3c0f677e213d667
node node_modules/vitest/vitest.mjs run
```

The commit is pinned to the same revision `kotoba/package.json` declares, so
this is the dependency the repo asks for, not a substitute. Output, measured in
the documentation pass that landed `ab57045`:

```
 Test Files  1 passed (1)
      Tests  3 passed (3)
   Duration  258ms
```

**3 passed** — with no `@etzhayyim/sdk` installed anywhere. What those three
tests cover:

1. rooms are created `scheduled`, flip to `live`, reject an unknown status,
   report `notFound` for a missing room, and are searchable by status /
   category / free text;
2. schedules require an existing room (`roomNotFound`), reject a fractional
   `durationMinutes` and an invalid status, and filter by room / status / since;
3. `coverage` rolls rooms and schedules up by status and category.

`node_modules/` is gitignored, so nothing tracked is modified by any of this.

## 8. `kotoba/` typecheck — this one does not pass

```bash
node node_modules/typescript/bin/tsc --noEmit
```

```
src/registry.ts(7,32): error TS2307: Cannot find module '@etzhayyim/sdk' or its
  corresponding type declarations.
src/registry.ts(112,14): error TS7006: Parameter 'r' implicitly has an 'any' type.
src/registry.ts(124,11): error TS7006: Parameter 'r' implicitly has an 'any' type.
src/registry.ts(161,14): error TS7006: Parameter 'r' implicitly has an 'any' type.
src/registry.ts(168,11): error TS7006: Parameter 'r' implicitly has an 'any' type.
```

There is **one** root cause: the unresolved `Etzhayyim` type. The four
implicit-`any` errors are downstream of it. Do not "fix" them by adding `any`
annotations — that would silence the signal while leaving the package just as
uninstallable.

## 9. Deploying

**Not attempted, and not attempted during the migration either.**
`wrangler dev --local` (§5.1) is as far as this went.

Per the workspace rule, a production deploy must run from a checkout that
contains `origin/main`: the last writer wins and deploys have no fast-forward
check.

Before deciding to deploy, note that `wrangler.jsonc` declares **no `routes`**,
and that `mcp.etzhayyim.com`, `live.etzhayyim.com` and `l1ve9pq4.etzhayyim.com`
all have no DNS answer (`dig +short`, 2026-08-18). Deploying or retiring this
surface is a separate decision from migrating it.
