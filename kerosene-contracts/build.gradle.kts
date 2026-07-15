plugins {
    `java-library`
}

group = "kerosene"
version = "PRE-ALPHA"
description = "Kerosene shared contracts for KFE/Core boundaries"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}
