plugins {
    `java-library`
    id("io.spring.dependency-management") version "1.1.6"
}

group = "kerosene"
version = "PRE-ALPHA"
description = "Kerosene shared runtime utilities used by Core and KFE"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.15")
    }
}

configurations.configureEach {
    exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
}

dependencies {
    api(project(":kerosene-contracts"))
    api("org.springframework:spring-context")
    api("org.springframework:spring-web")
    api("jakarta.persistence:jakarta.persistence-api")
    api("org.slf4j:slf4j-api")
    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    implementation("org.bitcoinj:bitcoinj-core:0.15.10") {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
    }
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")
}
