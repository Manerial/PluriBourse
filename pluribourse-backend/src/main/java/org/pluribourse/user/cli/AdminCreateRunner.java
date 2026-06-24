package org.pluribourse.user.cli;

import lombok.*;
import org.pluribourse.user.entities.*;
import org.pluribourse.user.enums.*;
import org.pluribourse.user.repositories.*;
import org.springframework.boot.*;
import org.springframework.context.*;
import org.springframework.security.crypto.password.*;
import org.springframework.stereotype.*;

@Component
@RequiredArgsConstructor
public class AdminCreateRunner implements ApplicationRunner {

    private static final String CREATE_OPTION = AdminCliSupport.CREATE_ADMIN;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationContext applicationContext;
    private final AdminCliSupport support;

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption(CREATE_OPTION)) {
            return;
        }
        String login = support.extractLogin(args);
        performCreate(login);
        System.exit(SpringApplication.exit(applicationContext, () -> 0));
    }

    void performCreate(String login) {
        if (!userRepository.findByRole(Role.ADMIN).isEmpty()) {
            support.requireAdminAuth();
        }
        if (userRepository.existsByUsername(login)) {
            throw new IllegalStateException("Username '" + login + "' is already taken.");
        }
        User admin = new User();
        admin.setUsername(login);
        admin.setRole(Role.ADMIN);
        admin.setPreferredLanguage(Language.FR);
        admin.setLanguageInitialized(false);
        admin.setEnabled(true);

        String temporaryPassword = support.generatePassword();
        admin.setPassword(passwordEncoder.encode(temporaryPassword));
        admin.setForcePasswordChange(true);
        userRepository.save(admin);

        System.out.println("=== PluriBourse Admin Account Created ===");
        System.out.println("Login: " + login);
        System.out.println("Temporary password: " + temporaryPassword);
        System.out.println("Log in and change your password immediately.");
        System.out.println("=========================================");
    }
}
