# E-Queue

ระบบจองคิวภาษาไทย ใช้ Spring Boot 3.5.16, Java 21, Thymeleaf, PostgreSQL และ Bootstrap 5.1.3 / Sneat 1.0.0 จาก Pack ที่ให้มา

## เปิดระบบ

- ลูกค้า: http://localhost:8090/
- ผู้ดูแล: http://localhost:8090/admin/login
- บัญชีแอดมินใช้ข้อมูลใน `m_user` เท่านั้น ระบบไม่สร้างบัญชีให้อัตโนมัติ
- Profile เริ่มต้น: `local` ใช้ฐานข้อมูล `db_queue` บน `localhost:5432`
- Profile `uat`: ใช้ฐานข้อมูล Neon ตาม `PGHOST`, `PGDATABASE`, `PGUSER` และ `PGPASSWORD`
- รหัสผ่านแยกเก็บใน `application-local.properties` / `application-uat.properties` ซึ่งถูกยกเว้นจาก Git และโหลดเฉพาะ profile นั้น

รันคำสั่งจากโฟลเดอร์โปรเจกต์:

```powershell
.\setup-java.ps1  # ดาวน์โหลด JDK 21 แบบแยกใน .tools เมื่อยังไม่มี พร้อมตรวจ SHA-256
.\build.ps1       # build ด้วย Maven Wrapper
.\start.ps1 -Profile local  # พอร์ต 8090; ใช้ -Port 8092 เพื่อเปลี่ยนพอร์ต
.\start.ps1 -Profile local -Background  # เปิดเบื้องหลัง พร้อม log ใน .local
.\start.ps1 -Profile uat  # เปิดระบบด้วยฐานข้อมูล UAT
.\stop.ps1        # หยุด instance ที่เปิดด้วย -Background
```

โปรเจกต์นี้เตรียม Java 21 ไว้ใน `.tools` แล้ว ไม่แก้ Java เริ่มต้นของเครื่อง ตัว Maven Wrapper ดาวน์โหลด Maven จาก Maven Central เมื่อจำเป็น

## Profile local / uat

`application.yml` เก็บค่าร่วม โดยแยก datasource และ template cache ไว้ใน `application-local.yml` / `application-uat.yml` หากไม่ระบุ profile จะใช้ `local` เสมอ

- Local: คัดลอก `application-local.properties.example` เป็น `application-local.properties` แล้วกรอกค่าของเครื่อง
- UAT: คัดลอก `application-uat.properties.example` เป็น `application-uat.properties` แล้วกรอกค่าของ Neon และ remember-key ของ UAT หรือกำหนด environment variables แทน
- UAT รองรับ `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`, `PGSSLMODE`, `PGCHANNELBINDING`; URL สำหรับ JDBC ใช้ชื่อ parameter `channelBinding` และบังคับ SSL/channel binding เป็น `require` ตามค่าเริ่มต้น
- UAT ไม่โหลดไฟล์ค่าเชื่อมต่อ Local ใช้ template cache ส่วน Local ปิด cache สำหรับพัฒนา
- ทั้งสอง profile ไม่มีการสร้าง schema หรือ insert ข้อมูลตั้งต้นอัตโนมัติ

เปิด JAR โดยตรงได้ด้วย:

```powershell
java -jar target/e-queue-1.0.0.jar --spring.profiles.active=local
java -jar target/e-queue-1.0.0.jar --spring.profiles.active=uat
```

หรือกำหนด `SPRING_PROFILES_ACTIVE=uat` ในสภาพแวดล้อมที่ deploy สำหรับ `start.ps1` ให้เลือกผ่าน `-Profile` ซึ่งระบุ profile ให้ชัดเจนเสมอ

สร้างฐานข้อมูล `db_queue` แล้วรัน `database/schema.sql` ด้วย DBeaver หรือ PostgreSQL client ก่อนเปิดระบบครั้งแรก ตัวอย่างจากโฟลเดอร์โปรเจกต์:

```powershell
psql -h localhost -p 5432 -U postgres -d db_queue -v ON_ERROR_STOP=1 -f database/schema.sql
```

ระบบไม่สร้างหรือปรับโครงสร้างฐานข้อมูลตอนเริ่มทำงาน (`spring.sql.init.mode=never`) ไฟล์ SQL สร้างเฉพาะโครงสร้างตารางโดยไม่ใช้ PostgreSQL ENUM และไม่มีคำสั่ง insert ข้อมูลตั้งต้น รันซ้ำได้โดยไม่ลบข้อมูลหรือเขียนทับค่าร้านเดิม แต่ไม่ใช้สำหรับอัปเกรดโครงสร้างตารางที่มีอยู่ หากโครงสร้างเปลี่ยนต้องรันคำสั่ง SQL ที่จำเป็นด้วยตนเอง

ก่อนเปิดใช้งานฐานข้อมูลใหม่ ผู้ดูแลฐานข้อมูลต้องจัดเตรียมบัญชีแอดมินใน `m_user` (รหัสผ่านเก็บเป็น BCrypt hash), ข้อมูลร้านใน `m_booking_setting` (`id_setting=1`) และตารางเวลาทั้ง 7 วันใน `m_business_hour` (`day_of_week=1–7`) ตามข้อมูลจริงของร้าน ระบบไม่มีข้อมูลสำรองหรือค่าเริ่มต้นที่สร้างแทนให้

ฐานข้อมูลเดิมที่มีตารางครบแล้วเปิดใช้งานต่อได้ทันที หากย้ายเครื่อง ให้คัดลอกไฟล์ `.properties.example` ของ profile ที่ต้องการแล้วกรอกค่าจริง สำหรับ UAT ที่ยังไม่มีตาราง ให้ผู้ดูแลฐานข้อมูลรัน SQL และจัดเตรียมข้อมูลจริงก่อนเปิดใช้งาน

รองรับ environment variables มาตรฐานของ Spring ได้แก่ `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `APP_REMEMBER_KEY` และ `PORT` รหัสผ่านใน `m_user` เก็บแบบ BCrypt

## Deploy บน Render ด้วย Docker

มี `Dockerfile` แบบ multi-stage ใช้ Java 21: build ด้วย Maven Wrapper และรันเฉพาะ JAR ด้วยผู้ใช้ที่ไม่ใช่ root ค่าเริ่มต้นใน container คือ profile `uat` และรับพอร์ตจาก `PORT` ส่วน `.dockerignore` ส่งเฉพาะ source และไฟล์ build จึงไม่รวมรหัสผ่านในไฟล์ `.properties`, `.local`, `.tools` หรือ artifacts

ก่อน deploy ให้เตรียมตารางด้วย `database/schema.sql` และข้อมูลจริงของร้าน/บัญชีแอดมินใน Neon ให้ครบ ฐานข้อมูล UAT ที่ตรวจครั้งล่าสุดยังไม่มีตารางระบบ แอปและ Docker ไม่มีขั้นตอนสร้างตารางหรือ insert ข้อมูลให้อัตโนมัติ Health check ใช้ `/` ซึ่งต้องอ่านข้อมูลร้านได้จึงจะผ่าน

### ใช้ Blueprint

1. Push โค้ดที่มี `Dockerfile` และ `render.yaml` ขึ้น GitHub
2. ที่ Render เลือก **New → Blueprint** แล้วเชื่อม repository `bbkiss550/e-queue`
3. กรอก `PGPASSWORD` ของ Neon เมื่อ Render ขอค่า ส่วน `REMEMBER_KEY` จะสร้างแบบสุ่มให้และเก็บเป็น environment variable
4. Deploy แล้วตรวจหน้าแรก, Login, การจอง และ Noti ผ่าน URL HTTPS ของ Render

Blueprint ตั้ง Web Service แบบ Free ใน Singapore ไว้ก่อน ปรับ plan ได้ตามการใช้งานจริง บริการ Free จะพักเมื่อไม่มีการใช้งาน จึงเหมาะสำหรับทดลอง; ดู [ข้อจำกัดของ Free service](https://render.com/docs/free)

### สร้าง Web Service ผ่าน Dashboard

เลือก **New → Web Service**, เชื่อม repository, เลือก **Language: Docker**, Dockerfile `./Dockerfile` และ Health Check Path `/` ไม่ต้องใส่ Build Command หรือ Start Command เพิ่ม จากนั้นตั้ง environment variables:

| Variable | ค่า |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `uat` |
| `PORT` | `10000` |
| `PGHOST` | Host ของ Neon ตาม `render.yaml` |
| `PGPORT` | `5432` |
| `PGDATABASE` | `db_queue` |
| `PGUSER` | `neondb_owner` |
| `PGPASSWORD` | รหัสผ่าน Neon ใส่ใน Render เท่านั้น |
| `PGSSLMODE` | `require` |
| `PGCHANNELBINDING` | `require` |
| `REMEMBER_KEY` | สุ่มอย่างน้อย 32 bytes และเก็บค่าเดิมข้าม deploy |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `framework` |
| `SERVER_SERVLET_SESSION_COOKIE_SECURE` | `true` |

สองค่าท้ายรองรับ HTTPS ที่ Render proxy และให้ session cookie ส่งผ่าน HTTPS การแจ้งเตือน SSE และ session เก็บในหน่วยความจำของ instance ให้ใช้ **1 instance** สำหรับโครงสร้างปัจจุบัน; หากต้องการหลาย instance ต้องเพิ่มระบบกระจาย event และ shared session ก่อน

### Build Docker บนเครื่อง

```powershell
docker build -t e-queue:latest .
```

ทดสอบ container กับ UAT โดยกำหนด `$env:PGPASSWORD` และ `$env:REMEMBER_KEY` บนเครื่องก่อน ไม่ส่งรหัสผ่านเป็น build argument:

```powershell
docker run --rm -p 10000:10000 -e PGPASSWORD -e REMEMBER_KEY e-queue:latest
```

เปิด `http://localhost:10000` (ค่า secure cookie สำหรับ HTTPS ถูกตั้งใน Render ไม่ได้บังคับใน Dockerfile) Container จะใช้ฐานข้อมูล UAT จริง จึงต้องเตรียมฐานข้อมูลและระวังการทดสอบที่แก้ไขข้อมูล

อ้างอิง [Docker on Render](https://render.com/docs/docker), [Blueprint YAML](https://render.com/docs/blueprint-spec) และ [Health checks](https://render.com/docs/health-checks)

## กติกาการจอง

- ลูกค้าไม่สมัครสมาชิก ค้นหาประวัติและจัดการคิวด้วยเบอร์โทรไทย 9–10 หลัก
- จองวันนี้ถึงวันนี้ + 2 วัน ตามเขตเวลา Asia/Bangkok
- รอบจองเป็นต้นชั่วโมง อยู่ตั้งแต่เวลาเปิดและก่อนเวลาปิดร้าน
- Admin ตั้งจำนวนคิวต่อชั่วโมง 1–999 และจองล่วงหน้าขั้นต่ำ 0–60 นาที
- เวลา 09:33 ตั้งขั้นต่ำ 60 นาที จะเลือกจองได้ตั้งแต่ 11:00
- ตั้งเวลารายสัปดาห์จันทร์–อาทิตย์ และวันหยุดแยกตามวันที่
- เพิ่มวันหยุดยกเลิกคิวที่ยังไม่ถึงเวลาของวันนั้นด้วยเหตุผล “ร้านปิด” ส่วนประวัติที่ผ่านเวลาไปแล้วคงไว้
- ลบวันหยุดเปิดรับจองใหม่ตามตารางประจำ รายการที่ยกเลิกแล้วไม่ถูกคืนสถานะ
- หากปรับเวลาเปิด–ปิดหรือ Capacity จนกระทบคิวเดิม ระบบแจ้งให้เลื่อนหรือยกเลิกคิวที่เกี่ยวข้องก่อน
- เบอร์เดิมจองหลายคิวในวันเดียวกันได้ โดยต้องยืนยันคำเตือนก่อนจองเพิ่ม
- ลูกค้าและ Admin เลื่อนได้เฉพาะเวลาในวันเดิม ยกเลิกได้ก่อนเวลานัด ไม่แก้ชื่อ/เบอร์/วันนัด
- เก็บสถานะ `BOOKED` / `CANCELLED` เท่านั้น UI แสดง “สำเร็จ” เมื่อเวลานัดผ่านไป
- รหัส `YYMMDDNNN` ใช้วันที่สร้างการจอง Running เริ่มใหม่แต่ละวัน สูงสุด 999 รายการต่อวัน เมื่อเต็มระบบแจ้งให้ติดต่อร้าน

## หน้าจอ

ลูกค้า: หน้าหลัก, เลือกวัน/เวลา, กรอกข้อมูล/ยืนยัน, จองสำเร็จ, ตรวจสอบรายการ/ประวัติ, Modal เลื่อนเวลา

ผู้ดูแล: Login, รายการรายวัน, ปฏิทินเริ่มรายเดือนสลับรายวันได้, Modal เพิ่ม/ดู/เลื่อน/ยกเลิก, ตั้งค่าร้าน/ตารางประจำ/วันหยุด

CRUD, ค้นหาขณะพิมพ์, เปลี่ยนวัน และมุมมอง Admin ใช้ fetch โดยไม่มี query parameters บน URL หน้าระบบ Mobile เปลี่ยนตารางเป็น Card List; ปฏิทินกดวันที่เพื่อดูรายวันได้ ไม่มีภาพพื้นหลัง ตามข้อสรุปกับผู้ใช้

Noti อยู่ในระบบ Admin เท่านั้น ใช้ Server-Sent Events (SSE) ส่งหลัง Transaction Commit มีกระดิ่ง จำนวนยังไม่อ่าน ข้อความเด้ง และรายการแจ้งเตือนย้อนหลัง เมื่อการเชื่อมต่อกลับมาจะอ่านจากฐานข้อมูลใหม่ มี heartbeat / auto reconnect และ polling สำรองเมื่อออฟไลน์ Proxy ที่นำไปใช้จริงต้องไม่ buffer SSE

## โครงสร้าง

```text
src/main/java/th/co/equeue/
  config/          Login และ BCrypt
  domain/          Booking, BusinessHour, BookingSetting
  repository/      Spring JDBC + PostgreSQL
  service/         กติกาการจอง, Transaction, SSE
  web/             Thymeleaf pages, JSON API, Validation
database/schema.sql SQL สำหรับเตรียมฐานข้อมูลด้วยตนเอง
src/main/resources/
  application.yml ค่าร่วมและ default profile
  application-local.yml / application-uat.yml ค่าของแต่ละสภาพแวดล้อม
  templates/       Layout/fragment ของลูกค้าและ Admin
  static/assets/   Sneat 1.0.0 assets ที่นำมาจาก Pack
  static/css/      CSS ปรับหน้าตาตาม Reference และ Responsive
  static/js/       fetch, Calendar, Modal, SSE
src/test/          Integration tests กับ PostgreSQL จริง
```

ใช้ Lock แถวตั้งค่าภายใน Transaction เพื่อให้การจอง/เลื่อน/ยกเลิก/ตั้งค่า/วันหยุดตรวจ Capacity อย่างสอดคล้องกันทุก instance ฐานข้อมูลเดียวกัน Running ใช้ atomic upsert และ Unique Constraint รหัสจอง ทุกคำขอสร้างมี UUID idempotency key ป้องกัน Double Submit การแจ้งเตือนบันทึกใน Transaction เดียวกับการจองและส่ง SSE หลัง Commit

API Admin ต้องผ่าน Login และ POST ทุกตัวตรวจ CSRF Token ส่วนข้อมูลที่ผู้ใช้กรอกถูก escape ก่อนแสดงใน HTML

## ทดสอบ

สร้างฐานข้อมูลแยกชื่อ `db_queue_test` แล้วรัน `database/schema.sql` ในฐานข้อมูลนี้ก่อน พร้อมจัดเตรียมข้อมูลร้านและตารางเวลาที่ชุดทดสอบต้องใช้ จากนั้นกำหนด `TEST_DB_PASSWORD` ก่อนรัน:

```powershell
$env:TEST_DB_PASSWORD = 'รหัสผ่าน PostgreSQL ของเครื่อง'
.\build.ps1 -Test
```

ชุดทดสอบล้างเฉพาะตารางใน `db_queue_test` ก่อนแต่ละกรณี มีการตรวจชื่อฐานข้อมูลก่อนล้าง ไม่ใช้ข้อมูลจริงใน `db_queue`

ครอบคลุม Capacity เมื่อคำขอเข้าพร้อมกัน, Running / Double Submit, ช่วงวันและเวลา, คำเตือนเบอร์ซ้ำ, สิทธิ์เลื่อน/ยกเลิก, วันหยุด, สถานะที่แสดง, Noti ที่บันทึกและอ่านแล้ว, Login / CSRF และการ render หน้าเว็บ

Java requirements อ้างอิง [Spring Boot documentation](https://docs.spring.io/spring-boot/3.5/system-requirements.html). Sneat ใช้ Asset ของรุ่นที่แนบโดยตรง และเก็บ License ใน `licenses/Sneat-LICENSE.md`
