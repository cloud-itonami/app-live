# app-live

**A live-streaming room catalog.** This repository holds the public half of a
live-show application: **rooms** (a streamer's channel) and **broadcast
schedules** (when that room goes live). Nothing here takes money, holds PII, or
generates avatars — see [Boundary](#boundary).

The name says `live` but not *which* live, so to be explicit: this is
`did:web:live.etzhayyim.com`, the room + schedule registry, backed by AT
Protocol PDS records. It is not a video pipeline and not a chat server.

## What is actually in here

| Path | What it is | State |
|---|---|---|
| `kotoba/` | The reference implementation: `createRoom` / `setRoomStatus` / `getRoom` / `listRooms` / `addSchedule` / `listSchedules` / `coverage`, in TypeScript over the AT PDS. | **Real, and its 3 tests pass.** |
| `svelte/` | SvelteKit edge BFF (`+page.svelte`, an XRPC passthrough route). | Present, not exercised by any test. |
| `static/live-v1/` | A prebuilt `kami_app_live` wasm bundle + its JS shim. | Binary artifact, no source in this repo. |
| `wrangler.jsonc` | Cloudflare Worker config, serving the SvelteKit build. | Declares `name: etzhayyim-project-live`. |
| `RESUME.edn` | Migration notes inherited from the monorepo. | **Stale — see below.** |
| `MIGRATION-TODO.md` | Charter/substrate checklist inherited from the monorepo. | Checklist, largely unticked. |

### Read `RESUME.edn` with care

`RESUME.edn` is a verbatim carry-over from `etzhayyim/root`. Its
`:operator_steps` reference paths that **do not exist in this repository**
(`30-graph/graph-schema/migrations/…`, `70-tools/scripts/…`,
`00-contracts/lexicons/…`), and its smoke test posts to
`dispatcher.etzhayyim.com`. Those steps describe the *monorepo* this app was
extracted from, not this checkout. They are history, not instructions.

The instructions that do work are in
**[`docs/operator-quickstart.md`](docs/operator-quickstart.md)** — every command
there has been run end-to-end from a fresh clone.

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

## Known blocker: `npm install` does not work here

`npm install` in `kotoba/` fails, and it is **not** a problem with this repo's
code. The declared dependency `@etzhayyim/sdk` needs a `tsc` build step and
drags in a large tree; npm ≥ 11.16 refuses to run that preparation inside a
project-scoped install, and pnpm fails further down the same tree.

The important part: **the sdk is only needed for types.** `kotoba/src/registry.ts`
imports it as `import type`, which is erased at runtime, and the mock the tests
use has no imports at all. The suite therefore runs green with no sdk present.

Consequence, stated plainly:

- **Tests run.** 3/3 pass — see the quickstart.
- **`tsc --noEmit` does not.** Without the sdk it reports `TS2307` plus four
  cascading implicit-`any` errors.

Full evidence, and where the fix belongs (upstream, not here), is recorded in
[`docs/adr/2608180536-sdk-is-a-type-only-dependency.edn`](docs/adr/2608180536-sdk-is-a-type-only-dependency.edn).

## Canonical metadata

`README.edn` (`etzhayyim.repository/v1`) is the machine-readable identity for
this repo; `migration.edn` records the tree this was copied from. This file is
the prose entry point — it does not replace either.
