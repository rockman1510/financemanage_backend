dependencies {
    implementation(project(":shared-kernel"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
}

dependencies {
    // Uses other modules through their named interface "api" only
    implementation(project(":modules:identity"))
    implementation(project(":modules:ledger"))
    implementation(project(":modules:currency"))
}
