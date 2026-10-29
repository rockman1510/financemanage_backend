dependencies {
    implementation(project(":shared-kernel"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation(libs.shedlock.spring)
}

dependencies {
    // Uses other modules through their named interface "api" only
    implementation(project(":modules:identity"))
    implementation(project(":modules:currency"))
}
