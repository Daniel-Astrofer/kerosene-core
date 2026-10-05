package com.kerosene.auth.application.orchestrator.signup.infra;

import com.kerosene.auth.application.orchestrator.signup.port.UserNotifier;
import com.kerosene.notification.model.NotificationKind;
import com.kerosene.notification.model.NotificationSeverity;
import com.kerosene.notification.model.UserNotificationPayload;
import com.kerosene.notification.model.entity.NotificationEntity;
import com.kerosene.notification.repository.NotificationRepository;
import com.kerosene.notification.service.NotificationPersistedEvent;
import com.kerosene.notification.service.NotificationPersistenceService;
import com.kerosene.notification.service.NotificationService;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real Hibernate/IDENTITY persistence on disposable H2, never the application database. */
class NotificationUserNotifierTransactionTest {
    @Test
    void bypassingNewTransactionReproducesOriginalPostCommitNullIdFailure() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            var oldBehavior = new NotificationUserNotifier(context.getBean(NotificationService.class));
            assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void afterCommit() { oldBehavior.notify(42L, payload()); }
                    }))).isInstanceOf(NullPointerException.class);
            assertThat(context.getBean(Events.class).committed).isEmpty();
        }
    }

    @Test
    void signupAfterCommitCreatesNotificationInNewTransactionAndDispatchesAfterItsCommit() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            var notifier = context.getBean(UserNotifier.class);
            var events = context.getBean(Events.class);
            tx.executeWithoutResult(status -> TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override public void afterCommit() {
                            assertThat(events.committed).isEmpty();
                            notifier.notify(42L, payload());
                            assertThat(events.committed).hasSize(1);
                        }
                    }));
            List<NotificationEntity> rows = tx.execute(status ->
                    context.getBean(NotificationRepository.class).findByUserIdOrderByCreatedAtDesc(42L));
            assertThat(rows).hasSize(1);
            assertThat(rows.getFirst().getId()).isNotNull();
            assertThat(events.committed.getFirst().payload().get("id")).isEqualTo(rows.getFirst().getId());
        }
    }

    @Test
    void ordinaryNotificationStillRollsBackWithItsCallerWithoutDispatch() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            tx.executeWithoutResult(status -> {
                context.getBean(NotificationService.class).notifyUser(42L, payload());
                assertThat(context.getBean(Events.class).committed).isEmpty();
                status.setRollbackOnly();
            });
            Long count = tx.execute(status -> context.getBean(NotificationRepository.class).count());
            assertThat(count).isZero();
            assertThat(context.getBean(Events.class).committed).isEmpty();
        }
    }

    private static UserNotificationPayload payload() {
        return UserNotificationPayload.create(NotificationKind.ACCOUNT_CREATED, NotificationSeverity.SUCCESS,
                "Account created", "Ready", "/home", "user", "42", Map.of());
    }

    static class Events {
        final List<NotificationPersistedEvent> committed = new ArrayList<>();
        @TransactionalEventListener
        public void onCommit(NotificationPersistedEvent event) { committed.add(event); }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Config {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory() {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(new DriverManagerDataSource(
                    "jdbc:h2:mem:notification_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setPackagesToScan(NotificationEntity.class.getPackageName());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean NotificationRepository repository(EntityManagerFactory factory) {
            return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory))
                    .getRepository(NotificationRepository.class);
        }
        @Bean NotificationPersistenceService persistence(NotificationRepository repository, ApplicationEventPublisher events) {
            return new NotificationPersistenceService(repository, events);
        }
        @Bean NotificationService notifications(NotificationPersistenceService persistence, NotificationRepository repository) {
            return new NotificationService(persistence, repository);
        }
        @Bean NotificationUserNotifier notifier(NotificationService service) { return new NotificationUserNotifier(service); }
        @Bean Events events() { return new Events(); }
    }
}
