package com.payflow.support;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.api.ApiResponse;
import com.payflow.common.error.NotFoundException;
import com.payflow.common.security.JwtService;
import com.payflow.common.security.Role;
import com.payflow.directory.AuthUserClient;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// Full payment-service + real Postgres in Docker. Users live in auth-service, so tests mint JWTs directly and
// the Feign client to auth-service is replaced by a Mockito mock backed by an in-memory directory.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final Map<String, UUID> DIRECTORY = new ConcurrentHashMap<>();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected JwtService jwtService;

    @MockBean
    protected AuthUserClient authUserClient;

    public record Account(UUID id, String email, String token) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    @BeforeEach
    void wireDirectory() {
        when(authUserClient.lookup(anyString(), anyString())).thenAnswer(inv -> {
            String email = inv.getArgument(0);
            UUID id = DIRECTORY.get(email);
            if (id == null) {
                throw new NotFoundException("Recipient");
            }
            return ApiResponse.of(new AuthUserClient.UserSummary(id, email, "USER"));
        });
    }

    protected Account register(String prefix) {
        return account(prefix, Role.USER);
    }

    protected Account admin() {
        return account("admin", Role.ADMIN);
    }

    private Account account(String prefix, Role role) {
        UUID id = UUID.randomUUID();
        String email = prefix + "-" + id.toString().substring(0, 8) + "@payflow.dev";
        DIRECTORY.put(email, id);
        return new Account(id, email, jwtService.issue(id, email, role));
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected static String newKey() {
        return "key-" + UUID.randomUUID();
    }
}
