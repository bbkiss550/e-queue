-- Run manually against the target PostgreSQL database before the first startup.
-- Structure only: this script does not insert users, shop settings or business hours.
-- Re-running preserves existing tables, bookings, users and shop settings.
-- Future schema changes must be applied explicitly by the database administrator.
BEGIN;

CREATE TABLE IF NOT EXISTS m_user (
  id_user BIGSERIAL PRIMARY KEY,
  username VARCHAR(60) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  create_date TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS m_booking_setting (
  id_setting INTEGER PRIMARY KEY CHECK (id_setting = 1),
  shop_name VARCHAR(100) NOT NULL,
  capacity_per_hour INTEGER NOT NULL CHECK (capacity_per_hour BETWEEN 1 AND 999),
  lead_minutes INTEGER NOT NULL CHECK (lead_minutes BETWEEN 0 AND 60),
  update_date TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS m_business_hour (
  day_of_week INTEGER PRIMARY KEY CHECK (day_of_week BETWEEN 1 AND 7),
  is_open BOOLEAN NOT NULL,
  open_time TIME NOT NULL,
  close_time TIME NOT NULL,
  CHECK (open_time < close_time)
);
CREATE TABLE IF NOT EXISTS m_holiday (
  holiday_date DATE PRIMARY KEY,
  description VARCHAR(200) NOT NULL DEFAULT 'ร้านปิด',
  create_date TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS t_booking (
  id_booking BIGSERIAL PRIMARY KEY,
  booking_code VARCHAR(9) NOT NULL UNIQUE,
  customer_name VARCHAR(100) NOT NULL,
  customer_phone VARCHAR(10) NOT NULL,
  booking_date DATE NOT NULL,
  booking_time TIME NOT NULL,
  status VARCHAR(12) NOT NULL CHECK (status IN ('BOOKED', 'CANCELLED')),
  cancellation_reason VARCHAR(200),
  request_key UUID NOT NULL UNIQUE,
  create_by VARCHAR(60) NOT NULL,
  create_date TIMESTAMPTZ NOT NULL DEFAULT now(),
  update_by VARCHAR(60) NOT NULL,
  update_date TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (extract(minute FROM booking_time) = 0 AND extract(second FROM booking_time) = 0),
  CHECK (customer_phone ~ '^0[0-9]{8,9}$')
);
CREATE INDEX IF NOT EXISTS booking_slots_idx ON t_booking (booking_date, booking_time) WHERE status='BOOKED';
CREATE INDEX IF NOT EXISTS booking_phone_idx ON t_booking (customer_phone, booking_date);
CREATE TABLE IF NOT EXISTS t_booking_running (
  running_date DATE PRIMARY KEY,
  last_number INTEGER NOT NULL CHECK (last_number BETWEEN 1 AND 999)
);
CREATE TABLE IF NOT EXISTS t_notification (
  id_notification BIGSERIAL PRIMARY KEY,
  id_booking BIGINT NOT NULL REFERENCES t_booking(id_booking),
  is_read BOOLEAN NOT NULL DEFAULT false,
  create_date TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMIT;
