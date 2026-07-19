/**
 * live kotoba — barrel.
 *
 * Per ADR-2606011400. Live-streaming room catalog (room → schedule) on the
 * etzhayyim substrate (AT PDS records; no RW).
 *
 *   room     : createRoom / setRoomStatus (scheduled→live→ended) / getRoom / listRooms (title+desc search)
 *   schedule : addSchedule (FK→room, uint durationMinutes) / listSchedules
 *   coverage
 *
 * (c) MIXED SPLIT: the public live-room catalog (rooms + schedules, first-party
 * consumer content) migrates. `sendCheer` (tipping = Settlement) + AI VTuber
 * avatar generation (misaki LoRA + ComfyUI compute) STAY etzhayyim; chat federates via
 * `app.bsky.feed.post` (already on-substrate). NOT in this package.
 */

export * from "./types.js";
export {
  createRoom,
  setRoomStatus,
  getRoom,
  listRooms,
  addSchedule,
  listSchedules,
  coverage,
} from "./registry.js";
