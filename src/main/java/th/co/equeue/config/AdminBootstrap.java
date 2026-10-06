package th.co.equeue.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final JdbcTemplate db; private final PasswordEncoder encoder; private final String username,password;
    public AdminBootstrap(JdbcTemplate db,PasswordEncoder encoder,@Value("${app.admin-username}") String username,@Value("${app.admin-password}") String password) {
        this.db=db;this.encoder=encoder;this.username=username;this.password=password;
    }
    public void run(ApplicationArguments args) {
        if(db.queryForObject("SELECT count(*) FROM m_user",Integer.class)==0) {
            if(password.length()<12) throw new IllegalStateException("กำหนด ADMIN_PASSWORD อย่างน้อย 12 ตัวอักษรสำหรับบัญชีแรก");
            db.update("INSERT INTO m_user(username,password_hash) VALUES (?,?) ON CONFLICT(username) DO NOTHING",username,encoder.encode(password));
        }
    }
}
