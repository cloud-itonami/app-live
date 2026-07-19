/**
 * live kotoba — live-streaming room catalog: room + schedule.
 *
 * Per ADR-2606011400 (Consensys pattern) + ADR-2605172400 (3-axis OR-test).
 *
 * SPLIT (this app is (c) mixed — a live-streaming room platform with chat +
 * cheers + an AI VTuber avatar):
 *   PUBLIC (THIS PACKAGE) — live rooms + broadcast schedules. First-party
 *   consumer content (a streamer creates/schedules a room): browse live/upcoming
 *   streams = a consumer catalog (like webpage/worlds). No PII custody, no
 *   settlement, no fulfillment liability on the room/schedule itself.
 *     → migrated to etzhayyim front (AT PDS records, replaces RW).
 *
 *   REGULATED (STAYS etzhayyim, NOT in this package) — `sendCheer` (superchat /
 *   tipping = Settlement), the AI VTuber avatar generation (misaki LoRA +
 *   ComfyUI = compute), and live chat (federates via `app.bsky.feed.post`,
 *   already on-substrate). Consumed via consent-capability.
 *
 * AT-Lexicon: no float. Durations are integers (minutes).
 *
 * Identity hierarchy:
 *   did:web:live.etzhayyim.com                            — controller
 *   did:web:live.etzhayyim.com:room:{roomId}              — a live room
 *   did:web:live.etzhayyim.com:sched:{scheduleId}         — a broadcast schedule
 */

export const LIVE_DID_PREFIX = "did:web:live.etzhayyim.com:" as const;

export const ROOM_COLLECTION = "com.etzhayyim.apps.live.room";
export const SCHEDULE_COLLECTION = "com.etzhayyim.apps.live.schedule";

// ─── Enums ──────────────────────────────────────────────────────────

export type RoomStatus = "scheduled" | "live" | "ended";
export type ScheduleStatus = "planned" | "live" | "done" | "cancelled";

export const ROOM_STATUSES: ReadonlySet<string> = new Set(["scheduled", "live", "ended"]);
export const SCHEDULE_STATUSES: ReadonlySet<string> = new Set(["planned", "live", "done", "cancelled"]);

// ─── Room ───────────────────────────────────────────────────────────

export interface RoomRecord {
  did: string;
  roomId: string;
  title: string;
  streamerDid: string;
  description?: string;
  status: RoomStatus;
  category?: string;
  tags?: string[];
  createdAt: string;
  updatedAt: string;
}
export interface RoomView extends RoomRecord {
  roomUri: string;
}
export interface CreateRoomInput {
  roomId: string;
  title: string;
  streamerDid: string;
  description?: string;
  category?: string;
  tags?: string[];
}
export interface CreateRoomOutput {
  status: "created" | "alreadyExists" | "rejected";
  roomUri?: string;
  did?: string;
  roomId?: string;
  error?: string;
}
export interface SetRoomStatusInput {
  roomId: string;
  status: RoomStatus;
}
export interface SetRoomStatusOutput {
  status: "updated" | "rejected" | "notFound";
  roomId?: string;
  newStatus?: RoomStatus;
  error?: string;
}
export interface GetRoomInput {
  roomId: string;
}
export interface GetRoomOutput {
  room?: RoomView;
  error?: string;
}
export interface ListRoomsInput {
  status?: RoomStatus;
  category?: string;
  streamerDid?: string;
  tag?: string;
  /** App-layer substring search over title + description. */
  q?: string;
  limit?: number;
  cursor?: string;
}
export interface ListRoomsOutput {
  items: RoomView[];
  cursor?: string;
  total: number;
}

// ─── Schedule (FK→room) ─────────────────────────────────────────────

export interface ScheduleRecord {
  did: string;
  scheduleId: string;
  /** FK → room. */
  roomId: string;
  startsAt: string;
  title?: string;
  durationMinutes?: number;
  status: ScheduleStatus;
  createdAt: string;
}
export interface ScheduleView extends ScheduleRecord {
  scheduleUri: string;
}
export interface AddScheduleInput {
  scheduleId: string;
  roomId: string;
  startsAt: string;
  title?: string;
  durationMinutes?: number;
  status?: ScheduleStatus;
}
export interface AddScheduleOutput {
  status: "added" | "alreadyExists" | "rejected" | "roomNotFound";
  scheduleUri?: string;
  did?: string;
  scheduleId?: string;
  error?: string;
}
export interface ListSchedulesInput {
  roomId?: string;
  status?: ScheduleStatus;
  since?: string;
  limit?: number;
  cursor?: string;
}
export interface ListSchedulesOutput {
  items: ScheduleView[];
  cursor?: string;
  total: number;
}

// ─── Coverage ───────────────────────────────────────────────────────

export interface CoverageInput {
  maxScan?: number;
}
export interface CoverageOutput {
  roomCount?: number;
  scheduleCount?: number;
  roomsByStatus?: Record<string, number>;
  roomsByCategory?: Record<string, number>;
  truncated?: boolean;
  error?: string;
}

// ─── Validation + helpers ───────────────────────────────────────────

export function isUint(n: unknown): n is number {
  return typeof n === "number" && Number.isInteger(n) && n >= 0;
}

export function roomDidFor(id: string): string {
  return `${LIVE_DID_PREFIX}room:${id.toLowerCase()}`;
}
export function roomRkey(id: string): string {
  return `room-${id.toLowerCase()}`;
}
export function scheduleDidFor(id: string): string {
  return `${LIVE_DID_PREFIX}sched:${id.toLowerCase()}`;
}
export function scheduleRkey(id: string): string {
  return `sched-${id.toLowerCase()}`;
}
