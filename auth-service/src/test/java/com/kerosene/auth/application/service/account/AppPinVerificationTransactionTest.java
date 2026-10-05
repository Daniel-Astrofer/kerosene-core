package com.kerosene.auth.application.service.account;

import com.kerosene.auth.AuthExceptions;
import com.kerosene.auth.application.infra.persistence.jpa.UserAppPinSettingsRepository;
import com.kerosene.auth.application.service.crypto.contracts.Hasher;
import com.kerosene.auth.application.service.validation.totp.contracts.TOTPVerifier;
import com.kerosene.auth.model.entity.UserAppPinSettings;
import com.kerosene.auth.model.entity.UserDataBase;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real disposable H2 commits/rollbacks and row locks; repository is a SQL test adapter, not Hibernate mapping. */
class AppPinVerificationTransactionTest {
    @Test void rejectedPinAndLockoutPersistDespiteExceptionsAndCallerRollback() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:pin_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        var tx = new DataSourceTransactionManager(source); var sql = new JdbcTemplate(source);
        sql.execute("create table pin (id integer primary key, failures integer, locked timestamp, verified timestamp)");
        sql.update("insert into pin values (1, 0, null, null)");
        var user = new UserDataBase(); ReflectionTestUtils.setField(user, "id", 7L);
        var repo = mock(UserAppPinSettingsRepository.class); var hasher = mock(Hasher.class);
        when(hasher.verify(any(char[].class), eq("test-hash"))).thenAnswer(i -> "1234".equals(new String(i.getArgument(0, char[].class))));
        when(repo.findByUserIdAndDeviceHash(7L, "device")).thenAnswer(i -> Optional.of(sql.queryForObject("select * from pin where id=1 for update", (rs, row) -> {
            var state = new UserAppPinSettings(); state.setUser(user); state.setDeviceHash("device"); state.setEnabled(true); state.setPinHash("test-hash");
            state.setFailedAttempts(rs.getInt("failures")); var locked = rs.getTimestamp("locked"); state.setLockedUntil(locked == null ? null : locked.toLocalDateTime()); return state;
        })));
        when(repo.save(any())).thenAnswer(i -> { UserAppPinSettings state = i.getArgument(0); sql.update("update pin set failures=?, locked=?, verified=? where id=1", state.getFailedAttempts(), state.getLockedUntil(), state.getLastVerifiedAt()); return state; });
        var target = new AppPinService(repo, hasher, mock(TOTPVerifier.class), 4, 8, 2, 5);
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(tx, new AnnotationTransactionAttributeSource()));
        var service = (AppPinService) proxy.getProxy();
        new TransactionTemplate(tx).executeWithoutResult(status -> {
            assertThatThrownBy(() -> service.verify(user, "device", "9999")).isInstanceOf(AuthExceptions.StructuredAuthException.class);
            status.setRollbackOnly();
        });
        assertThat(sql.queryForObject("select failures from pin where id=1", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> service.verify(user, "device", "9999")).isInstanceOf(AuthExceptions.StructuredAuthException.class);
        assertThat(sql.queryForObject("select failures from pin where id=1", Integer.class)).isEqualTo(2);
        assertThat(sql.queryForObject("select locked from pin where id=1", java.sql.Timestamp.class)).isNotNull();
        assertThatThrownBy(() -> service.verify(user, "device", "1234")).isInstanceOf(AuthExceptions.StructuredAuthException.class);
        sql.update("update pin set locked=null, failures=1 where id=1");
        service.verify(user, "device", "1234");
        assertThat(sql.queryForObject("select failures from pin where id=1", Integer.class)).isZero();
        assertThat(sql.queryForObject("select verified from pin where id=1", java.sql.Timestamp.class)).isNotNull();
        assertThat(UserAppPinSettingsRepository.class.getMethod("findByUserIdAndDeviceHash", Long.class, String.class)
                .getAnnotation(org.springframework.data.jpa.repository.Lock.class).value()).isEqualTo(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        sql.execute("shutdown");
    }
}
