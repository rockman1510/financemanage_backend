dependencies {
    implementation(project(":shared-kernel"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-cache")
}

dependencies {
    // Uses other modules through their named interface "api" only
    implementation(project(":modules:identity"))
    implementation(project(":modules:deposit"))
    implementation(project(":modules:loan"))
    implementation(project(":modules:ledger"))
    implementation(project(":modules:currency"))
    implementation(project(":modules:saving"))
    implementation(project(":modules:investment"))
}
