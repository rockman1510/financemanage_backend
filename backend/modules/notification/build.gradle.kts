dependencies {
    implementation(project(":shared-kernel"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
}

dependencies {
    // Listens to events published by the domain modules (named interface "api" only)
    implementation(project(":modules:deposit"))
    implementation(project(":modules:loan"))
    implementation(project(":modules:identity"))
}
