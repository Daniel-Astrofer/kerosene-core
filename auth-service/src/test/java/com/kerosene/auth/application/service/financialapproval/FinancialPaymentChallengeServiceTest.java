package com.kerosene.auth.application.service.financialapproval;

import com.kerosene.auth.application.service.cache.contracts.RedisServicer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import static com.kerosene.common.financial.approval.FinancialPaymentApprovalV1.*;
import static com.kerosene.auth.application.service.financialapproval.FinancialPaymentCredentialVerifierTest.context;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Cache transport is an atomic in-memory stub; this does not test a deployed Redis server. */
class FinancialPaymentChallengeServiceTest {
    private final RedisServicer redis = mock(RedisServicer.class);
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
    private final long now = Instant.now().getEpochSecond();
    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(now), ZoneOffset.UTC);
    private final FinancialPaymentChallengeService service = new FinancialPaymentChallengeService(redis, new com.fasterxml.jackson.databind.ObjectMapper(), "service", clock);
    FinancialPaymentChallengeServiceTest() {
        when(redis.incrementWithExpire(any(), eq(60L))).thenReturn(1L);
        doAnswer(i -> { cache.put(i.getArgument(0), i.getArgument(1)); return null; }).when(redis).setValue(any(), any(), eq(90L));
        when(redis.getAndDeleteValue(any())).thenAnswer(i -> cache.remove(i.getArgument(0)));
    }
    private Proof proof(Challenge challenge) { return new Proof(1, PROOF_TYPE, "cred", "install", signedPayload(challenge, "cred", "install", 1, now), "opaque-signature"); }

    @Test void independentRandomChallengesAreBoundAndConsumedExactlyOnce() {
        var c = service.issue(context(), " Alice "); var other = service.issue(context(), "Alice");
        assertThat(c.challengeId()).isNotEqualTo(other.challengeId()); assertThat(c.challenge()).isNotEqualTo(other.challenge());
        assertThat(c.bindingHash()).isEqualTo(bindingHash(context())); assertThat(c.username()).isEqualTo("alice");
        assertThat(service.consume(context(), proof(c), "ALICE")).isEqualTo(c);
        assertThatThrownBy(() -> service.consume(context(), proof(c), "alice")).isInstanceOf(RuntimeException.class);
        assertThat(cache).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings = {"expired", "username", "context", "service", "generic"})
    void expiredAndMismatchedContextCannotBeUsed(String fault) {
        var c = service.issue(context(), "alice"); var input = context(); var verifier = service; String username = "alice";
        if (fault.equals("expired")) { verifier = new FinancialPaymentChallengeService(redis, new com.fasterxml.jackson.databind.ObjectMapper(), "service", Clock.offset(clock, java.time.Duration.ofSeconds(90))); }
        if (fault.equals("username")) { username = "bob"; }
        if (fault.equals("service")) { verifier = new FinancialPaymentChallengeService(redis, new com.fasterxml.jackson.databind.ObjectMapper(), "other", clock); }
        if (fault.equals("context")) { var x = context(); input = new Context(8, x.deviceRef(), x.idempotencyKey(), x.rail(), x.direction(), x.sourceWalletId(), x.destinationWalletId(), x.amountSats(), x.networkFeeSats(), x.externalReference(), x.memo(), x.paymentRequestPublicId(), x.feeRateSatPerVbyte(), x.feeTargetBlocks(), x.quoteId()); }
        if (fault.equals("generic")) { cache.clear(); }
        var target = verifier; var ctx = input; var user = username;
        assertThatThrownBy(() -> target.consume(ctx, proof(c), user)).isInstanceOf(RuntimeException.class);
        assertThat(cache).isEmpty();
    }

    @Test void concurrentConsumeHasOnlyOneWinner() throws Exception {
        var c = service.issue(context(), "alice");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Boolean> consume = () -> { start.await(); try { service.consume(context(), proof(c), "alice"); return true; } catch (RuntimeException rejected) { return false; } };
            var one = pool.submit(consume); var two = pool.submit(consume); start.countDown();
            assertThat(java.util.List.of(one.get(), two.get())).containsExactlyInAnyOrder(true, false);
        }
    }

    @Test void rateAndCacheFailuresNeverProduceChallenge() {
        when(redis.incrementWithExpire(any(), eq(60L))).thenReturn(31L);
        assertThatThrownBy(() -> service.issue(context(), "alice")).isInstanceOf(RuntimeException.class);
        assertThat(cache).isEmpty();
        when(redis.incrementWithExpire(any(), eq(60L))).thenReturn(1L);
        doThrow(new IllegalStateException("cache private failure")).when(redis).setValue(any(), any(), anyLong());
        assertThatThrownBy(() -> service.issue(context(), "alice")).hasMessage("Payment challenge storage unavailable");
    }
}
