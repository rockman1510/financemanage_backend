dependencies {
    implementation(project(":shared-kernel"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation(libs.shedlock.spring)
}

dependencies {
    // Listens to UserDeleted from the identity module (named interface "api" only)
    implementation(project(":modules:identity"))
}
