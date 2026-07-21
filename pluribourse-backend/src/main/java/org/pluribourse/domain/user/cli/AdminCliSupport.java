package org.pluribourse.domain.user.cli;

import lombok.*;
import org.pluribourse.domain.user.entities.*;
import org.pluribourse.domain.user.enums.*;
import org.pluribourse.domain.user.repositories.*;
import org.springframework.boot.*;
import org.springframework.security.crypto.password.*;
import org.springframework.stereotype.*;

import java.io.*;
import java.security.*;
import java.util.*;
import java.util.stream.*;

@Component
@RequiredArgsConstructor
public class AdminCliSupport {

    public static final String RESET_ADMIN_PASSWORD = "reset-admin-password";
    public static final String CREATE_ADMIN = "create-admin";

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int PASSWORD_LENGTH = 12;
    // Shared so that buffered bytes read by readLine() are not lost when readPassword() reads next
    private static final Scanner STDIN = new Scanner(System.in);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    String generatePassword() {
        SecureRandom random = new SecureRandom();
        return random.ints(PASSWORD_LENGTH, 0, CHARS.length())
                .mapToObj(i -> String.valueOf(CHARS.charAt(i)))
                .collect(Collectors.joining());
    }

    String extractLogin(ApplicationArguments args) {
        List<String> values = args.getOptionValues("login");
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
        String login = readLine();
        char[] password = readPassword();

        User admin = userRepository.findByUsername(login)
                .filter(u -> u.getRole() == Role.ADMIN)
                .orElseThrow(() -> new IllegalStateException("Authentication failed."));
        if (!passwordEncoder.matches(new String(password), admin.getPassword())) {
            throw new IllegalStateException("Authentication failed.");
        }
    }

    String readLine() {
        return STDIN.nextLine().strip();
    }

    char[] readPassword() {
        Console console = System.console();
        if (console != null) {
            return console.readPassword("Password: ");
        }
        // If the system console is not defined, read it from the normal STDIN
        System.out.print("Password (warning: input not hidden): ");
        return STDIN.nextLine().toCharArray();
    }
}
