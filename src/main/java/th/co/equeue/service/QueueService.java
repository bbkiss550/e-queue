package th.co.equeue.service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.co.equeue.domain.*;
import th.co.equeue.repository.QueueRepository;
import th.co.equeue.web.*;
import th.co.equeue.web.ApiModels.*;

@Service
public class QueueService {
    public record QueueChanged(String type,Long bookingId) {}
    public record Slot(LocalTime time,int remaining,boolean available,String reason) {}
    public record BookingView(long id,String bookingCode,String customerName,String customerPhone,LocalDate bookingDate,
        LocalTime bookingTime,String status,String displayStatus,boolean actionable,String cancellationReason,String createBy,Instant createDate) {}
    private final QueueRepository repo;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    public QueueService(QueueRepository repo,Clock clock,ApplicationEventPublisher events) { this.repo=repo;this.clock=clock;this.events=events; }
    public LocalDateTime now() { return LocalDateTime.now(clock); }
    public BookingView view(Booking b) {
        boolean actionable=b.status().equals("BOOKED") && b.bookingDate().atTime(b.bookingTime()).isAfter(now());
        return new BookingView(b.id(),b.bookingCode(),b.customerName(),b.customerPhone(),b.bookingDate(),b.bookingTime(),b.status(),b.status().equals("CANCELLED")?"ยกเลิก":actionable?"จองแล้ว":"สำเร็จ",actionable,b.cancellationReason(),b.createBy(),b.createDate());
    }
    @Transactional(readOnly=true)
    public Map<String,Object> settings() {
        var s=repo.setting();
        return Map.of("shopName",s.shopName(),"capacityPerHour",s.capacityPerHour(),"leadMinutes",s.leadMinutes(),"hours",repo.hours(),"holidays",repo.holidays(),"today",now().toLocalDate(),"now",now());
    }
    @Transactional(readOnly=true)
    public Map<String,Object> slots(LocalDate date) {
        BookingRules.horizon(date,now().toLocalDate());
        var s=repo.setting(); var h=repo.hours().get(date.getDayOfWeek().getValue()-1); boolean holiday=repo.holiday(date);
        List<Slot> slots=new ArrayList<>();
        if (h.open() && !holiday) for(int hour=0;hour<24;hour++) {
            LocalTime time=LocalTime.of(hour,0);
            if(time.isBefore(h.openTime()) || !time.isBefore(h.closeTime())) continue;
            int remaining=Math.max(0,s.capacityPerHour()-repo.count(date,time,0));
            boolean allowed=BookingRules.availableTime(date,time,h,false,s.leadMinutes(),now());
            slots.add(new Slot(time,remaining,allowed&&remaining>0,!allowed?"ปิดรับจอง":remaining==0?"เต็ม":""));
        }
        return Map.of("date",date,"slots",slots,"closed",holiday||!h.open(),"leadMinutes",s.leadMinutes());
    }
    @Transactional
    public BookingView create(CreateBooking req,String actor) {
        repo.lockSettings();
        String phone=BookingRules.phone(req.phone()); String name=req.name().trim();
        if(name.isBlank()) throw new ApiException("INVALID_NAME","กรุณากรอกชื่อผู้จอง");
        var existing=repo.byRequest(req.requestKey());
        if(existing.isPresent()) {
            var b=existing.get();
            if(!b.customerName().equals(name)||!b.customerPhone().equals(phone)||!b.bookingDate().equals(req.date())||!b.bookingTime().equals(req.time())) throw new ApiException("REQUEST_CONFLICT","รายการนี้ถูกส่งไปแล้ว กรุณาเริ่มการจองใหม่");
            return view(b);
        }
        validateSlot(req.date(),req.time(),0);
        if(repo.duplicate(phone,req.date())&&!req.duplicateConfirmed()) throw new ApiException("DUPLICATE_WARNING","เบอร์นี้มีรายการจองในวันที่เลือกแล้ว ต้องการจองเพิ่มหรือไม่?");
        LocalDate today=now().toLocalDate(); int sequence=repo.nextNumber(today);
        if(sequence==0) throw new ApiException("RUNNING_FULL","วันนี้มีรายการครบ 999 รายการแล้ว กรุณาติดต่อร้าน");
        String code=today.format(DateTimeFormatter.ofPattern("yyMMdd"))+String.format("%03d",sequence);
        var booking=repo.insert(code,name,phone,req.date(),req.time(),req.requestKey(),actor);
        repo.db().update("INSERT INTO t_notification(id_booking) VALUES (?)",booking.id());
        events.publishEvent(new QueueChanged("booking-created",booking.id()));
        return view(booking);
    }
    @Transactional
    public BookingView reschedule(ChangeRequest req,String actor,boolean admin) {
        repo.lockSettings(); var booking=authorized(req.id(),req.phone(),admin); ensureActionable(booking);
        if(req.time()==null) throw new ApiException("INVALID_TIME","กรุณาเลือกเวลาใหม่");
        if(req.time().equals(booking.bookingTime())) throw new ApiException("SAME_TIME","กรุณาเลือกเวลาใหม่ที่ต่างจากเวลาเดิม");
        validateSlot(booking.bookingDate(),req.time(),booking.id()); repo.reschedule(booking.id(),req.time(),actor);
        events.publishEvent(new QueueChanged("booking-changed",booking.id())); return view(repo.find(booking.id()).orElseThrow());
    }
    @Transactional
    public BookingView cancel(ChangeRequest req,String actor,boolean admin) {
        repo.lockSettings(); var booking=authorized(req.id(),req.phone(),admin); ensureActionable(booking);
        repo.cancel(booking.id(),admin?"ยกเลิกโดยผู้ดูแล":"ยกเลิกโดยผู้จอง",actor);
        events.publishEvent(new QueueChanged("booking-changed",booking.id())); return view(repo.find(booking.id()).orElseThrow());
    }
    @Transactional(readOnly=true)
    public List<BookingView> lookup(String phone) { return repo.forPhone(BookingRules.phone(phone)).stream().map(this::view).toList(); }
    @Transactional(readOnly=true)
    public BookingView detail(long id) { return view(repo.find(id).orElseThrow(()->new ApiException("NOT_FOUND","ไม่พบรายการจอง"))); }
    @Transactional(readOnly=true)
    public List<BookingView> list(ListRequest req) {
        if(req.to().isBefore(req.from()) || req.to().isAfter(req.from().plusDays(42))) throw new ApiException("INVALID_RANGE","กรุณาเลือกช่วงวันที่ไม่เกิน 42 วัน");
        return repo.between(req.from(),req.to(),req.search()==null?"":req.search().trim()).stream().map(this::view).toList();
    }
    @Transactional
    public Map<String,Object> saveSettings(SettingsRequest req) {
        repo.lockSettings();
        if(req.hours().stream().map(HourRequest::dayOfWeek).distinct().count()!=7) throw new ApiException("INVALID_HOURS","กรุณากำหนดเวลาให้ครบทั้ง 7 วัน");
        for(var h:req.hours()) if(!h.openTime().isBefore(h.closeTime())) throw new ApiException("INVALID_HOURS","เวลาปิดต้องอยู่หลังเวลาเปิดภายในวันเดียวกัน");
        var future=repo.between(now().toLocalDate(),now().toLocalDate().plusDays(2),"").stream().filter(b->view(b).actionable()).toList();
        for(var b:future) {
            var h=req.hours().stream().filter(x->x.dayOfWeek()==b.bookingDate().getDayOfWeek().getValue()).findFirst().orElseThrow();
            if(!h.open() || b.bookingTime().isBefore(h.openTime()) || !b.bookingTime().isBefore(h.closeTime()) || repo.count(b.bookingDate(),b.bookingTime(),0)>req.capacityPerHour())
                throw new ApiException("EXISTING_BOOKINGS","การตั้งค่านี้กระทบรายการจองเดิม กรุณาเลื่อนหรือยกเลิกรายการที่เกี่ยวข้องก่อน");
        }
        repo.db().update("UPDATE m_booking_setting SET shop_name=?,capacity_per_hour=?,lead_minutes=?,update_date=now() WHERE id_setting=1",req.shopName().trim(),req.capacityPerHour(),req.leadMinutes());
        for(var h:req.hours()) repo.db().update("UPDATE m_business_hour SET is_open=?,open_time=?,close_time=? WHERE day_of_week=?",h.open(),h.openTime(),h.closeTime(),h.dayOfWeek());
        events.publishEvent(new QueueChanged("settings-changed",null)); return settings();
    }
    @Transactional
    public Map<String,Object> addHoliday(HolidayRequest req,String actor) {
        repo.lockSettings();
        if(req.date().isBefore(now().toLocalDate())) throw new ApiException("INVALID_DATE","กรุณาเลือกวันหยุดตั้งแต่วันนี้เป็นต้นไป");
        String description=req.description()==null||req.description().isBlank()?"ร้านปิด":req.description().trim();
        repo.db().update("INSERT INTO m_holiday(holiday_date,description) VALUES (?,?) ON CONFLICT (holiday_date) DO UPDATE SET description=excluded.description",req.date(),description);
        int cancelled=repo.db().update("UPDATE t_booking SET status='CANCELLED',cancellation_reason='ร้านปิด',update_by=?,update_date=now() WHERE booking_date=? AND status='BOOKED' AND (booking_date+booking_time)>?",actor,req.date(),now());
        events.publishEvent(new QueueChanged("holiday-changed",null)); return Map.of("cancelled",cancelled,"settings",settings());
    }
    @Transactional
    public void deleteHoliday(LocalDate date) { repo.lockSettings();repo.db().update("DELETE FROM m_holiday WHERE holiday_date=?",date);events.publishEvent(new QueueChanged("holiday-changed",null)); }
    @Transactional(readOnly=true)
    public Map<String,Object> notifications(Long before) {
        if(before!=null && before<1) throw new ApiException("INVALID_NOTIFICATION","ข้อมูลแจ้งเตือนไม่ถูกต้อง");
        var rows=repo.notifications(before);
        var items=rows.subList(0,Math.min(4,rows.size()));
        return Map.of("items",items,"unread",repo.db().queryForObject("SELECT count(*) FROM t_notification WHERE is_read=false",Integer.class),"hasMore",rows.size()>4,"nextBefore",items.isEmpty()?0L:items.getLast().get("id"));
    }
    public Map<String,Object> notifications() { return notifications(null); }
    @Transactional
    public void readNotifications(Long id) {
        if(id==null) repo.db().update("UPDATE t_notification SET is_read=true WHERE is_read=false");
        else if(repo.db().update("UPDATE t_notification SET is_read=true WHERE id_notification=?",id)==0) throw new ApiException("NOT_FOUND","ไม่พบแจ้งเตือนนี้");
        events.publishEvent(new QueueChanged("notifications-read",null));
    }
    @Transactional
    public void readNotifications() { readNotifications(null); }
    private Booking authorized(long id,String phone,boolean admin) {
        var booking=repo.find(id).orElseThrow(()->new ApiException("NOT_FOUND","ไม่พบรายการจอง"));
        if(!admin && !booking.customerPhone().equals(BookingRules.phone(phone))) throw new ApiException("NOT_FOUND","ไม่พบรายการจอง");
        return booking;
    }
    private void ensureActionable(Booking b) { if(!view(b).actionable()) throw new ApiException("BOOKING_LOCKED","รายการนี้ยกเลิกแล้วหรือเลยเวลานัด ไม่สามารถเลื่อนหรือยกเลิกได้"); }
    private void validateSlot(LocalDate date,LocalTime time,long excludeId) {
        BookingRules.horizon(date,now().toLocalDate()); var s=repo.setting(); var h=repo.hours().get(date.getDayOfWeek().getValue()-1);
        if(!BookingRules.availableTime(date,time,h,repo.holiday(date),s.leadMinutes(),now())) throw new ApiException("SLOT_UNAVAILABLE","เวลานี้ปิดรับจองแล้ว กรุณาเลือกเวลาอื่น");
        if(repo.count(date,time,excludeId)>=s.capacityPerHour()) throw new ApiException("SLOT_FULL","ช่วงเวลานี้เต็มแล้ว กรุณาเลือกเวลาอื่น");
    }
}
