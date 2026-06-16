package com.seatrush.virtualuser.account;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatrush.virtualuser.config.VirtualUserProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class VirtualUserAccountStore {

    private static final TypeReference<List<VirtualUserAccount>> ACCOUNT_LIST_TYPE =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;
    private final VirtualUserProperties properties;
    private final Path poolFile;
    private final Map<Integer, VirtualUserAccount> accounts = new LinkedHashMap<>();

    public VirtualUserAccountStore(
            ObjectMapper objectMapper,
            VirtualUserProperties properties
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.poolFile = Path.of(properties.accountPoolFile()).toAbsolutePath();
        load();
    }

    public synchronized List<VirtualUserAccount> ensureCapacity(int requiredCount) {
        for (int number = 1; number <= requiredCount; number++) {
            accounts.computeIfAbsent(number, this::newAccount);
        }
        return new ArrayList<>(accounts.values().stream().limit(requiredCount).toList());
    }

    public synchronized void markRegistered(int number) {
        VirtualUserAccount account = requireAccount(number);
        accounts.put(number, account.register());
    }

    public synchronized void updateToken(
            int number,
            String accessToken,
            java.time.Instant expiresAt
    ) {
        VirtualUserAccount account = requireAccount(number);
        accounts.put(number, account.authenticate(accessToken, expiresAt));
    }

    public synchronized VirtualUserAccount get(int number) {
        return requireAccount(number);
    }

    public synchronized int size() {
        return accounts.size();
    }

    public Mono<Void> save() {
        return Mono.fromRunnable(this::writeSnapshot)
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private synchronized void load() {
        if (!Files.exists(poolFile)) {
            return;
        }
        try {
            List<VirtualUserAccount> loaded =
                    objectMapper.readValue(poolFile.toFile(), ACCOUNT_LIST_TYPE);
            loaded.forEach(account -> accounts.put(account.number(), account));
        } catch (IOException exception) {
            throw new IllegalStateException("가상 사용자 계정 풀을 읽을 수 없습니다.", exception);
        }
    }

    private void writeSnapshot() {
        List<VirtualUserAccount> snapshot;
        synchronized (this) {
            snapshot = List.copyOf(accounts.values());
        }

        try {
            Files.createDirectories(poolFile.getParent());
            Path temporaryFile = poolFile.resolveSibling(poolFile.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(temporaryFile.toFile(), snapshot);
            moveTemporaryFile(temporaryFile);
        } catch (IOException exception) {
            throw new IllegalStateException("가상 사용자 계정 풀을 저장할 수 없습니다.", exception);
        }
    }

    private void moveTemporaryFile(Path temporaryFile) throws IOException {
        try {
            Files.move(
                    temporaryFile,
                    poolFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(
                    temporaryFile,
                    poolFile,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private VirtualUserAccount newAccount(int number) {
        return new VirtualUserAccount(
                number,
                "virtual-user-%d@%s".formatted(number, properties.accountEmailDomain()),
                properties.accountPassword(),
                false,
                null,
                null
        );
    }

    private VirtualUserAccount requireAccount(int number) {
        VirtualUserAccount account = accounts.get(number);
        if (account == null) {
            throw new IllegalArgumentException("가상 사용자 계정을 찾을 수 없습니다: " + number);
        }
        return account;
    }
}
