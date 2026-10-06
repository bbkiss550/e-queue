package th.co.equeue.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import th.co.equeue.domain.*;

@Repository
public class QueueRepository {
    private final JdbcTemplate db;
    public QueueRepository(JdbcTemplate db) { this.db=db; }
    public JdbcTemplate db() { return db; }
    public void lockSettings() { db.queryForObject("SELECT id_setting FROM m_booking_setting WHERE id_setting=1 FOR UPDATE", Integer.class); }
    public BookingSetting setting() { return db.queryForObject("SELECT * FROM m_booking_setting WHERE id_setting=1", (r,n)->new BookingSetting(r.getString("shop_name"),r.getInt("capacity_per_hour"),r.getInt("lead_minutes"))); }
    public List<BusinessHour> hours() { return db.query("SELECT * FROM m_business_hour ORDER BY day_of_week", (r,n)->new BusinessHour(r.getInt("day_of_week"),r.getBoolean("is_open"),r.getObject("open_time",LocalTime.class),r.getObject("close_time",LocalTime.class))); }
    public boolean holiday(LocalDate date) { return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM m_holiday WHERE holiday_date=?)",Boolean.class,date)); }
    public List<Map<String,Object>> holidays() { return db.queryForList("SELECT holiday_date AS date, description FROM m_holiday ORDER BY holiday_date DESC"); }
    public List<Booking> forPhone(String phone) { return db.query("SELECT * FROM t_booking WHERE customer_phone=? ORDER BY booking_date DESC, booking_time DESC, id_booking DESC",this::mapBooking,phone); }
    public List<Booking> between(LocalDate from, LocalDate to, String search) {
        String normalized=search.replaceAll("[\\s-]", "");
        return db.query("SELECT * FROM t_booking WHERE booking_date BETWEEN ? AND ? AND (strpos(lower(customer_name),lower(?))>0 OR strpos(customer_phone,?)>0) ORDER BY booking_date, booking_time, id_booking",this::mapBooking,from,to,search,normalized);
    }
    public Optional<Booking> find(long id) { return db.query("SELECT * FROM t_booking WHERE id_booking=?",this::mapBooking,id).stream().findFirst(); }
    public Optional<Booking> byRequest(UUID key) { return db.query("SELECT * FROM t_booking WHERE request_key=?",this::mapBooking,key).stream().findFirst(); }
    public int count(LocalDate date, LocalTime time, long excludeId) { return db.queryForObject("SELECT count(*) FROM t_booking WHERE booking_date=? AND booking_time=? AND status='BOOKED' AND id_booking<>?",Integer.class,date,time,excludeId); }
    public boolean duplicate(String phone, LocalDate date) { return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM t_booking WHERE customer_phone=? AND booking_date=? AND status='BOOKED')",Boolean.class,phone,date)); }
    public int nextNumber(LocalDate today) {
        List<Integer> values=db.query("INSERT INTO t_booking_running VALUES (?,1) ON CONFLICT (running_date) DO UPDATE SET last_number=t_booking_running.last_number+1 WHERE t_booking_running.last_number<999 RETURNING last_number",(r,n)->r.getInt(1),today);
        return values.isEmpty() ? 0 : values.getFirst();
    }
    public Booking insert(String code,String name,String phone,LocalDate date,LocalTime time,UUID key,String actor) {
        Long id=db.queryForObject("INSERT INTO t_booking (booking_code,customer_name,customer_phone,booking_date,booking_time,status,request_key,create_by,update_by) VALUES (?,?,?,?,?,'BOOKED',?,?,?) RETURNING id_booking",Long.class,code,name,phone,date,time,key,actor,actor);
        return find(id).orElseThrow();
    }
    public void reschedule(long id,LocalTime time,String actor) { db.update("UPDATE t_booking SET booking_time=?,update_by=?,update_date=now() WHERE id_booking=?",time,actor,id); }
    public void cancel(long id,String reason,String actor) { db.update("UPDATE t_booking SET status='CANCELLED',cancellation_reason=?,update_by=?,update_date=now() WHERE id_booking=?",reason,actor,id); }
    public List<Map<String,Object>> notifications(Long before) {
        return db.queryForList("SELECT n.id_notification AS id,n.is_read AS \"isRead\",n.create_date AS \"createdAt\",b.id_booking AS \"bookingId\",b.booking_code AS \"bookingCode\",b.customer_name AS \"customerName\",b.booking_date AS \"bookingDate\",b.booking_time AS \"bookingTime\" FROM t_notification n JOIN t_booking b USING(id_booking) WHERE n.id_notification < ? ORDER BY n.id_notification DESC LIMIT 5",before==null?Long.MAX_VALUE:before);
    }
    private Booking mapBooking(ResultSet r,int n) throws SQLException {
        return new Booking(r.getLong("id_booking"),r.getString("booking_code"),r.getString("customer_name"),r.getString("customer_phone"),r.getObject("booking_date",LocalDate.class),r.getObject("booking_time",LocalTime.class),r.getString("status"),r.getString("cancellation_reason"),r.getString("create_by"),r.getTimestamp("create_date").toInstant(),r.getTimestamp("update_date").toInstant());
    }
}
