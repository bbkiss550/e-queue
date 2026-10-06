package th.co.equeue;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import th.co.equeue.service.*;
import th.co.equeue.web.*;
import th.co.equeue.web.ApiModels.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:postgresql://localhost:5432/db_queue_test",
    "spring.datasource.password=${TEST_DB_PASSWORD}","app.remember-key=test-key-only"})
@AutoConfigureMockMvc
@Import(QueueIntegrationTest.TestClock.class)
class QueueIntegrationTest {
    @TestConfiguration static class TestClock { @Bean @Primary Clock testClock(){ return Clock.fixed(Instant.parse("2026-10-06T02:33:00Z"),ZoneId.of("Asia/Bangkok")); } }
    @Autowired QueueService service; @Autowired JdbcTemplate db; @Autowired MockMvc mvc;
    final LocalDate today=LocalDate.of(2026,10,6);
    @BeforeEach void clean(){
        assertEquals("db_queue_test",db.queryForObject("SELECT current_database()",String.class));
        db.execute("TRUNCATE t_notification,t_booking,t_booking_running,m_holiday RESTART IDENTITY");
        db.update("UPDATE m_booking_setting SET shop_name='ABC Car Care',capacity_per_hour=3,lead_minutes=60");
        db.update("UPDATE m_business_hour SET is_open=true,open_time='09:00',close_time='18:00'");
    }
    CreateBooking request(LocalDate date,int hour,String phone,UUID key,boolean duplicate){return new CreateBooking(date,LocalTime.of(hour,0),"ทดสอบระบบ",phone,key,duplicate);}
    CreateBooking request(int hour,String phone){return request(today,hour,phone,UUID.randomUUID(),false);}
    String fail(Runnable action){return assertThrows(ApiException.class,action::run).code();}
    @Test void applicationStartsWithoutInsertingInitialData(){
        String schema="e_queue_no_seed_startup_test";
        String url="jdbc:postgresql://localhost:5432/db_queue_test?currentSchema="+schema;
        String password=System.getenv("TEST_DB_PASSWORD");
        db.execute("CREATE SCHEMA "+schema);
        try {
            var isolated=new DriverManagerDataSource(url,"postgres",password);
            new ResourceDatabasePopulator(new FileSystemResource("database/schema.sql")).execute(isolated);
            new WebApplicationContextRunner().withUserConfiguration(QueueApplication.class)
                .withPropertyValues("spring.datasource.url="+url,"spring.datasource.username=postgres",
                    "spring.datasource.password="+password,"spring.sql.init.mode=never","app.remember-key=test-key-only")
                .run(context->{
                    assertNull(context.getStartupFailure());
                    var emptyDb=context.getBean(JdbcTemplate.class);
                    for(String table:List.of("m_user","m_booking_setting","m_business_hour","m_holiday","t_booking","t_booking_running","t_notification"))
                        assertEquals(0,emptyDb.queryForObject("SELECT count(*) FROM "+table,Integer.class),table);
                });
        } finally {db.execute("DROP SCHEMA "+schema+" CASCADE");}
    }
    @Test void leadTimeRoundsToNextWholeHour(){assertEquals("SLOT_UNAVAILABLE",fail(()->service.create(request(10,"0811111111"),"CUSTOMER")));var b=service.create(request(11,"0811111111"),"CUSTOMER");assertEquals(LocalTime.of(11,0),b.bookingTime());}
    @Test void noHalfHourBookings(){var req=new CreateBooking(today,LocalTime.of(11,30),"ชื่อ","0811111111",UUID.randomUUID(),false);assertEquals("SLOT_UNAVAILABLE",fail(()->service.create(req,"CUSTOMER")));}
    @Test void dateHorizonInclusive(){service.create(request(today.plusDays(2),9,"0811111111",UUID.randomUUID(),false),"CUSTOMER");assertEquals("INVALID_DATE",fail(()->service.create(request(today.plusDays(3),9,"0822222222",UUID.randomUUID(),false),"CUSTOMER")));assertEquals("INVALID_DATE",fail(()->service.create(request(today.minusDays(1),9,"0822222222",UUID.randomUUID(),false),"CUSTOMER")));}
    @Test void runningUsesCreationDateNotAppointment(){var b=service.create(request(today.plusDays(2),9,"0811111111",UUID.randomUUID(),false),"CUSTOMER");assertEquals("261006001",b.bookingCode());}
    @Test void idempotentSubmissionCreatesOneNotification(){var req=request(11,"0811111111");var a=service.create(req,"CUSTOMER");var b=service.create(req,"CUSTOMER");assertEquals(a.id(),b.id());assertEquals(1,db.queryForObject("SELECT count(*) FROM t_notification",Integer.class));}
    @Test void idempotencyKeyCannotChangePayload(){var key=UUID.randomUUID();service.create(request(today,11,"0811111111",key,false),"CUSTOMER");assertEquals("REQUEST_CONFLICT",fail(()->service.create(request(today,12,"0811111111",key,true),"CUSTOMER")));}
    @Test void duplicatePhoneRequiresAcknowledgement(){service.create(request(11,"0811111111"),"CUSTOMER");var req=request(12,"0811111111");assertEquals("DUPLICATE_WARNING",fail(()->service.create(req,"CUSTOMER")));service.create(new CreateBooking(req.date(),req.time(),req.name(),req.phone(),req.requestKey(),true),"CUSTOMER");assertEquals(2,service.lookup("081-111-1111").size());}
    @Test void parallelRequestsNeverOverbookAndCodesStayUnique() throws Exception {
        try(var pool=Executors.newFixedThreadPool(12)){
            List<Future<String>> futures=new ArrayList<>();for(int i=0;i<20;i++){final String phone=String.format("08%08d",i);futures.add(pool.submit(()->{try{return service.create(request(11,phone),"CUSTOMER").bookingCode();}catch(ApiException e){assertEquals("SLOT_FULL",e.code());return null;}}));}
            Set<String> codes=new HashSet<>();for(var f:futures){var code=f.get(30,TimeUnit.SECONDS);if(code!=null)codes.add(code);}assertEquals(Set.of("261006001","261006002","261006003"),codes);assertEquals(3,db.queryForObject("SELECT count(*) FROM t_booking",Integer.class));
        }
    }
    @Test void parallelDoubleSubmitIsIdempotent() throws Exception {var req=request(11,"0811111111");try(var pool=Executors.newFixedThreadPool(8)){List<Callable<Long>> calls=new ArrayList<>();for(int i=0;i<8;i++)calls.add(()->service.create(req,"CUSTOMER").id());for(var f:pool.invokeAll(calls))assertEquals(1L,f.get());}assertEquals(1,db.queryForObject("SELECT count(*) FROM t_booking",Integer.class));}
    @Test void rescheduleKeepsCodeAndDate(){var b=service.create(request(11,"0811111111"),"CUSTOMER");var moved=service.reschedule(new ChangeRequest(b.id(),LocalTime.of(12,0),"0811111111"),"CUSTOMER",false);assertEquals(b.bookingCode(),moved.bookingCode());assertEquals(b.bookingDate(),moved.bookingDate());}
    @Test void capacityAppliesOnReschedule(){db.update("UPDATE m_booking_setting SET capacity_per_hour=1");var b=service.create(request(11,"0811111111"),"CUSTOMER");service.create(request(12,"0822222222"),"CUSTOMER");assertEquals("SLOT_FULL",fail(()->service.reschedule(new ChangeRequest(b.id(),LocalTime.of(12,0),null),"admin",true)));}
    @Test void wrongPhoneCannotChangeBooking(){var b=service.create(request(11,"0811111111"),"CUSTOMER");assertEquals("NOT_FOUND",fail(()->service.cancel(new ChangeRequest(b.id(),null,"0822222222"),"CUSTOMER",false)));assertEquals("BOOKED",service.detail(b.id()).status());}
    @Test void cancellationReleasesCapacity(){db.update("UPDATE m_booking_setting SET capacity_per_hour=1");var b=service.create(request(11,"0811111111"),"CUSTOMER");service.cancel(new ChangeRequest(b.id(),null,"0811111111"),"CUSTOMER",false);assertEquals("CANCELLED",service.detail(b.id()).status());service.create(request(11,"0822222222"),"CUSTOMER");}
    @Test void elapsedBookingDisplaysSuccessAndIsLocked(){var b=service.create(request(11,"0811111111"),"CUSTOMER");db.update("UPDATE t_booking SET booking_time='09:00' WHERE id_booking=?",b.id());assertEquals("สำเร็จ",service.detail(b.id()).displayStatus());assertEquals("BOOKED",service.detail(b.id()).status());assertEquals("BOOKING_LOCKED",fail(()->service.cancel(new ChangeRequest(b.id(),null,null),"admin",true)));assertEquals("BOOKING_LOCKED",fail(()->service.reschedule(new ChangeRequest(b.id(),LocalTime.of(12,0),null),"admin",true)));}
    @Test void holidayCancelsFutureWithReasonAndPreservesElapsed(){var b=service.create(request(11,"0811111111"),"CUSTOMER");var old=service.create(request(12,"0822222222"),"CUSTOMER");db.update("UPDATE t_booking SET booking_time='09:00' WHERE id_booking=?",old.id());var result=service.addHoliday(new HolidayRequest(today,"วันหยุด"),"admin");assertEquals(1,result.get("cancelled"));assertEquals("ร้านปิด",service.detail(b.id()).cancellationReason());assertEquals("สำเร็จ",service.detail(old.id()).displayStatus());assertEquals("SLOT_UNAVAILABLE",fail(()->service.create(request(13,"0833333333"),"CUSTOMER")));service.deleteHoliday(today);assertEquals("CANCELLED",service.detail(b.id()).status());}
    @Test void settingsCannotStrandExistingBookings(){service.create(request(11,"0811111111"),"CUSTOMER");var hours=new ArrayList<HourRequest>();for(int d=1;d<=7;d++)hours.add(new HourRequest(d,true,LocalTime.of(12,0),LocalTime.of(18,0)));assertEquals("EXISTING_BOOKINGS",fail(()->service.saveSettings(new SettingsRequest("ร้าน",3,60,hours))));assertEquals("ABC Car Care",service.settings().get("shopName"));}
    @Test void weeklyClosedDayBlocksBooking(){db.update("UPDATE m_business_hour SET is_open=false WHERE day_of_week=2");assertEquals("SLOT_UNAVAILABLE",fail(()->service.create(request(11,"0811111111"),"CUSTOMER")));assertEquals(true,service.slots(today).get("closed"));}
    @Test void runningLimitRollsBackCleanly(){db.update("INSERT INTO t_booking_running VALUES (?,999)",today);assertEquals("RUNNING_FULL",fail(()->service.create(request(11,"0811111111"),"CUSTOMER")));assertEquals(0,db.queryForObject("SELECT count(*) FROM t_booking",Integer.class));}
    @Test void invalidPhoneIsRejected(){assertEquals("INVALID_PHONE",fail(()->service.create(request(11,"1234"),"CUSTOMER")));}
    @Test void notificationReadStatePersists(){service.create(request(11,"0811111111"),"CUSTOMER");assertEquals(1,service.notifications().get("unread"));service.readNotifications();assertEquals(0,service.notifications().get("unread"));}
    @Test void readingOneNotificationLeavesOthersUnread(){
        service.create(request(11,"0811111111"),"CUSTOMER");service.create(request(12,"0822222222"),"CUSTOMER");
        service.readNotifications(1L);service.readNotifications(1L);
        assertEquals(1,service.notifications().get("unread"));
        assertEquals(true,db.queryForObject("SELECT is_read FROM t_notification WHERE id_notification=1",Boolean.class));
        assertEquals(false,db.queryForObject("SELECT is_read FROM t_notification WHERE id_notification=2",Boolean.class));
        assertEquals("NOT_FOUND",fail(()->service.readNotifications(999L)));
    }
    @Test void notificationsPageByFourWithoutDuplicatesWhenNewBookingArrives(){
        for(int i=0;i<9;i++)service.create(request(11+i/3,String.format("08%08d",i)),"CUSTOMER");
        var first=service.notifications();assertEquals(9,first.get("unread"));assertEquals(true,first.get("hasMore"));
        assertEquals(4,((List<?>)first.get("items")).size());assertEquals(6L,first.get("nextBefore"));
        service.create(request(14,"0899999999"),"CUSTOMER");
        var second=service.notifications(6L);assertEquals(4,((List<?>)second.get("items")).size());assertEquals(2L,second.get("nextBefore"));
        var last=service.notifications(2L);assertEquals(1,((List<?>)last.get("items")).size());assertEquals(false,last.get("hasMore"));
        assertEquals(1L,((Map<?,?>)((List<?>)last.get("items")).getFirst()).get("id"));
        assertEquals("INVALID_NOTIFICATION",fail(()->service.notifications(0L)));
    }
    @Test @WithMockUser(roles="ADMIN") void notificationApiReadsSingleAndAllAndKeepsReadState() throws Exception {
        service.create(request(11,"0811111111"),"CUSTOMER");service.create(request(12,"0822222222"),"CUSTOMER");
        mvc.perform(post("/api/admin/notifications/read").with(csrf()).contentType("application/json").content("{\"id\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.unread").value(1));
        mvc.perform(get("/api/admin/notifications").param("before","2")).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].isRead").value(true)).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(post("/api/admin/notifications/read").with(csrf()).contentType("application/json").content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.unread").value(0));
        mvc.perform(get("/api/admin/notifications")).andExpect(jsonPath("$.unread").value(0));
    }
    @Test void publicAndLoginPagesRender() throws Exception {for(String path:List.of("/","/booking","/check","/admin/login"))mvc.perform(get(path)).andExpect(status().isOk());}
    @Test void adminApiRequiresLogin() throws Exception {mvc.perform(get("/api/admin/notifications")).andExpect(status().isUnauthorized());mvc.perform(get("/admin/bookings")).andExpect(status().is3xxRedirection());}
    @Test void csrfAndPhoneValidationEnforced() throws Exception {String body="{\"date\":\"2026-10-06\",\"time\":\"11:00\",\"name\":\"ทดสอบ\",\"phone\":\"123\",\"requestKey\":\""+UUID.randomUUID()+"\"}";mvc.perform(post("/api/public/bookings").contentType("application/json").content(body)).andExpect(status().isForbidden());mvc.perform(post("/api/public/bookings").with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_PHONE"));}
    @Test @WithMockUser(roles="ADMIN") void adminPagesRender() throws Exception {mvc.perform(get("/admin/bookings")).andExpect(status().isOk());mvc.perform(get("/admin/settings")).andExpect(status().isOk());}
    @Test @WithMockUser(roles="ADMIN") void adminCannotEditCustomerOrDate() throws Exception {var b=service.create(request(11,"0811111111"),"CUSTOMER");mvc.perform(post("/api/admin/reschedule").with(csrf()).contentType("application/json").content("{\"id\":"+b.id()+",\"time\":\"12:00\",\"date\":\"2026-10-08\",\"name\":\"แก้ชื่อ\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.bookingDate").value("2026-10-06")).andExpect(jsonPath("$.customerName").value("ทดสอบระบบ"));}
}
