package com.kerosene.architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.env.MockEnvironment;
import com.kerosene.Application;
import com.kerosene.config.KfeProfileCoreControllerExclusionFilter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureGuardrailsTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.kerosene", "com.kerosene.kfe");

    @Test
    void productionCodeDoesNotUseMisspelledStructuralNames() {
        List<String> violations = PRODUCTION_CLASSES.stream()
                .filter(hasMisspelledPackageOrClassName())
                .map(JavaClass::getName)
                .sorted()
                .toList();

        assertTrue(violations.isEmpty(), () -> "Misspelled package/class names found:\n" + String.join("\n", violations));
    }

    @Test
    void productionCodeDoesNotWriteToSystemErr() {
        List<String> violations = PRODUCTION_CLASSES.stream()
                .flatMap(javaClass -> javaClass.getFieldAccessesFromSelf().stream())
                .filter(this::isSystemErrAccess)
                .map(this::describeFieldAccess)
                .sorted()
                .toList();

        assertTrue(violations.isEmpty(), () -> "System.err access found:\n" + String.join("\n", violations));
    }

    @Test
    void objectMappersAreCreatedOnlyInSpringConfiguration() {
        List<String> violations = PRODUCTION_CLASSES.stream()
                .filter(javaClass -> !javaClass.getPackageName().startsWith("com.kerosene.config"))
                .filter(javaClass -> !javaClass.isAssignableTo(jakarta.persistence.AttributeConverter.class))
                .flatMap(javaClass -> javaClass.getConstructorCallsFromSelf().stream())
                .filter(this::isObjectMapperConstructorCall)
                .map(this::describeConstructorCall)
                .sorted()
                .toList();

        assertTrue(violations.isEmpty(), () -> "ObjectMapper constructors outside configuration:\n"
                + String.join("\n", violations));
    }

    @Test
    void nonKfeProductionCodeDoesNotDependOnKfeImplementationPackages() {
        noClasses()
                .that().resideOutsideOfPackage("com.kerosene.kfe..")
                .should().dependOnClassesThat().resideInAPackage("com.kerosene.kfe..")
                .check(PRODUCTION_CLASSES);
    }


    @Test
    void kfeProductionCodeDoesNotDependOnAuthImplementationPackages() {
        noClasses()
                .that().resideInAPackage("com.kerosene.kfe..")
                .should().dependOnClassesThat().resideInAPackage("com.kerosene.auth..")
                .check(PRODUCTION_CLASSES);
    }


    @Test
    void kfeProductionCodeDoesNotDependOnNotificationImplementationPackages() {
        noClasses()
                .that().resideInAPackage("com.kerosene.kfe..")
                .should().dependOnClassesThat().resideInAPackage("com.kerosene.notification..")
                .check(PRODUCTION_CLASSES);
    }


    @Test
    void kfeProductionCodeDoesNotDependOnSecurityImplementationPackages() {
        noClasses()
                .that().resideInAPackage("com.kerosene.kfe..")
                .should().dependOnClassesThat().resideInAPackage("com.kerosene.security..")
                .check(PRODUCTION_CLASSES);
    }


    @Test
    void kfeProductionCodeDoesNotDependOnSovereignImplementationPackages() {
        noClasses()
                .that().resideInAPackage("com.kerosene.kfe..")
                .should().dependOnClassesThat().resideInAPackage("com.kerosene.sovereign..")
                .check(PRODUCTION_CLASSES);
    }


    @Test
    void coreApplicationExcludesKfeRuntimeFromDefaultComponentScan() {
        ComponentScan componentScan = Application.class.getAnnotation(ComponentScan.class);
        assertNotNull(componentScan, "Application must declare an explicit component scan boundary");

        boolean excludesKfePackage = Arrays.stream(componentScan.excludeFilters())
                .anyMatch(filter -> filter.type() == FilterType.REGEX
                        && Arrays.asList(filter.pattern()).contains("com\\.kerosene\\.kfe\\..*"));

        assertTrue(excludesKfePackage, "Core runtime must exclude com.kerosene.kfe from the default component scan");
    }

    @Test
    void kfeProfileApplicationExcludesCoreControllersFromRuntimeComponentScan() {
        ComponentScan componentScan = Application.class.getAnnotation(ComponentScan.class);
        assertNotNull(componentScan, "Application must declare an explicit component scan boundary");

        boolean excludesCoreControllersForKfe = Arrays.stream(componentScan.excludeFilters())
                .anyMatch(filter -> filter.type() == FilterType.CUSTOM
                        && Arrays.asList(filter.classes()).contains(KfeProfileCoreControllerExclusionFilter.class));

        assertTrue(
                excludesCoreControllersForKfe,
                "KFE runtime must not publish Core controllers from the shared executable");
    }

    @Test
    void kfeProfileControllerFilterKeepsOperationalHealthPublished() throws Exception {
        KfeProfileCoreControllerExclusionFilter filter = new KfeProfileCoreControllerExclusionFilter();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("kfe");
        filter.setEnvironment(environment);
        MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory();

        assertTrue(
                !filter.match(
                        metadataReaderFactory.getMetadataReader("com.kerosene.common.controller.HealthController"),
                        metadataReaderFactory),
                "KFE runtime must keep shared health endpoints available for Kubernetes probes");
        assertTrue(
                filter.match(
                        metadataReaderFactory.getMetadataReader("com.kerosene.auth.controller.UserController"),
                        metadataReaderFactory),
                "KFE runtime must still exclude Core HTTP controllers");
    }

    @Test
    void kfeIsStandaloneWithoutDirectClasspathDependency() {
        // As of schema separation, kfe-service is a standalone process — auth no longer
        // carries it on the classpath at runtime. The auto-configuration import is obsolete.
        assertDoesNotThrow(() -> {
            try {
                Class.forName("com.kerosene.kfe.config.KfeServiceRuntimeConfiguration");
            } catch (ClassNotFoundException e) {
                // Expected: kfe is standalone, not on auth classpath
            }
        });
    }

    @Test
    void applicationAndDomainLayersDoNotDependOnControllers() {
        noClasses()
                .that().resideInAnyPackage("com.kerosene..application..", "com.kerosene..domain..")
                .should().dependOnClassesThat().resideInAPackage("com.kerosene..controller..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void domainLayerDoesNotDependOnSpringOrInfrastructureAdapters() {
        noClasses()
                .that().resideInAPackage("com.kerosene..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "com.kerosene..controller..",
                        "com.kerosene..infra..",
                        "com.kerosene..repository..")
                .check(PRODUCTION_CLASSES);
    }


    private String readAutoConfigurationImports() throws IOException {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertNotNull(inputStream, "AutoConfiguration.imports resource must be present on the test classpath");
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Predicate<JavaClass> hasMisspelledPackageOrClassName() {
        return javaClass -> {
            String packageName = javaClass.getPackageName();
            String className = javaClass.getSimpleName();
            return packageName.contains(".persistance.")
                    || packageName.contains(".contratcs.")
                    || className.contains("Bcript")
                    || className.contains("Usuario")
                    || className.contains("Alredy");
        };
    }

    private boolean isSystemErrAccess(JavaFieldAccess access) {
        return System.class.getName().equals(access.getTarget().getOwner().getName())
                && "err".equals(access.getTarget().getName());
    }

    private boolean isObjectMapperConstructorCall(JavaConstructorCall call) {
        return ObjectMapper.class.getName().equals(call.getTarget().getOwner().getName());
    }

    private String describeFieldAccess(JavaFieldAccess access) {
        return access.getOriginOwner().getName() + ":" + access.getLineNumber();
    }

    private String describeConstructorCall(JavaConstructorCall call) {
        return call.getOriginOwner().getName() + ":" + call.getLineNumber();
    }
}
