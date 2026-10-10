dependencies {
    implementation(project(":shared-kernel"))
    implementation(project(":modules:deposit"))
    implementation(project(":modules:loan"))
    implementation("org.springframework.boot:spring-boot-starter-cache")
}
