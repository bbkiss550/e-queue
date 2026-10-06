package th.co.equeue;

import static org.junit.jupiter.api.Assertions.*;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProfileConfigurationTest {
    private final ApplicationContextRunner runner=new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test void defaultProfileLoadsLocalConfiguration(){
        runner.withPropertyValues("spring.profiles.active=").run(context->{
            assertNull(context.getStartupFailure());
            assertArrayEquals(new String[]{"local"},context.getEnvironment().getDefaultProfiles());
            assertTrue(StreamSupport.stream(context.getEnvironment().getPropertySources().spliterator(),false)
                .anyMatch(source->source.getName().contains("application-local.yml")));
            assertEquals("never",context.getEnvironment().getProperty("spring.sql.init.mode"));
        });
    }

    @Test void explicitLocalProfileDoesNotLoadUatConfiguration(){
        runner.withPropertyValues("spring.profiles.active=local").run(context->{
            assertNull(context.getStartupFailure());
            assertArrayEquals(new String[]{"local"},context.getEnvironment().getActiveProfiles());
            assertFalse(StreamSupport.stream(context.getEnvironment().getPropertySources().spliterator(),false)
                .anyMatch(source->source.getName().contains("application-uat")));
            assertEquals("false",context.getEnvironment().getProperty("spring.thymeleaf.cache"));
        });
    }

    @Test void uatUsesPgVariablesAndNeverImportsLocalSecrets(){
        runner.withPropertyValues("spring.profiles.active=uat","PGHOST=uat-profile-test.invalid",
            "PGPORT=5432","PGDATABASE=profile_test_db","PGUSER=profile_test_user",
            "PGPASSWORD=profile-test-password-only","PGSSLMODE=require","PGCHANNELBINDING=require")
            .run(context->{
                assertNull(context.getStartupFailure());
                var environment=context.getEnvironment();
                assertEquals("jdbc:postgresql://uat-profile-test.invalid:5432/profile_test_db?sslmode=require&channelBinding=require",
                    environment.getProperty("spring.datasource.url"));
                assertEquals("profile_test_user",environment.getProperty("spring.datasource.username"));
                assertTrue("profile-test-password-only".equals(environment.getProperty("spring.datasource.password")));
                assertFalse(StreamSupport.stream(environment.getPropertySources().spliterator(),false)
                    .anyMatch(source->source.getName().contains("application-local")));
                assertEquals("never",environment.getProperty("spring.sql.init.mode"));
                assertEquals("true",environment.getProperty("spring.thymeleaf.cache"));
            });
    }
}
