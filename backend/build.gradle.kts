plugins {
    java
    alias(libs.plugins.spring.boot) apply false
}

allprojects {
    group = "com.financemanage"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")

    dependencies {
        // Versions are managed centrally by the Spring Boot & Modulith BOMs
        "implementation"(platform(libs.findLibrary("spring-boot-bom").get()))
        "implementation"(platform(libs.findLibrary("spring-modulith-bom").get()))
        "testImplementation"(platform(libs.findLibrary("spring-boot-bom").get()))
        "testImplementation"(platform(libs.findLibrary("spring-modulith-bom").get()))
        "annotationProcessor"(platform(libs.findLibrary("spring-boot-bom").get()))

        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
