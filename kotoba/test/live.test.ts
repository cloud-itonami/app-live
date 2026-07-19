import { describe, it, expect, beforeEach } from "vitest";
import { MockEtzhayyim } from "@etzhayyim/sdk-mock";
import {
  createRoom,
  setRoomStatus,
  getRoom,
  listRooms,
  addSchedule,
  listSchedules,
  coverage,
} from "../src/index.js";

const STREAMER = "did:web:misaki.live.etzhayyim.com";

describe("live kotoba", () => {
  let e: any;
  beforeEach(() => {
    e = new MockEtzhayyim({ did: "did:web:live.etzhayyim.com" });
  });

  describe("room catalog", () => {
    it("creates rooms (start scheduled), flips status, reads, searches", async () => {
      expect((await createRoom(e, { roomId: "R-1", title: "Misaki ASMR Night", streamerDid: STREAMER, description: "chill stream", category: "asmr", tags: ["vtuber"] })).status).toBe("created");
      expect((await getRoom(e, { roomId: "R-1" })).room?.status).toBe("scheduled");
      expect((await setRoomStatus(e, { roomId: "R-1", status: "live" })).newStatus).toBe("live");
      expect((await setRoomStatus(e, { roomId: "R-1", status: "offline" as any })).status).toBe("rejected");
      expect((await setRoomStatus(e, { roomId: "GHOST", status: "live" })).status).toBe("notFound");
      await createRoom(e, { roomId: "R-2", title: "Gaming Hour", streamerDid: STREAMER, category: "gaming" });
      expect((await listRooms(e, { status: "live" })).total).toBe(1);
      expect((await listRooms(e, { category: "gaming" })).total).toBe(1);
      expect((await listRooms(e, { q: "asmr" })).total).toBe(1);
    });
  });

  describe("schedules FK→room", () => {
    beforeEach(async () => {
      await createRoom(e, { roomId: "R-1", title: "Room", streamerDid: STREAMER });
    });
    it("adds schedules (FK→room, uint duration, status validated), rejects missing room", async () => {
      expect((await addSchedule(e, { scheduleId: "S-1", roomId: "R-1", startsAt: "2026-06-05T20:00:00Z", title: "evening", durationMinutes: 120 })).status).toBe("added");
      expect((await addSchedule(e, { scheduleId: "S-F", roomId: "R-1", startsAt: "x", durationMinutes: 12.5 as any })).status).toBe("rejected"); // float
      expect((await addSchedule(e, { scheduleId: "S-X", roomId: "R-1", startsAt: "x", status: "maybe" as any })).status).toBe("rejected"); // status
      expect((await addSchedule(e, { scheduleId: "S-G", roomId: "GHOST", startsAt: "x" })).status).toBe("roomNotFound");
      expect((await listSchedules(e, { roomId: "R-1", status: "planned" })).total).toBe(1);
      expect((await listSchedules(e, { since: "2026-06-01T00:00:00Z" })).total).toBe(1);
    });
    it("coverage rolls up rooms + schedules by status/category", async () => {
      await setRoomStatus(e, { roomId: "R-1", status: "live" });
      await createRoom(e, { roomId: "R-2", title: "Two", streamerDid: STREAMER, category: "music" });
      await addSchedule(e, { scheduleId: "S-1", roomId: "R-1", startsAt: "2026-06-05T20:00:00Z" });
      const cov = await coverage(e);
      expect(cov.roomCount).toBe(2);
      expect(cov.scheduleCount).toBe(1);
      expect(cov.roomsByStatus?.live).toBe(1);
      expect(cov.roomsByStatus?.scheduled).toBe(1);
      expect(cov.roomsByCategory?.music).toBe(1);
    });
  });
});
