package com.example.HRMS.regression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.HRMS.auth.entity.UserStatus;
import com.example.HRMS.common.security.ScopeType;
import java.nio.ByteBuffer;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Regression: MFA challenge/verify flow. Uses the existing TOTP mechanism; the
 * valid code is computed with the same RFC 6238 algorithm the server uses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MfaRegressionTests {

    // Base32 of the 20-byte ASCII secret "12345678901234567890" (RFC 6238 test key).
    private static final String MFA_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RbacTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures.resetIdentityData();
        fixtures.createUser("mfauser", ScopeType.PLATFORM, null, UserStatus.ACTIVE,
                true, MFA_SECRET, false);
    }

    private String loginForChallenge() throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"mfauser\",\"password\":\""
                                + RbacTestFixtures.PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MFA_REQUIRED"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("mfaToken").asString();
    }

    @Test
    void validMfaFlowIssuesTokens() throws Exception {
        String mfaToken = loginForChallenge();
        String code = currentTotp(MFA_SECRET);

        String json = mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType("application/json")
                        .content("{\"mfaToken\":\"" + mfaToken + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("accessToken").asString()).isNotBlank();
    }

    @Test
    void invalidMfaCodeIsUnauthorizedAndIssuesNoToken() throws Exception {
        String mfaToken = loginForChallenge();
        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType("application/json")
                        .content("{\"mfaToken\":\"" + mfaToken + "\",\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void invalidChallengeTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType("application/json")
                        .content("{\"mfaToken\":\"not-a-real-token\",\"code\":\"123456\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // --- RFC 6238 TOTP (HMAC-SHA1, 6 digits, 30s) matching the server verifier ---
    private static String currentTotp(String base32Secret) throws Exception {
        byte[] key = base32Decode(base32Secret);
        long step = Instant.now().getEpochSecond() / 30;
        byte[] data = ByteBuffer.allocate(Long.BYTES).putLong(step).array();
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        byte[] hash = mac.doFinal(data);
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int otp = binary % 1_000_000;
        return String.format("%06d", otp);
    }

    private static byte[] base32Decode(String input) {
        String cleaned = input.trim().replace("=", "").toUpperCase();
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        int bits = 0;
        int value = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (char c : cleaned.toCharArray()) {
            int idx = alphabet.indexOf(c);
            if (idx < 0) {
                continue;
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
