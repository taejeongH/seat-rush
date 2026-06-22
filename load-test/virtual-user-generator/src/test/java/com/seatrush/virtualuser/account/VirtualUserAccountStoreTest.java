package com.seatrush.virtualuser.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.seatrush.virtualuser.config.VirtualUserProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VirtualUserAccountStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createOnlyAccountsRequiredBeyondStoredPool() {
        VirtualUserAccountStore store = store();

        List<VirtualUserAccount> first = store.ensureCapacity(10);
        List<VirtualUserAccount> second = store.ensureCapacity(12);

        assertThat(first).hasSize(10);
        assertThat(second).hasSize(12);
        assertThat(store.size()).isEqualTo(12);
        assertThat(second.get(10).email()).isEqualTo("virtual-user-11@seat-rush.local");
    }

    @Test
    void persistAccountRegistrationAndTokenForReuse() {
        VirtualUserAccountStore store = store();
        store.ensureCapacity(1);
        Instant expiresAt = Instant.now().plusSeconds(3600);
        store.markRegistered(1);
        store.updateToken(1, "access-token", expiresAt);
        store.save().block();

        VirtualUserAccountStore restored = store();
        VirtualUserAccount account = restored.get(1);

        assertThat(account.registered()).isTrue();
        assertThat(account.accessToken()).isEqualTo("access-token");
        assertThat(account.tokenExpiresAt()).isEqualTo(expiresAt);
    }

    private VirtualUserAccountStore store() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new VirtualUserAccountStore(objectMapper, properties());
    }

    private VirtualUserProperties properties() {
        return new VirtualUserProperties(
                "https://seat-rush.example.com",
                Duration.ofSeconds(10),
                Duration.ofSeconds(2),
                Duration.ofSeconds(10),
                Duration.ofMinutes(20),
                Duration.ofMillis(400),
                Duration.ofSeconds(15),
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                5,
                Duration.ofMillis(150),
                2,
                temporaryDirectory.resolve("accounts.json").toString(),
                "seat-rush.local",
                "Virtual-user-password!",
                Duration.ofMinutes(5),
                Duration.ofMinutes(10)
        );
    }
}
