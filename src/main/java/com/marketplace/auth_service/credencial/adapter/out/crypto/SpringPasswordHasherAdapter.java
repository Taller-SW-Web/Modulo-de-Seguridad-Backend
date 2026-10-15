package com.marketplace.auth_service.credencial.adapter.out.crypto;

import com.marketplace.auth_service.credencial.application.port.out.PasswordHasherPort;
import com.marketplace.auth_service.credencial.domain.model.AlgoritmoHash;
import com.marketplace.auth_service.credencial.domain.model.HashContrasena;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class SpringPasswordHasherAdapter implements PasswordHasherPort {
    private static final int BCRYPT_MIN_STRENGTH = 12;
    private static final Pattern BCRYPT_STRENGTH =
            Pattern.compile("^\\$2[aby]\\$(\\d{2})\\$");
    private static final Pattern ARGON2ID_FORMAT =
            Pattern.compile("^\\$argon2id\\$v=19\\$m=\\d+,t=\\d+,p=\\d+\\$[^$]+\\$[^$]+$");

    private final PasswordEncoder argon2id =
            new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(BCRYPT_MIN_STRENGTH);

    @Override
    public HashContrasena generar(CharSequence secreto) {
        Objects.requireNonNull(secreto, "secreto");
        return new HashContrasena(argon2id.encode(secreto), AlgoritmoHash.ARGON2ID);
    }

    @Override
    public boolean verificar(CharSequence secreto, HashContrasena hash) {
        Objects.requireNonNull(secreto, "secreto");
        Objects.requireNonNull(hash, "hash");

        return switch (hash.algoritmo()) {
            case ARGON2ID -> isArgon2idFormat(hash.valor())
                    && argon2id.matches(secreto, hash.valor());
            case BCRYPT -> isBcryptStrengthSupported(hash.valor())
                    && bcrypt.matches(secreto, hash.valor());
        };
    }

    private boolean isArgon2idFormat(String encoded) {
        return ARGON2ID_FORMAT.matcher(encoded).matches();
    }

    private boolean isBcryptStrengthSupported(String encoded) {
        Matcher matcher = BCRYPT_STRENGTH.matcher(encoded);
        return matcher.find() && Integer.parseInt(matcher.group(1)) >= BCRYPT_MIN_STRENGTH;
    }
}
