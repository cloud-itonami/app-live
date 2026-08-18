# Operator quickstart — app-live

Every command below was run end-to-end from a fresh `git clone` of this
repository. Where a command **fails**, that is recorded as the expected result
rather than omitted — the failure is the thing an operator most needs to know
in advance.

Measured on: macOS (darwin-arm64), Node **v26.3.0**, npm **11.16.0**,
pnpm **10.26.2**. The npm failure below is version-sensitive; the rest is not.

## 0. What you get

The `kotoba/` package — the room + schedule catalog — has a 3-test suite that
passes. That is the part of this repo you can exercise today. `svelte/` and the
prebuilt wasm in `static/live-v1/` have no tests.

## 1. Clone

```bash
git clone https://github.com/cloud-itonami/app-live
cd app-live
```

## 2. Do not start with `npm install` — it fails

```bash
cd kotoba
npm install
```

Expected result — **this fails**, on npm 11.16.0:

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

## 3. Build a toolchain outside the project

Because the project directory cannot be installed into, put the test runner
somewhere else. This directory is disposable.

```bash
mkdir -p /tmp/live-vitest && cd /tmp/live-vitest
printf '{"name":"live-vitest","private":true,"type":"module"}\n' > package.json
npm install vitest@^4.1.0
```

Result: `added 44 packages`. (This succeeding is itself the proof that npm is
fine and the project's declared dependencies are the blocker.)

## 4. Graft the runner and the mock into the repo

The tests need exactly two things: the runner, and `@etzhayyim/sdk-mock` — which
is a single self-contained file with **no imports of its own**.

```bash
cd <repo>/kotoba
mkdir -p node_modules/@etzhayyim
cp -R /tmp/live-vitest/node_modules/. node_modules/
git clone https://github.com/etzhayyim/com-etzhayyim-sdk-mock.git \
  node_modules/@etzhayyim/sdk-mock
git -C node_modules/@etzhayyim/sdk-mock checkout \
  c857ff9be5310bf433bfe1e8d3c0f677e213d667
```

The commit is pinned to the same revision `kotoba/package.json` declares, so
this is the dependency the repo asks for, not a substitute.

Nothing here modifies a tracked file: `node_modules/` is the only thing touched.
This repo has no `.gitignore`, so `git status` will now show
`kotoba/node_modules/` as untracked. Do not commit it — stage paths
explicitly rather than using `git add -A`.

## 5. Run the suite

```bash
node node_modules/vitest/vitest.mjs run
```

Actual output:

```
 RUN  v4.1.10 /private/tmp/clean-live/kotoba

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

## 6. Typecheck — this one does not pass

```bash
npm install typescript@^5.6.0   # into /tmp/live-vitest, then cp -R as in step 4
node node_modules/typescript/bin/tsc --noEmit
```

Actual output:

```
src/registry.ts(7,32): error TS2307: Cannot find module '@etzhayyim/sdk' or its
  corresponding type declarations.
src/registry.ts(112,14): error TS7006: Parameter 'r' implicitly has an 'any' type.
src/registry.ts(124,11): error TS7006: Parameter 'r' implicitly has an 'any' type.
src/registry.ts(161,14): error TS7006: Parameter 'r' implicitly has an 'any' type.
src/registry.ts(168,11): error TS7006: Parameter 'r' implicitly has an 'any' type.
```

There is **one** root cause: the unresolved `Etzhayyim` type. The four
implicit-`any` errors are downstream of it — they are not four separate defects.
Restoring a working `@etzhayyim/sdk` install clears all five.

Do not "fix" these by adding `any` annotations to `registry.ts`. That would
silence the signal that the dependency is missing while leaving the package
just as uninstallable.

## 7. Deploying

Not covered here, and not attempted. `wrangler.jsonc` points `main` at
`svelte/.svelte-kit/cloudflare/_worker.js` — a **build output** that does not
exist until `svelte/` is installed and built, and `svelte/` has the same
install situation as `kotoba/`. Anyone who gets that build working should
extend this document with the commands they actually ran.

Per the workspace rule, a production deploy must run from a checkout that
contains `origin/main`; the last writer wins and deploys have no
fast-forward check.
