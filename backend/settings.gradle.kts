rootProject.name = "financemanage-backend"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        // Google mirror of Maven Central first (avoids Central rate limits on CI), Central as fallback
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}

include(
    "shared-kernel",
    "modules:identity",
    "modules:deposit",
    "modules:loan",
    "modules:notification",
    "modules:reporting",
    "modules:ledger",
    "modules:currency",
    "app",
)
