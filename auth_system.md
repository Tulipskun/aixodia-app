# Auth System

สถานะ: implement แล้ว — ทางเดียวสำหรับทุก client
ขอบเขต: Worker `aixodia-auth` เป็นผู้ออก token เพียงผู้เดียว ทุกทางเข้าใช้ตาราง
`auth_tokens` เหมือนกัน: แอปผ่าน `/auth/device/*`, เว็บคอนโซลผ่าน `/auth/token`
หลัง Access, และ CLI ผ่าน `aixodia auth login` (device flow เดียวกัน)
flow PKCE เดิม (`/v1/auth/authorize|exchange`) ถูกลบออกจาก engine และแอปแล้ว
`CF_TOKEN` เป็นความลับของ engine สำหรับ Cloudflare API + socket ภายในเท่านั้น
client ไม่ถือและไม่ส่ง token ตัวนี้

## Flow

1. แอปเรียก `POST /auth/device/code` ได้ `device_code` (ลับ), `user_code` (เช่น `WXYZ-2041`), `interval`, `expires_in` 10 นาที
2. แอปแสดง `user_code` และเปิดเว็บ `/auth/device?code=...`
3. เว็บอยู่หลัง Cloudflare Access (GitHub) ผู้ใช้ตรวจรายละเอียดเครื่องแล้วกด Approve
4. แอป poll `POST /auth/device/token` จนได้ `access_token` ครั้งเดียว
5. แอปส่ง `Authorization: Bearer <token>` ทุก request

## กฎ

- Server (Worker) เป็นผู้ออก token เท่านั้น
- Token สุ่ม 256 บิต หมดได้ด้วย revoke; token ที่เว็บออกมี `expires_at`
  เป็นเพียง refresh hint สำหรับแคชในหน่วยความจำ (ไม่มี column บังคับใน device flow)
- อีเมลไม่ถูกส่งหรือใช้ใน login
- Device code ใช้ได้ครั้งเดียว หมดใน 10 นาที
- เก็บ token ใน D1 **แบบ hash (SHA-256)** — ค้นหาด้วย hash ต้นทุนเท่าเดิม
  และ Worker มี pattern นี้อยู่แล้ว ไม่มีเหตุผลต้องเก็บ plaintext
  (approve page ไม่มีโอกาสต้องแสดง token ซ้ำ)
- `/auth/device/token` จำกัดต่อ IP — `user_code` มีพื้นที่ค้นหาไม่กี่ล้าน
  ไม่งั้น brute force ได้; ตอบ `authorization_pending` / `slow_down`
  ตาม RFC 8628 ให้แอปถอย backoff
- Engine ↔ Worker ยืนยันกันด้วย shared secret ใน header
  (จุดสิ้นสุด sync ต้องป้องกัน ไม่ใช่ public)

## Sync กับ Engine

- Worker push `activate(token)` และ `revoke(token)` ไปที่ engine ทันที
- push ไม่สำเร็จ (engine offline) → ระบุแถวว่า `sync_pending`
  **engine ต้อง re-sync เป็นระยะ (เช่นทุก 60 วิ)** — ไม่งั้น token ที่ออก
  ตอน engine ล่มจะใช้ไม่ได้ตลอดกาล ทั้งที่ Worker บอกว่า active
- ตอน engine เริ่มทำงาน ดึงชุด token ที่ active ทั้งหมดจาก Worker ก่อนรับ request
- ระหว่างยังดึงไม่สำเร็จ engine ปฏิเสธทุก token (fail closed)
- revoke ตอน engine offline ไม่ต้อง retry — startup pull ตัดทิ้งเอง

## Endpoints

| Method | Path | ใครเรียก |
|---|---|---|
| POST | `/auth/device/code` | แอป |
| POST | `/auth/device/token` | แอป |
| GET | `/auth/device` | เว็บ (Access) |
| POST | `/auth/device/approve` | เว็บ (Access, CSRF) |
| GET | `/auth/sessions` | เว็บ (Access) |
| POST | `/auth/revoke` | เว็บ หรือแอป |
| POST | `/auth/sessions/sync` | Worker → engine |
| GET | `/auth/sessions/active` | engine → Worker |

## ความเสี่ยงที่ยอมรับ

- ถ้าเขียน D1 ได้ สร้าง session ได้เอง คุม credential ของ Cloudflare ให้เข้มงวด
- Token อยู่ในหน่วยความจำ engine ด้วย — dump หน่วยความจำ = token รั่ว
- Device token ไม่มีวันหมดอายุ — revoke คือกำแพงเดียว หน้า sessions ต้องหาใช้ง่าย

## ที่เก็บในแอป

- `EncryptedSharedPreferences` คีย์ใน Keystore
- ไม่ backup, ไม่ log header

## CLI

- `aixodia auth login` ใช้ device flow เดียวกับแอป (พิมพ์โค้ด + เปิด browser)
- เก็บ token ที่ `~/.aixodia/cli.token` โหมด 0600 (fallback แบบ gh เมื่อไม่มี
  system credential store บน headless host)
- `aixodia auth status|token|logout` — logout ส่ง `/auth/revoke` ด้วย token นั้น
