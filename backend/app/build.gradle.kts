plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":shared-kernel"))
    implementation(project(":modules:identity"))
    implementation(project(":modules:deposit"))
    implementation(project(":modules:loan"))
    implementation(project(":modules:notification"))
    implementation(project(":modules:reporting"))
    implementation(project(":modules:ledger"))
    implementation(project(":modules:currency"))
    implementation(project(":modules:budget"))
    implementation(project(":modules:bill"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jdbc")
    implementation(libs.springdoc.webmvc)
    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.jdbc)

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}
