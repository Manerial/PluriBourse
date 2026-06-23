package org.pluribourse.user.cli;

import lombok.*;
import org.pluribourse.user.enums.*;
import org.pluribourse.user.repositories.*;
import org.springframework.boot.*;
import org.springframework.context.*;
import org.springframework.security.crypto.password.*;
import org.springframework.stereotype.*;

@Component
@RequiredArgsConstructor
public class AdminPasswordResetRunner implements ApplicationRunner {

    private static final String RESET_OPTION = AdminCliSupport.RESET_ADMIN_PASSWORD;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationContext applicationContext;
    private final AdminCliSupport support;

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption(RESET_OPTION)) {
            return;
        }
        String login = support.extractLogin(args);
        performReset(login);
        System.exit(SpringApplication.exit(applicationContext, () -> 0));
    }

    void performReset(String login) {
        var admins = userRepository.findByRole(Role.ADMIN);
        if (admins.isEmpty()) {
            throw new IllegalStateException("No admin account found. Use --create-admin to create one.");
        }
        if (admins.size() > 1) {
            support.requireAdminAuth();
        }
        var admin = admins.stream()
                .filter(u -> u.getUsername().equals(login))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No admin account with login '" + login + "'."));

        var temporaryPassword = support.generatePassword();
        admin.setPassword(passwordEncoder.encode(temporaryPassword));
        admin.setForcePasswordChange(true);
        userRepository.save(admin);

        System.out.println("=== PluriBourse Admin Password Reset ===");
        System.out.println("Temporary password: " + temporaryPassword);
        System.out.println("Log in and change your password immediately.");
        System.out.println("========================================");
    }
}
