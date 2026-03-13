plugins {

    java
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
    // Supply Chain Defense: varre CVEs conhecidos em todas as dependências (NVD)
    id("org.owasp.dependencycheck") version "10.0.4"
}


group = "kerosene"
version = "PRE-ALPHA"
description = "backend"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
	mavenCentral()
}

dependencies {
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")

    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")

    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
    implementation("org.bitcoinj:bitcoinj-core:0.15.10")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-web")
	runtimeOnly("com.h2database:h2")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.jboss.aerogear:aerogear-otp-java:1.0.0.M1")
    implementation("commons-codec:commons-codec:1.16.0")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation(group = "io.lettuce", name = "lettuce-core", version = "6.8.1.RELEASE")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    implementation("com.bucket4j:bucket4j-core:8.7.0")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.yubico:webauthn-server-core:2.4.0")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.micrometer:micrometer-observation")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")

    // gRPC for MPC Sidecar
    implementation("io.grpc:grpc-netty-shaded:1.62.2")
    implementation("io.grpc:grpc-protobuf:1.62.2")
    implementation("io.grpc:grpc-stub:1.62.2")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    compileOnly("jakarta.annotation:jakarta.annotation-api:2.1.1")
    implementation("commons-net:commons-net:3.10.0")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// ─── OWASP Supply Chain Defense ─────────────────────────────────────────────
// Executa com: ./gradlew dependencyCheckAnalyze
// No CI/CD: adicionar ao pipeline antes do build de produção.
// Documenta falhas em build/reports/dependency-check-report.html
dependencyCheck {
    // Falha a build se qualquer dependência tiver CVSS >= 7.0 (HIGH/CRITICAL)
    failBuildOnCVSS = 7.0f

    // Formatos do relatório: HTML legível + JSON para CI
    formats = listOf("HTML", "JSON")

    analyzers {
        // Ativa análise de JARs (principal para projetos Java)
        jarEnabled = true
        nodeAuditEnabled = false   // Sem Node.js neste projeto
    }
}
