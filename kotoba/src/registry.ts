/**
 * live kotoba — room + schedule registries + coverage.
 * AT PDS records (no RW). Schedules FK→room. First-party consumer live-room
 * catalog; cheers (settlement) + avatar generation (compute) stay etzhayyim.
 */

import type { Etzhayyim } from "@etzhayyim/sdk";
import {
  ROOM_COLLECTION,
  ROOM_STATUSES,
  SCHEDULE_COLLECTION,
  SCHEDULE_STATUSES,
  isUint,
  roomDidFor,
  roomRkey,
  scheduleDidFor,
  scheduleRkey,
  type AddScheduleInput,
  type AddScheduleOutput,
  type CoverageInput,
  type CoverageOutput,
  type CreateRoomInput,
  type CreateRoomOutput,
  type GetRoomInput,
  type GetRoomOutput,
  type ListRoomsInput,
  type ListRoomsOutput,
  type ListSchedulesInput,
  type ListSchedulesOutput,
  type RoomRecord,
  type RoomView,
  type ScheduleRecord,
  type ScheduleView,
  type SetRoomStatusInput,
  type SetRoomStatusOutput,
} from "./types.js";

const PAGE_LIMIT = 100;
const DEFAULT_MAX_SCAN = 10_000;

async function exists(e: Etzhayyim, collection: string, rkey: string): Promise<boolean> {
  const resp = await e.read({ collection, rkey }).catch(() => ({ records: [] }));
  return Boolean(resp.records[0]?.value);
}

async function scanAll<T>(e: Etzhayyim, collection: string, maxScan: number, onRow: (v: T) => void): Promise<number> {
  let cursor: string | undefined;
  let scanned = 0;
  while (scanned < maxScan) {
    const page = await e.read<T>({ collection, cursor, limit: PAGE_LIMIT });
    for (const r of page.records) {
      if (scanned >= maxScan) break;
      onRow(r.value);
      scanned += 1;
    }
    if (scanned >= maxScan || !page.cursor || page.records.length < PAGE_LIMIT) break;
    cursor = page.cursor;
  }
  return scanned;
}

// ─── Room ───────────────────────────────────────────────────────────

export async function createRoom(e: Etzhayyim, input: CreateRoomInput): Promise<CreateRoomOutput> {
  if (!input.roomId || !input.title || !input.streamerDid) return { status: "rejected", error: "missingRequiredFields" };
  const rkey = roomRkey(input.roomId);
  const existing = await e.read<RoomRecord>({ collection: ROOM_COLLECTION, rkey }).catch(() => ({ records: [] }));
  if (existing.records[0]?.value) {
    return { status: "alreadyExists", roomUri: existing.records[0].uri, did: existing.records[0].value.did, roomId: input.roomId };
  }
  const did = roomDidFor(input.roomId);
  const now = new Date().toISOString();
  const record: RoomRecord = {
    did,
    roomId: input.roomId,
    title: input.title,
    streamerDid: input.streamerDid,
    description: input.description,
    status: "scheduled",
    category: input.category,
    tags: input.tags,
    createdAt: now,
    updatedAt: now,
  };
  const receipt = await e.write({ collection: ROOM_COLLECTION, record: record as unknown as Record<string, unknown>, rkey });
  return { status: "created", roomUri: receipt.uri, did, roomId: input.roomId };
}

export async function setRoomStatus(e: Etzhayyim, input: SetRoomStatusInput): Promise<SetRoomStatusOutput> {
  if (!input.roomId || !ROOM_STATUSES.has(input.status)) return { status: "rejected", error: "invalidStatus" };
  const rkey = roomRkey(input.roomId);
  const resp = await e.read<RoomRecord>({ collection: ROOM_COLLECTION, rkey }).catch(() => ({ records: [] }));
  const room = resp.records[0]?.value;
  if (!room) return { status: "notFound", error: "roomNotFound" };
  await e.write({ collection: ROOM_COLLECTION, record: { ...room, status: input.status, updatedAt: new Date().toISOString() } as unknown as Record<string, unknown>, rkey });
  return { status: "updated", roomId: input.roomId, newStatus: input.status };
}

export async function getRoom(e: Etzhayyim, input: GetRoomInput): Promise<GetRoomOutput> {
  if (!input.roomId) return { error: "invalidRoomId" };
  const resp = await e.read<RoomRecord>({ collection: ROOM_COLLECTION, rkey: roomRkey(input.roomId) }).catch(() => ({ records: [] }));
  const r = resp.records[0];
  if (!r) return { error: "notFound" };
  return { room: { ...r.value, roomUri: r.uri } };
}

export async function listRooms(e: Etzhayyim, input: ListRoomsInput = {}): Promise<ListRoomsOutput> {
  const limit = Math.min(input.limit ?? 50, 200);
  const resp = await e.read<RoomRecord>({ collection: ROOM_COLLECTION, cursor: input.cursor, limit });
  const q = input.q?.toLowerCase();
  const items: RoomView[] = resp.records
    .filter((r) => {
      const v = r.value;
      if (input.status && v.status !== input.status) return false;
      if (input.category && v.category !== input.category) return false;
      if (input.streamerDid && v.streamerDid !== input.streamerDid) return false;
      if (input.tag && !(v.tags ?? []).includes(input.tag)) return false;
      if (q) {
        const hay = [v.title, v.description ?? ""].join(" ").toLowerCase();
        if (!hay.includes(q)) return false;
      }
      return true;
    })
    .map((r) => ({ ...r.value, roomUri: r.uri }));
  return { items, cursor: resp.cursor, total: items.length };
}

// ─── Schedule ───────────────────────────────────────────────────────

export async function addSchedule(e: Etzhayyim, input: AddScheduleInput): Promise<AddScheduleOutput> {
  if (!input.scheduleId || !input.roomId || !input.startsAt) return { status: "rejected", error: "missingRequiredFields" };
  if (input.durationMinutes != null && !isUint(input.durationMinutes)) return { status: "rejected", error: "durationMinutesMustBeUint" };
  if (input.status && !SCHEDULE_STATUSES.has(input.status)) return { status: "rejected", error: "invalidStatus" };
  if (!(await exists(e, ROOM_COLLECTION, roomRkey(input.roomId)))) {
    return { status: "roomNotFound", error: `roomNotFound:${input.roomId}` };
  }
  const rkey = scheduleRkey(input.scheduleId);
  const existing = await e.read<ScheduleRecord>({ collection: SCHEDULE_COLLECTION, rkey }).catch(() => ({ records: [] }));
  if (existing.records[0]?.value) {
    return { status: "alreadyExists", scheduleUri: existing.records[0].uri, did: existing.records[0].value.did, scheduleId: input.scheduleId };
  }
  const did = scheduleDidFor(input.scheduleId);
  const record: ScheduleRecord = {
    did,
    scheduleId: input.scheduleId,
    roomId: input.roomId,
    startsAt: input.startsAt,
    title: input.title,
    durationMinutes: input.durationMinutes,
    status: input.status ?? "planned",
    createdAt: new Date().toISOString(),
  };
  const receipt = await e.write({ collection: SCHEDULE_COLLECTION, record: record as unknown as Record<string, unknown>, rkey });
  return { status: "added", scheduleUri: receipt.uri, did, scheduleId: input.scheduleId };
}

export async function listSchedules(e: Etzhayyim, input: ListSchedulesInput = {}): Promise<ListSchedulesOutput> {
  const limit = Math.min(input.limit ?? 50, 200);
  const resp = await e.read<ScheduleRecord>({ collection: SCHEDULE_COLLECTION, cursor: input.cursor, limit });
  const items: ScheduleView[] = resp.records
    .filter((r) => {
      const v = r.value;
      if (input.roomId && v.roomId !== input.roomId) return false;
      if (input.status && v.status !== input.status) return false;
      if (input.since && v.startsAt < input.since) return false;
      return true;
    })
    .map((r) => ({ ...r.value, scheduleUri: r.uri }));
  return { items, cursor: resp.cursor, total: items.length };
}

// ─── Coverage ───────────────────────────────────────────────────────

export async function coverage(e: Etzhayyim, input: CoverageInput = {}): Promise<CoverageOutput> {
  const maxScan = Math.min(input.maxScan ?? DEFAULT_MAX_SCAN, DEFAULT_MAX_SCAN);
  const roomsByStatus: Record<string, number> = {};
  const roomsByCategory: Record<string, number> = {};
  const roomCount = await scanAll<RoomRecord>(e, ROOM_COLLECTION, maxScan, (v) => {
    roomsByStatus[v.status] = (roomsByStatus[v.status] ?? 0) + 1;
    if (v.category) roomsByCategory[v.category] = (roomsByCategory[v.category] ?? 0) + 1;
  });
  const scheduleCount = await scanAll<ScheduleRecord>(e, SCHEDULE_COLLECTION, maxScan, () => {});
  return {
    roomCount,
    scheduleCount,
    roomsByStatus,
    roomsByCategory,
    truncated: roomCount >= maxScan || scheduleCount >= maxScan,
  };
}
