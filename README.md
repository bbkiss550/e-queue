# E-Queue

ระบบจองคิวภาษาไทย ใช้ Spring Boot 3.5.16, Java 21, Thymeleaf, PostgreSQL และ Bootstrap 5.1.3 / Sneat 1.0.0 จาก Pack ที่ให้มา

## เปิดระบบ

- ลูกค้า: http://localhost:8090/
- ผู้ดูแล: http://localhost:8090/admin/login
- บัญชีเริ่มต้น: `admin` รหัสผ่านที่สร้างให้เก็บใน `.local/admin-login.txt`
- ฐานข้อมูลใช้งาน: `db_queue` บน `localhost:5432`
- ค่าเชื่อมต่อและรหัสผ่านเก็บใน `application-local.properties` ซึ่งถูกยกเว้นจาก Git

รันคำสั่งจากโฟลเดอร์โปรเจกต์:

```powershell
.\setup-java.ps1  # ดาวน์โหลด JDK 21 แบบแยกใน .tools เมื่อยังไม่มี พร้อมตรวจ SHA-256
.\build.ps1       # build ด้วย Maven Wrapper
.\start.ps1       # เริ่มระบบพอร์ต 8090; ใช้ -Port 8092 เพื่อเปลี่ยนพอร์ต
.\start.ps1 -Background  # เปิดแบบเบื้องหลัง พร้อม log ใน .local
.\stop.ps1        # หยุด instance ที่เปิดด้วย -Background
```

โปรเจกต์นี้เตรียม Java 21 ไว้ใน `.tools` แล้ว ไม่แก้ Java เริ่มต้นของเครื่อง ตัว Maven Wrapper ดาวน์โหลด Maven จาก Maven Central เมื่อจำเป็น

สร้างฐานข้อมูลว่าง `db_queue` ก่อนรันครั้งแรก Flyway สร้างและปรับ schema อัตโนมัติ โดยไม่ใช้ PostgreSQL ENUM หากย้ายเครื่อง ให้คัดลอก `application-local.properties.example` เป็น `application-local.properties` และกรอกค่าจริง

รองรับ environment variables มาตรฐานของ Spring ได้แก่ `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `APP_ADMIN_USERNAME`, `APP_ADMIN_PASSWORD`, `APP_REMEMBER_KEY` และ `PORT` ค่า Admin Password ใช้สร้างบัญชีครั้งแรกเท่านั้น ไม่เปลี่ยนรหัสผ่านบัญชีเดิมทุกครั้งที่เปิดระบบ รหัสผ่านใน `m_user` เก็บแบบ BCrypt

## กติกาการจอง

- ลูกค้าไม่สมัครสมาชิก ค้นหาประวัติและจัดการคิวด้วยเบอร์โทรไทย 9–10 หลัก
- จองวันนี้ถึงวันนี้ + 2 วัน ตามเขตเวลา Asia/Bangkok
- รอบจองเป็นต้นชั่วโมง อยู่ตั้งแต่เวลาเปิดและก่อนเวลาปิดร้าน
- Admin ตั้งจำนวนคิวต่อชั่วโมง 1–999 และจองล่วงหน้าขั้นต่ำ 0–60 นาที
- เวลา 09:33 ตั้งขั้นต่ำ 60 นาที จะเลือกจองได้ตั้งแต่ 11:00
- ค่าเริ่มต้น: เปิดทุกวัน 09:00–18:00, 1 คิวต่อชั่วโมง, ขั้นต่ำ 60 นาที; ปรับได้ในหน้าตั้งค่า
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
  config/          Login, BCrypt และสร้าง Admin ครั้งแรก
  domain/          Booking, BusinessHour, BookingSetting
  repository/      Spring JDBC + PostgreSQL
  service/         กติกาการจอง, Transaction, SSE
  web/             Thymeleaf pages, JSON API, Validation
src/main/resources/
  db/migration/    Flyway SQL
  templates/       Layout/fragment ของลูกค้าและ Admin
  static/assets/   Sneat 1.0.0 assets ที่นำมาจาก Pack
  static/css/      CSS ปรับหน้าตาตาม Reference และ Responsive
  static/js/       fetch, Calendar, Modal, SSE
src/test/          Integration tests กับ PostgreSQL จริง
```

ใช้ Lock แถวตั้งค่าภายใน Transaction เพื่อให้การจอง/เลื่อน/ยกเลิก/ตั้งค่า/วันหยุดตรวจ Capacity อย่างสอดคล้องกันทุก instance ฐานข้อมูลเดียวกัน Running ใช้ atomic upsert และ Unique Constraint รหัสจอง ทุกคำขอสร้างมี UUID idempotency key ป้องกัน Double Submit การแจ้งเตือนบันทึกใน Transaction เดียวกับการจองและส่ง SSE หลัง Commit

API Admin ต้องผ่าน Login และ POST ทุกตัวตรวจ CSRF Token ส่วนข้อมูลที่ผู้ใช้กรอกถูก escape ก่อนแสดงใน HTML

## ทดสอบ

สร้างฐานข้อมูลแยกชื่อ `db_queue_test` แล้วกำหนด `TEST_DB_PASSWORD` ก่อนรัน:

```powershell
$env:TEST_DB_PASSWORD = 'รหัสผ่าน PostgreSQL ของเครื่อง'
.\build.ps1 -Test
```

ชุดทดสอบล้างเฉพาะตารางใน `db_queue_test` ก่อนแต่ละกรณี มีการตรวจชื่อฐานข้อมูลก่อนล้าง ไม่ใช้ข้อมูลจริงใน `db_queue`

ครอบคลุม Capacity เมื่อคำขอเข้าพร้อมกัน, Running / Double Submit, ช่วงวันและเวลา, คำเตือนเบอร์ซ้ำ, สิทธิ์เลื่อน/ยกเลิก, วันหยุด, สถานะที่แสดง, Noti ที่บันทึกและอ่านแล้ว, Login / CSRF และการ render หน้าเว็บ

Java requirements อ้างอิง [Spring Boot documentation](https://docs.spring.io/spring-boot/3.5/system-requirements.html). Sneat ใช้ Asset ของรุ่นที่แนบโดยตรง และเก็บ License ใน `licenses/Sneat-LICENSE.md`
