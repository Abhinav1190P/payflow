package com.payflow.auth;

import com.payflow.common.error.ConflictException;
import com.payflow.common.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Writes a new user in a short transaction. The password arrives already hashed: BCrypt takes around 100 ms,
// and doing it inside the transaction would hold a database connection for that whole time.
// The wallet is no longer created here: payment-service opens it lazily the first time the user touches it.
@Service
public class AccountRegistrar {

    private final UserRepository users;

    public AccountRegistrar(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public User create(String normalisedEmail, String passwordHash, Role role) {
        if (users.existsByEmailIgnoreCase(normalisedEmail)) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
        }
        return users.save(new User(normalisedEmail, passwordHash, role));
    }
}
