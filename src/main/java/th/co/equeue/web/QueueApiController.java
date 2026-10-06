package th.co.equeue.web;

import java.security.Principal;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import th.co.equeue.service.*;
import th.co.equeue.web.ApiModels.*;

@RestController
@RequestMapping("/api")
public class QueueApiController {
    private final QueueService service; private final NotificationStream stream;
    public QueueApiController(QueueService service,NotificationStream stream) {this.service=service;this.stream=stream;}
    @GetMapping("/public/settings") public Object publicSettings() { return service.settings(); }
    @PostMapping("/public/slots") public Object slots(@Valid @RequestBody DateRequest req) { return service.slots(req.date()); }
    @PostMapping("/public/bookings") public Object create(@Valid @RequestBody CreateBooking req) { return service.create(req,"CUSTOMER"); }
    @PostMapping("/public/lookup") public Object lookup(@Valid @RequestBody PhoneRequest req) { return service.lookup(req.phone()); }
    @PostMapping("/public/reschedule") public Object reschedule(@Valid @RequestBody ChangeRequest req) { return service.reschedule(req,"CUSTOMER",false); }
    @PostMapping("/public/cancel") public Object cancel(@Valid @RequestBody ChangeRequest req) { return service.cancel(req,"CUSTOMER",false); }
    @PostMapping("/admin/list") public Object list(@Valid @RequestBody ListRequest req) {return service.list(req);}
    @PostMapping("/admin/detail") public Object detail(@Valid @RequestBody ChangeRequest req) {return service.detail(req.id());}
    @PostMapping("/admin/bookings") public Object adminCreate(@Valid @RequestBody CreateBooking req,Principal user) {return service.create(req,user.getName());}
    @PostMapping("/admin/reschedule") public Object adminReschedule(@Valid @RequestBody ChangeRequest req,Principal user) {return service.reschedule(req,user.getName(),true);}
    @PostMapping("/admin/cancel") public Object adminCancel(@Valid @RequestBody ChangeRequest req,Principal user) {return service.cancel(req,user.getName(),true);}
    @GetMapping("/admin/settings") public Object settings() {return service.settings();}
    @PostMapping("/admin/settings") public Object settingsSave(@Valid @RequestBody SettingsRequest req) {return service.saveSettings(req);}
    @PostMapping("/admin/holidays") public Object holiday(@Valid @RequestBody HolidayRequest req,Principal user) {return service.addHoliday(req,user.getName());}
    @PostMapping("/admin/holidays/remove") public Object removeHoliday(@Valid @RequestBody DateRequest req) {service.deleteHoliday(req.date());return Map.of("ok",true);}
    @GetMapping("/admin/notifications") public Object notifications(@RequestParam(required=false) Long before) {return service.notifications(before);}
    @PostMapping("/admin/notifications/read") public Object read(@Valid @RequestBody NotificationRead req) {service.readNotifications(req.id());return service.notifications();}
    @GetMapping(value="/admin/events",produces="text/event-stream") public SseEmitter events() {return stream.connect();}
}
