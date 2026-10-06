package th.co.equeue.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService users(JdbcTemplate db) {
        return username -> db.query("SELECT username,password_hash FROM m_user WHERE username=?",(r,n)->User.withUsername(r.getString(1)).password(r.getString(2)).roles("ADMIN").build(),username)
            .stream().findFirst().orElseThrow(()->new UsernameNotFoundException("ไม่พบผู้ใช้"));
    }
    @Bean SecurityFilterChain security(HttpSecurity http,@Value("${app.remember-key}") String key) throws Exception {
        http.authorizeHttpRequests(auth->auth
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR,jakarta.servlet.DispatcherType.ASYNC).permitAll()
                .requestMatchers("/admin/login","/admin/login-error","/assets/**","/css/**","/js/**","/api/public/**","/","/booking","/check","/error").permitAll()
                .anyRequest().hasRole("ADMIN"))
            .formLogin(form->form.loginPage("/admin/login").loginProcessingUrl("/admin/login")
                .defaultSuccessUrl("/admin/bookings",true).failureUrl("/admin/login-error").permitAll())
            .logout(out->out.logoutUrl("/admin/logout").logoutSuccessUrl("/admin/login"))
            .exceptionHandling(errors->errors.authenticationEntryPoint((req,res,ex)->{
                if(req.getRequestURI().startsWith("/api/")) {res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"message\":\"กรุณาเข้าสู่ระบบใหม่\"}");}
                else res.sendRedirect("/admin/login");
            }).accessDeniedHandler((req,res,ex)->{res.setStatus(403);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"หน้าเว็บหมดอายุ กรุณารีเฟรชแล้วลองใหม่\"}");}));
        if(!key.isBlank()) http.rememberMe(remember->remember.key(key).tokenValiditySeconds(7*24*60*60));
        return http.build();
    }
}
