dependencies {
    implementation(project(":shared-kernel"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation(libs.shedlock.spring)
}

dependencies {
    // Listens to events published by the domain modules (named interface "api" only)
    implementation(project(":modules:deposit"))
    implementation(project(":modules:loan"))
    implementation(project(":modules:identity"))
    implementation(project(":modules:ledger"))
    implementation(project(":modules:budget"))
    implementation(project(":modules:bill"))
    implementation(project(":modules:saving"))
}
