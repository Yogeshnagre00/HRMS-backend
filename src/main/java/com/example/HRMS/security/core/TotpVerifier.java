package com.example.HRMS.security.core;

import java.nio.ByteBuffer;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Minimal TOTP verifier (RFC 6238, HMAC-SHA1, 6 digits, 30-second step) used as
 * the MFA foundation. It relies only on the JDK's {@code javax.crypto} — no new
 * dependency and no custom cryptography.
 *
 * <p>This is a foundation: the shared secret is a Base32 string stored per user.
 * Provisioning of the secret / authenticator enrolment is out of scope for
 * V0-003 and is documented as a boundary. A ±1 step window tolerates clock skew.
 */
@Component
public class TotpVerifier {

    private static final int DIGITS = 6;
    private static final int TIME_STEP_SECONDS = 30;
    private static final int ALLOWED_SKEW_STEPS = 1;
    private static final String HMAC_ALGORITHM = "HmacSHA1";

    /** Verify a candidate code against the secret at the current time. */
    public boolean verify(String base32Secret, String candidate) {
        if (base32Secret == null || candidate == null) {
            return false;
        }
        String normalized = candidate.trim();
        if (normalized.length() != DIGITS || !normalized.chars().allMatch(Character::isDigit)) {
            return false;
        }
        byte[] key = base32Decode(base32Secret);
        long currentStep = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        for (long i = -ALLOWED_SKEW_STEPS; i <= ALLOWED_SKEW_STEPS; i++) {
            if (generate(key, currentStep + i).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private String generate(byte[] key, long step) {
        byte[] data = ByteBuffer.allocate(Long.BYTES).putLong(step).array();
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute TOTP", ex);
        }
    }

    /** Minimal RFC 4648 Base32 decoder (upper-case, no padding required). */
    private byte[] base32Decode(String input) {
        String cleaned = input.trim().replace("=", "").toUpperCase();
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        int bits = 0;
        int value = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (char c : cleaned.toCharArray()) {
            int idx = alphabet.indexOf(c);
            if (idx < 0) {
                continue; // skip non-alphabet characters defensively
            }
            value = (value << 5) | idx;
            bits += 5;
            if (bits >= 8) {
                out.write((value >>> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
