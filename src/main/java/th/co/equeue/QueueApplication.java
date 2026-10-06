package th.co.equeue;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QueueApplication {
    public static void main(String[] args) { SpringApplication.run(QueueApplication.class, args); }
    @Bean public Clock bookingClock() { return Clock.system(ZoneId.of("Asia/Bangkok")); }
}
