package org.pluribourse.user.cli;

import lombok.RequiredArgsConstructor;
import org.pluribourse.user.enums.Role;
import org.pluribourse.user.repositories.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Scanner;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AdminCliSupport {

    public static final String RESET_ADMIN_PASSWORD = "reset-admin-password";
    public static final String CREATE_ADMIN = "create-admin";

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    String generatePassword() {
        var random = new SecureRandom();
        return random.ints(PASSWORD_LENGTH, 0, CHARS.length())
                .mapToObj(i -> String.valueOf(CHARS.charAt(i)))
                .collect(Collectors.joining());
    }

    String extractLogin(ApplicationArguments args) {
        var values = args.getOptionValues("login");
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException("Missing required argument: --login=<username>");
        }
        return values.getFirst();
    }

    /**
     * Prompts for credentials of an existing admin and verifies them.
     * Always reports "Authentication failed." regardless of failure cause to prevent user enumeration.
     */
    void requireAdminAuth() {
        System.out.println("Authentication required — enter credentials of an existing admin:");
        System.out.print("Login: ");
        var login = readLine();
        var password = readPassword();

        var admin = userRepository.findByUsername(login)
                .filter(u -> u.getRole() == Role.ADMIN)
                .orElseThrow(() -> new IllegalStateException("Authentication failed."));
        if (!passwordEncoder.matches(new String(password), admin.getPassword())) {
            throw new IllegalStateException("Authentication failed.");
        }
    }

    String readLine() {
        return new Scanner(System.in).nextLine().strip();
    }

    char[] readPassword() {
        var console = System.console();
        if (console != null) {
            return console.readPassword("Password: ");
        }
        System.out.print("Password (warning: input not hidden): ");
        return new Scanner(System.in).nextLine().toCharArray();
    }
}
