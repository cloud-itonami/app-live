/* tslint:disable */
/* eslint-disable */

/**
 * External hook: ingest a cheer into the running show. The JS shell
 * (or a WebSocket relay) calls `live_send_cheer(kind, weight)`.
 * `kind` is one of "clap" | "yell" | "lightStick" | "jump".
 */
export function live_send_cheer(kind: string, weight: number): void;

/**
 * Replace the running show with one built from `room_json`. Called by
 * the audience JS shell when the room DO broadcasts a `stateChange`
 * (the performer console pushed a new setlist).
 *
 * Returns a JS error message on parse / build failure so the shell
 * can surface it (the renderer keeps running with the previous show).
 */
export function live_set_room(room_json: string): void;

export function run_live_v1(canvas_id: string, room_json?: string | null): Promise<void>;

export type InitInput = RequestInfo | URL | Response | BufferSource | WebAssembly.Module;

export interface InitOutput {
    readonly memory: WebAssembly.Memory;
    readonly live_send_cheer: (a: number, b: number, c: number) => void;
    readonly live_set_room: (a: number, b: number) => [number, number];
    readonly run_live_v1: (a: number, b: number, c: number, d: number) => any;
    readonly wasm_bindgen__closure__destroy__h1fc64ebb5598a658: (a: number, b: number) => void;
    readonly wasm_bindgen__closure__destroy__hd2b8cc312c917bbd: (a: number, b: number) => void;
    readonly wasm_bindgen__closure__destroy__h057faa16bb93c894: (a: number, b: number) => void;
    readonly wasm_bindgen__convert__closures_____invoke__he5b12a5e6eb39525: (a: number, b: number, c: any) => [number, number];
    readonly wasm_bindgen__convert__closures_____invoke__h383bd871f37058d8: (a: number, b: number, c: any, d: any) => void;
    readonly wasm_bindgen__convert__closures_____invoke__h3d70b9cf29f76971: (a: number, b: number, c: any) => void;
    readonly wasm_bindgen__convert__closures_____invoke__h3d70b9cf29f76971_3: (a: number, b: number, c: any) => void;
    readonly wasm_bindgen__convert__closures_____invoke__h3d70b9cf29f76971_4: (a: number, b: number, c: any) => void;
    readonly wasm_bindgen__convert__closures_____invoke__h3d70b9cf29f76971_5: (a: number, b: number, c: any) => void;
    readonly wasm_bindgen__convert__closures_____invoke__hd8c4d7bd5864159c: (a: number, b: number) => void;
    readonly __wbindgen_malloc: (a: number, b: number) => number;
    readonly __wbindgen_realloc: (a: number, b: number, c: number, d: number) => number;
    readonly __wbindgen_exn_store: (a: number) => void;
    readonly __externref_table_alloc: () => number;
    readonly __wbindgen_externrefs: WebAssembly.Table;
    readonly __wbindgen_free: (a: number, b: number, c: number) => void;
    readonly __externref_table_dealloc: (a: number) => void;
    readonly __wbindgen_start: () => void;
}

export type SyncInitInput = BufferSource | WebAssembly.Module;

/**
 * Instantiates the given `module`, which can either be bytes or
 * a precompiled `WebAssembly.Module`.
 *
 * @param {{ module: SyncInitInput }} module - Passing `SyncInitInput` directly is deprecated.
 *
 * @returns {InitOutput}
 */
export function initSync(module: { module: SyncInitInput } | SyncInitInput): InitOutput;

/**
 * If `module_or_path` is {RequestInfo} or {URL}, makes a request and
 * for everything else, calls `WebAssembly.instantiate` directly.
 *
 * @param {{ module_or_path: InitInput | Promise<InitInput> }} module_or_path - Passing `InitInput` directly is deprecated.
 *
 * @returns {Promise<InitOutput>}
 */
export default function __wbg_init (module_or_path?: { module_or_path: InitInput | Promise<InitInput> } | InitInput | Promise<InitInput>): Promise<InitOutput>;
