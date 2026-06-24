package org.pluribourse;

import org.pluribourse.user.cli.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;

import java.util.*;

@SpringBootApplication
public class PluribourseApplication {

    private static final Set<String> CLI_OPTIONS = Set.of(
            "--" + AdminCliSupport.RESET_ADMIN_PASSWORD,
            "--" + AdminCliSupport.CREATE_ADMIN
    );

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(PluribourseApplication.class);
        boolean isCLI = Arrays.stream(args)
                .anyMatch(CLI_OPTIONS::contains);
        if (isCLI) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        app.run(args);
    }

}
