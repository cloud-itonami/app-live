# app-live

**A live-streaming room catalog.** This repository holds the public half of a
live-show application: **rooms** (a streamer's channel) and **broadcast
schedules** (when that room goes live). Nothing here takes money, holds PII, or
generates avatars — see [Boundary](#boundary).

The name says `live` but not *which* live, so to be explicit: this is
`did:web:live.etzhayyim.com`, the room + schedule registry, backed by AT
Protocol PDS records. It is not a video pipeline and not a chat server.

**The appview was migrated from TypeScript/Svelte to ClojureScript on
2026-08-18** ([ADR-0001](docs/adr/0001-migrate-the-appview-from-typescript-to-clojurescript.edn)).
Every number below is re-derived from the tree by
`scripts/verify-docs-claims.cljs`, which fails when the prose and the tree
disagree.

## What gets deployed is the source you are reading

```
src/app_live/route.cljc    which handler answers  ← pure .cljc, tested
src/app_live/view.cljc     the page (jp-go-dds hiccup)  ← pure .cljc, tested
src/app_live/worker.cljs   the only layer touching Request/Response
        ↓ shadow-cljs :target :esm
dist/worker.js             ← what wrangler.jsonc's "main" points at
```

Before the migration `main` pointed at `svelte/.svelte-kit/cloudflare/_worker.js`
and `assets` at `svelte/.svelte-kit/cloudflare/client`. **Neither exists in this
tree**, and `svelte/` could not produce them — it has the same install blocker
as `kotoba/` (see below). So the handler that would be deployed could not be
built from this checkout at all, and the TypeScript a reader opened was in no
bundle. The verifier now checks that shadow's output dir, wrangler's `main` and
the exported namespace agree, and fails if they stop agreeing.

## Public routes

| METHOD | PATH | What it does |
|---|---|---|
| GET | `/` | this appview's description page |
| GET | `/health` | liveness — check from outside that the deployed surface answers |
| POST | `/xrpc/:nsid` | relay XRPC to the MCP router |
| OPTIONS | `/xrpc/*` | CORS preflight |

**This table comes from `app-live.route/routes`, and the page is drawn from the
same value.** The pre-migration `+page.svelte` carried `routeCount: 0`,
`routes: []` and `vars: []` as literals while the `wrangler.jsonc` beside it
declared two vars and the `+server.ts` beside it handled XRPC. The page could
not see its own neighbours. Now the route table is passed in, so display and
behaviour cannot drift.

`GET /health` is **new** — it did not exist before the migration. It is an
addition, named as one in ADR-0001, not something that was ported.

`POST /xrpc/a/b` is **relayed, not rejected.** The pre-migration route used
SvelteKit's rest parameter `[...path]`, so `event.params.path` was the whole
remaining path and only the empty string was a 400. Narrowing that to a single
segment would be a policy change wearing a migration's clothes.

## What is actually in here — 31 files

| Path | What it is | Migrated? |
|---|---|---|
| `src/app_live/` | The appview: route table, page, Worker entry. | **This is the migration.** |
| `test/app_live/` | 6 tests / 26 assertions, no browser and no build needed. | added |
| `scripts/` | `smoke-worker.cljs` (exercises the *built bundle*), `verify-docs-claims.cljs`. | added |
| `deps.edn` / `shadow-cljs.edn` | Build. `:warnings-as-errors` lives under `:compiler-options`. | added |
| `wrangler.jsonc` | Cloudflare Worker config, now serving `dist/worker.js`. | **changed on purpose** |
| `kotoba/` | Reference implementation of the catalog in TypeScript: `createRoom` / `setRoomStatus` / `getRoom` / `listRooms` / `addSchedule` / `listSchedules` / `coverage`. Its own `package.json`, its own vitest suite (3/3 pass). | **NOT migrated — see below** |
| `static/live-v1/` | A prebuilt `kami_app_live` wasm bundle (313 KB) + JS shim + the audience shell that boots it. | **NOT migrated — see below** |
| `RESUME.edn` | Migration notes inherited from the monorepo. | **Stale — see below.** |
| `MIGRATION-TODO.md` | Charter/substrate checklist inherited from the monorepo. | Checklist, largely unticked. |

**The appview holds 0 TypeScript files and 4 ClojureScript ones.** Before the
migration it was 2 to 0. Both numbers are verifier claims, so TypeScript coming
back fails the check — whether it returns to one of the seven removed paths
(`removed-by-migration-absent`, read from `migration.edn`) or arrives under a
new name (`appview-ts-files`).

### Two things here are not the appview, and were deliberately kept

A migration instruction that says "remove the TypeScript" would, applied by
extension to this repo, delete most of it. That is destruction, not migration.
Scoped by measurement instead:

| Kept | Measured reason |
|---|---|
| `kotoba/` — 7 files, 19,660 bytes, 5 of them `.ts` | It is in **no bundle**: `deps.edn` declares `:paths ["src"]`, and the `svelte/` tree that was removed never referenced it. It has its own package and its own passing suite. Migrating it needs a ClojureScript face for `@etzhayyim/sdk`, which is a separate decision. |
| `static/` — 5 files, 400,752 bytes | The pre-migration `assets` binding pointed at `./svelte/.svelte-kit/cloudflare/client`, **not** at `static/`. This Worker config never served it. `RESUME.edn` records the source as living in `40-engine/kami-engine/kami-app-live/` — it is not in this repo. |

Both are pinned in the verifier **by file count and by byte total**, so neither
can grow silently under cover of the migration.

Nothing in the current Worker serves `static/`. `GET /live-v1/kami_app_live.js`
returns 404 — measured in `wrangler dev --local`, not assumed.

### Read `RESUME.edn` with care

`RESUME.edn` is a verbatim carry-over from `etzhayyim/root`. Its
`:operator_steps` reference paths that **do not exist in this repository**
(`30-graph/graph-schema/migrations/…`, `70-tools/scripts/…`,
`00-contracts/lexicons/…`), and its smoke test posts to
`dispatcher.etzhayyim.com`. Those steps describe the *monorepo* this app was
extracted from, not this checkout. They are history, not instructions.

The instructions that do work are in
**[`docs/operator-quickstart.md`](docs/operator-quickstart.md)** — every command
there has been run end-to-end, with its real output.

## Boundary

This package is the **public** side of a deliberate split (`README.edn`
`:boundary`, and the header comment in `kotoba/src/index.ts`):

- **Here:** live rooms and broadcast schedules — first-party consumer catalog
  content. No settlement, no PII custody, no fulfillment liability.
- **Not here:** `sendCheer` (tipping — settlement), AI VTuber avatar generation
  (misaki LoRA + ComfyUI — compute), and live chat (federates via
  `app.bsky.feed.post`). Those stay on the etzhayyim side and are reached
  through a consent capability.

Identity hierarchy used by the records:

```
did:web:live.etzhayyim.com                     controller
did:web:live.etzhayyim.com:room:{roomId}       a live room
did:web:live.etzhayyim.com:sched:{scheduleId}  a broadcast schedule
```

AT-Lexicon rule observed throughout: **no floats** — `durationMinutes` is an
integer, and a fractional value is rejected rather than rounded.

## The page shows keys, and one value

env **key names** are shown; values are not — **except the relay target**. The
value of `AGENTGATEWAY_MCP_ROUTER_URL` is displayed deliberately, because an
operator needs to see where requests go.

The smoke checks this with **two independent sentinels**: a sentinel placed on a
different var must *not* appear, and the relay target *must*. One alone would
admit both a "hide everything" and a "show everything" implementation.

## Nothing this repo names resolves (the migration does not fix that)

| Host | Role | DNS (`dig +short`, 2026-08-18) |
|---|---|---|
| `mcp.etzhayyim.com` | relay target for `/xrpc/:nsid` | **no answer** |
| `live.etzhayyim.com` | the public identity in the records | **no answer** |
| `l1ve9pq4.etzhayyim.com` | the Worker that served `static/` | **no answer** |
| `dispatcher.etzhayyim.com` | named by `RESUME.edn`'s smoke test | **no answer** |

`wrangler.jsonc` declares **no `routes` at all**, so this Worker is not attached
to any hostname by its own config. `/xrpc/` returns **502 naming the URL it
tried** when the relay is unreachable — it does not hide a failure behind a 200.

## Known blocker: `npm install` does not work in `kotoba/`

`npm install` in `kotoba/` fails, and it is **not** a problem with this repo's
code. The declared dependency `@etzhayyim/sdk` needs a `tsc` build step and
drags in a large tree; npm ≥ 11.16 refuses to run that preparation inside a
project-scoped install, and pnpm fails further down the same tree.

The important part: **the sdk is only needed for types.** `kotoba/src/registry.ts`
imports it as `import type`, which is erased at runtime, and the mock the tests
use has no imports at all. The suite therefore runs green with no sdk present.

- **Tests run.** 3/3 pass — see the quickstart.
- **`tsc --noEmit` does not.** Without the sdk it reports `TS2307` plus four
  cascading implicit-`any` errors.

Full evidence, and where the fix belongs (upstream, not here), is recorded in
[`docs/adr/2608180536-sdk-is-a-type-only-dependency.edn`](docs/adr/2608180536-sdk-is-a-type-only-dependency.edn).
**The migration did not fix it and did not try** — the appview does not depend
on `kotoba/`, so the blocker never stood in its way.

## UI

The base is `kotoba-lang/jp-go-digital-design-system` (デジタル庁デザインシステム),
per the workspace's `kotoba-uiux` skill. Colour and size come only from the
`--hig-*` token contract; no raw hex, no px font sizes. App-specific CSS is
3 lines. The stylesheet is baked into the bundle with `shadow.resource/inline`
(the design system's zero-external-request policy).

Deterministic audit (`kotoba-lang/design-quality`): **100.00 / 100** (gate 95),
and still 100.00 with all 12 axes via `--extra-axes`.

### The design-system check is two checks, because it is two claims

Asserting `dads-table` appears **cannot fail**: that is markup the view emits,
present whether or not any stylesheet was inlined. Measured on this page:

| String | with CSS | without CSS |
|---|---|---|
| `dads-table` | 74 | **6** (never 0) |
| `class="dads-table"` | 1 | **1** (indistinguishable) |
| `--color-primitive-blue` | 45 | **0** |

So there are two: **the component was used** (`class="dads-table"`) and **the
stylesheet actually got in** (`--color-primitive-blue`). The design-quality
score does not draw this line either — stripping the stylesheet entirely still
scores **96.63 and PASSES the 95 gate**. Only the second smoke check can say the
CSS is there.

## Canonical metadata

`README.edn` (`etzhayyim.repository/v1`) is the machine-readable identity for
this repo; `migration.edn` records the tree this was copied from, the files the
migration was allowed to add, and the seven it removed. This file is the prose
entry point — it does not replace either.

## Verification

```bash
nbb scripts/verify-docs-claims.cljs .          # <dir> goes FIRST
```

exit 0 = everything agrees / 1 = a claim is false / **2 = could not answer**
(kept distinct from 0). Tests, build and smoke are in
[`docs/operator-quickstart.md`](docs/operator-quickstart.md).
