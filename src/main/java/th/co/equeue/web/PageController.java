package th.co.equeue.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import th.co.equeue.service.QueueService;

@Controller
public class PageController {
    private final QueueService service;
    public PageController(QueueService service) {this.service=service;}
    @ModelAttribute public void common(Model model) { model.addAttribute("settings",service.settings()); }
    @GetMapping("/") public String home() {return "customer/home";}
    @GetMapping("/booking") public String booking() {return "customer/booking";}
    @GetMapping("/check") public String check() {return "customer/check";}
    @GetMapping({"/admin/login","/admin/login-error"}) public String login(jakarta.servlet.http.HttpServletRequest req,Model model) {model.addAttribute("loginError",req.getRequestURI().endsWith("login-error"));return "admin/login";}
    @GetMapping({"/admin","/admin/bookings"}) public String admin(Model model) {model.addAttribute("activePage","bookings");return "admin/bookings";}
    @GetMapping("/admin/settings") public String settings(Model model) {model.addAttribute("activePage","settings");return "admin/settings";}
}
