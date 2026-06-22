package org.pluribourse.user.entities;

import jakarta.persistence.*;
import lombok.*;
import org.pluribourse.user.enums.*;

import java.io.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", nullable = false, length = 2)
    private Language preferredLanguage;

    @Column(name = "seller_profile_id")
    private Long sellerProfileId;

    @Column(name = "force_password_change", nullable = false)
    private boolean forcePasswordChange;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName = "";

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName = "";

    // Nullable wrapper: primitive boolean defaults to false during Java deserialization
    // of old Spring Session JDBC sessions, which would lock out existing users on upgrade.
    @Column(nullable = false)
    private Boolean enabled = true;
}
