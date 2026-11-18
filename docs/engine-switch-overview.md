# แอพสลับ engine — overview

> สถานะ: **ข้อ 1–3 ทำแล้ว** (catalog, ตัวชี้เดียว, discovery ไม่แย่งสิทธิ์) — ยังไม่ทำ Room migration
> (`engineId`) และ UI สวิตช์ · เอกสารนี้คงส่วนที่เหลือไว้ตามเดิม
> ฝั่ง server พร้อมแล้ว: Worker เป็นเจ้าของ `engine.aixodia.dpdns.org` ทั้ง host และเสิร์ฟ registry

## 1. วันนี้แอพเลือก engine ได้แค่ตัวเดียว

ที่อยู่ engine ถูกเก็บเป็น**ค่าเดียว** ชื่อ `daemon_url` ใน DataStore (`SettingsStore.kt`)

```
DEFAULT_DAEMON_URL = "https://engine.aixodia.dpdns.org"
current().daemonUrl ──► ทุกอย่างที่คุยกับ engine
```

ทุกช่องทางอ่านจากค่านี้จุดเดียว: REST/chat (`HistoryApi`, `FilesApi`), socket (`ChatTransport`),
terminal (`TerminalSocket`), screen, providers cache (`providersCacheUrl`) — เพราะ `absoluteUrl(...)`
ถูกเรียกจาก `settings.current()` ในทุกฟังก์ชัน

ดังนั้น "สลับ engine" ในแอพวันนี้ = เขียนที่อยู่ใหม่ (`saveDaemon`) แล้วให้ทุกอย่างวิ่งไปที่นั่น
แต่มันไม่มี UI ให้ทำ และไม่มีรายการให้เลือก — มีแค่ `healDaemon()` ที่รีเซ็ตกลับค่า default
เมื่อที่อยู่ที่เก็บไว้ไม่ตอบเป็น engine

## 2. `DaemonDiscovery` เป็นตัวตัดสินที่อยู่ ไม่ใช่ผู้ใช้

`DaemonDiscovery.kt` วนทุก 30 วินาที อ่าน `GET <daemonUrl>/healthz` (`HistoryApi.node()`)
และรับ frame `daemon` จาก socket แล้วส่งเข้า `decide(currentUrl, node)`:

```
decide()  →  Wait   ที่อยู่ยังว่าง / ไม่มีใครออนไลน์
          →  Keep   ที่อยู่เดียวกัน (ตัด trailing slash)
          →  Adopt  ประกาศมาเป็น URL อื่น → saveDaemon(url) + socket.reconnect()
```

`Adopt` คือสิ่งที่ทำให้ **ผู้ใช้ไม่มีสิทธิ์เลือก**: engine ที่คุยอยู่ประกาศที่อยู่ตัวเองแล้วแอพเขียนทับทันที
(ตอนนี้ปลอดภัยเพราะมีเครื่องเดียว — พอมีหลายเครื่อง มันจะดึงกลับไปเครื่องที่เชื่อมอยู่)

## 3. ฝั่ง server มีอะไรให้แล้ว

| ของ | ที่อยู่ | หมายเหตุ |
|---|---|---|
| registry | `GET https://engine.aixodia.dpdns.org/api/engines` | Worker `aixodia-engine-gateway`, อ่าน D1 ตาราง `engines`, **ไม่มี Access บัง** (ใต้ `/api/*`) |
| รูปแบบ | `{"engines":[{"id","name","url","subtitle","tag"}]}` | `url` = `https://engine.aixodia.dpdns.org/<id>` |
| engine แต่ละตัว | `https://engine.aixodia.dpdns.org/<id>/…` | engine ตัด `/<id>` ออกเองก่อน router; ชื่อชนกันถูกแก้ที่ server (`-1`, `-2`) |

ฝั่ง console (web) ทำครบแล้ว: catalog + `active_engine_id` ที่จำไว้ + proxy `/engine/by/<id>/…`
(`aixodia-web/src/client-core.js`, `functions/engine/[[path]].js`) — รูปแบบเดียวกันนี้ย้ายมาแอพได้เลย

## 4. ที่ต้องเพิ่มในแอพ (5 จุด)

### 4.1 catalog ของ engine
DataStore เพิ่ม 3 key ใน `SettingsStore`:

```
engines_json        // [{id,name,url,subtitle,tag}] ล่าสุดที่เคยอ่านได้
active_engine_id    // id ที่ผู้ใช้เลือก; ว่าง = ตัวแรกของ catalog
engines_fetched_at  // ไว้แสดง "อัปเดตล่าสุดเมื่อ…"
```

โหลด: ตอนเปิดแอพ + ปุ่ม refresh + หลัง sign-in สำเร็จ — ถ้า fetch ไม่ได้ให้ใช้ `engines_json` เดิม
(ไม่ใช่ล้างเป็นว่าง) เพราะรายการเก่ายังใช้สลับได้

### 4.2 `daemonUrl` ยังเป็นตัวชี้ตัวเดียว
ไม่ต้องแตะ REST/socket/terminal เลย: `saveDaemon(engine.url)` = สลับทั้งแอพ
พร้อมกันนั้นเขียน `active_engine_id` — สองค่านี้ต้องเขียนใน `edit {}` เดียวกัน ไม่งั้นแอพเปิดมา
จะเห็น id ใหม่กับ url เก่า

### 4.3 discovery ต้องเลิกแย่งสิทธิ์
`decide()` เปลี่ยนเป็น 3 ผลเหมือนเดิม แต่ `Adopt` จำกัดความหมาย:

* ถ้า URL ที่ประกาศมา **ตรงกับแถวของ active engine** → adopt (แก้ URL ในแถว, ไม่เปลี่ยน id)
* ถ้าเป็น URL ของ engine อื่น → **ไม่สลับ** แค่ log/แสดงว่ามี engine อื่นออนไลน์
* `healDaemon()` ยังอยู่ แต่รีเซ็ตไปที่ `url` ของ active engine ไม่ใช่ `DEFAULT_DAEMON_URL`

### 4.4 state ที่ผูกกับเครื่องต้องแยกต่อ engine
นี่คือส่วนที่ถ้าลืมจะพังแบบเงียบ ๆ: สลับแล้วเห็นประวัติของอีกเครื่องปนเข้ามา

| state | วันนี้ | ต้องเป็น |
|---|---|---|
| Room `sessions` / `messages` | key ด้วย `sessionId` | เพิ่มคอลัมน์ `engineId` (migration 11→12) และอ่านเฉพาะของ active |
| `sessionId` | ค่าเดียว | map ต่อ engine (จำ thread ที่เปิดค้างไว้ต่อเครื่อง) |
| `terminalSessionIds` / `terminalClosedIds` / pids | ค่าเดียว | map ต่อ engine (tmux ของแต่ละเครื่องคนละชุด) |
| `providersCache` + `providersCacheUrl` | cache เดียว | ล้างเมื่อสลับ (มีอยู่แล้วใน `saveDaemon`) หรือเก็บเป็น map |
| token (`access_cookie`) | ค่าเดียว | **ไม่ต้องแยก** — ออกโดย auth Worker ตัวเดียว ใช้ได้ทุก engine |

### 4.5 UI
แถวใน Settings: `Engine — Primary Engine · m-8a7d1192` → เปิด bottom sheet:

```
[✓] Primary Engine      m-8a7d1192   online
[ ] Work                work-1       unreachable
    Refresh
```

แต่ละแถวโชว์ `phase` จาก `DiscoveryState` ของ engine นั้น (ยิง `/healthz` ตอนเปิด sheet ไม่ต้อง poll)
แตะแล้วเข้าลำดับในข้อ 5

## 5. ลำดับการสลับ (ทั้งชุดอยู่ในคลิกเดียว)

```
tap row
  → GET <url>/healthz           ไม่ตอบ = ไม่สลับ, แสดง error (ไม่เขียนอะไรลง DataStore)
  → edit { daemonUrl = url; active_engine_id = id }   ครั้งเดียว
  → socket.reconnect() + terminal ปิดของเก่า/เปิดใหม่
  → history.sync() ของ engine ใหม่ + providers ของ engine ใหม่
  → Room: อ่านเฉพาะ engineId ใหม่
```

ย้อนกลับไม่ได้ครึ่งทาง: ถ้าขั้นใดขั้นหนึ่งหลังเขียน DataStore ล้ม แอพอยู่ในสถานะ "เลือกแล้วแต่ยังโหลดไม่ได้"
ซึ่ง `DaemonDiscovery` จะรายงานเป็น `UNREACHABLE` — ดีกว่าเขียนครึ่ง ๆ กลาง ๆ

## 6. สิ่งที่ *ไม่* ต้องทำ

* **ไม่ต้อง dedupe ชื่อ** — server แก้ `-1`/`-2` ให้แล้วในตาราง `engines`
* **ไม่ต้องล็อกอินใหม่ตอนสลับ** — ทุก engine อยู่ใต้ hostname เดียวกัน (cookie เดียว) และ token
  ออกโดย auth Worker
* **ไม่ต้องมี DNS/Access ต่อเครื่อง** — อย่างน้อยสำหรับเครื่องที่แชร์ tunnel ของ `engine.` ;
  เครื่องที่ tunnel แยกของตัวเอง = เพิ่ม Access bypass 2 app (`…/<name>/api`, `…/<name>/healthz`)
  ตามที่ handoff เขียนไว้

## 7. สถานะงาน

| # | งาน | สถานะ |
|---|---|---|
| 1 | catalog ใน `SettingsStore` + `EngineCatalog` | ✅ `EngineCatalog.kt`, key `engines_json`/`active_engine_id`/`engines_fetched_at` |
| 2 | ตัวชี้เดียว (`saveDaemon`) + `EngineDirectory.select()` เขียน id+url พร้อมกัน | ✅ `EngineDirectory.kt` |
| 3 | discovery ไม่แย่งสิทธิ์ (เทียบ engine id ใน path) | ✅ `DaemonDiscovery.decide()` |
| 4 | Room migration `engineId` | ค้าง |
| 5 | UI bottom sheet | ค้าง |
| 6 | terminal/`sessionId` เป็น map ต่อ engine | ค้าง (ไม่บล็อกการสลับ) |

## 8. ลำดับงานที่เหลือ

1. `SettingsStore`: 3 key + `EngineCatalog` (parse/serialize + unit test)
2. `EngineDirectory`: fetch `/api/engines`, เก็บ catalog, จัดการ active id (unit test เรื่อง fetch fail)
3. `DaemonDiscovery`: จำกัด `Adopt` ให้อยู่ใน active engine (unit test ตาม `decide` ที่มีอยู่)
4. Room migration `engineId`
5. UI bottom sheet + ปุ่ม refresh
6. `terminal`/`sessionId` เป็น map ต่อ engine (ทำทีหลังได้ ไม่บล็อกการสลับ)