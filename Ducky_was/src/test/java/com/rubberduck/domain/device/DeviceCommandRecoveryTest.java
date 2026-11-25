package com.rubberduck.domain.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.rubberduck.domain.device.entity.DeviceCommand;
import com.rubberduck.domain.device.entity.DeviceCommandStatus;
import com.rubberduck.domain.device.entity.DeviceUser;
import com.rubberduck.domain.device.repository.DeviceCommandRepository;
import com.rubberduck.domain.device.service.DeviceCommandService;
import com.rubberduck.global.exception.CustomException;
import com.rubberduck.support.ApiFlowTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DeviceCommandRecoveryTest extends ApiFlowTest {
    @Autowired private DeviceCommandService service;
    @Autowired private DeviceCommandRepository commands;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void abandonedPendingCommandFailsAndAllowsAnExplicitRetry() {
        var user = newUser("pending");
        var pi = provision("pending-" + user.getId(), user);
        var link = DeviceUser.link(pi.device(), user, "OWNER");
        var command = service.startRecording(user, pi.device().getId(), null);
        ageCreatedAt(command, 121);

        var expired = service.latestForUser(user, pi.device().getId()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(DeviceCommandStatus.FAILED);
        assertThat(expired.getErrorMessage()).contains("초과");
        assertThat(expired.getCompletedAt()).isNotNull();
        assertThat(service.claimNext(link)).isEmpty();
        var retry = service.startRecording(user, pi.device().getId(), null);
        assertThat(retry.getId()).isNotEqualTo(command.getId());
        assertThat(service.claimNext(link).orElseThrow().getId()).isEqualTo(retry.getId());
    }

    @Test
    void abandonedClaimReleasesStaleRecordingStateAndLateSuccessCannotCompleteRetry() {
        var user = newUser("claimed");
        var pi = provision("claimed-" + user.getId(), user);
        var link = DeviceUser.link(pi.device(), user, "OWNER");
        var command = service.startRecording(user, pi.device().getId(), null);
        service.claimNext(link);
        jdbc.update("update device_commands set claimed_at=? where device_command_id=?",
                Timestamp.from(Instant.now().minusSeconds(301)), command.getId());
        var device = devices.findById(pi.device().getId()).orElseThrow();
        device.setStatus("RECORDING");
        devices.save(device);

        assertThat(service.latestForUser(user, device.getId()).orElseThrow().getStatus())
                .isEqualTo(DeviceCommandStatus.FAILED);
        assertThat(devices.findById(device.getId()).orElseThrow().getStatus()).isEqualTo("ERROR");
        var retry = service.startRecording(user, device.getId(), null);
        assertThatThrownBy(() -> service.complete(command.getId(), link, true, null))
                .isInstanceOf(CustomException.class);
        assertThat(commands.findById(retry.getId()).orElseThrow().getStatus()).isEqualTo(DeviceCommandStatus.PENDING);
    }

    @Test
    void claimDeadlineStartsAtClaimRatherThanCreation() {
        var user = newUser("deadline");
        var pi = provision("deadline-" + user.getId(), user);
        var link = DeviceUser.link(pi.device(), user, "OWNER");
        var command = service.startRecording(user, pi.device().getId(), null);
        service.claimNext(link);
        ageCreatedAt(command, 900);
        assertThat(service.latestForUser(user, pi.device().getId()).orElseThrow().getStatus())
                .isEqualTo(DeviceCommandStatus.CLAIMED);
        assertThat(service.startRecording(user, pi.device().getId(), null).getId()).isEqualTo(command.getId());
    }

    @Test
    void expiryOfAnOlderClaimCannotResetTheStateOfANewerCommand() {
        var user = newUser("legacy");
        var pi = provision("legacy-" + user.getId(), user);
        var old = service.startRecording(user, pi.device().getId(), null);
        old.claim();
        old.setClaimedAt(Instant.now().minusSeconds(301));
        commands.save(old);
        ageCreatedAt(old, 400);
        var newer = commands.save(DeviceCommand.startRecording(pi.device(), user, null));
        pi.device().setStatus("RECORDING");
        devices.save(pi.device());

        assertThat(service.latestForUser(user, pi.device().getId()).orElseThrow().getId()).isEqualTo(newer.getId());
        assertThat(commands.findById(old.getId()).orElseThrow().getStatus()).isEqualTo(DeviceCommandStatus.FAILED);
        assertThat(devices.findById(pi.device().getId()).orElseThrow().getStatus()).isEqualTo("RECORDING");
    }

    @Test
    void concurrentStartsAndClaimsExecuteOnceAndCompletionRemainsIdempotent() throws Exception {
        var user = newUser("concurrent");
        var pi = provision("concurrent-" + user.getId(), user);
        var link = DeviceUser.link(pi.device(), user, "OWNER");
        var ids = concurrently(() -> service.startRecording(user, pi.device().getId(), null).getId());
        assertThat(ids.get(0)).isEqualTo(ids.get(1));
        var claims = concurrently(() -> service.claimNext(link).isPresent());
        assertThat(claims).containsExactlyInAnyOrder(true, false);
        service.complete(ids.get(0), link, true, null);
        var completedAt = commands.findById(ids.get(0)).orElseThrow().getCompletedAt();
        service.complete(ids.get(0), link, true, null);
        assertThat(commands.findById(ids.get(0)).orElseThrow().getCompletedAt()).isEqualTo(completedAt);
        assertThatThrownBy(() -> service.complete(ids.get(0), link, false, "conflicting retry"))
                .isInstanceOf(CustomException.class);
    }

    private void ageCreatedAt(DeviceCommand command, long seconds) {
        jdbc.update("update device_commands set created_at=? where device_command_id=?",
                Timestamp.from(Instant.now().minusSeconds(seconds)), command.getId());
    }

    private <T> java.util.List<T> concurrently(Callable<T> action) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<T> scheduled = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
            return action.call();
        };
        try {
            var first = executor.submit(scheduled);
            var second = executor.submit(scheduled);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }
}
