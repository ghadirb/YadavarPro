// Same CI-vs-local repository split as Maliar-Pro:
// GitHub Actions can reach google()/mavenCentral(); local machines in Iran often cannot,
// so regional mirrors go first only outside CI.
pluginManagement {
    repositories {
        if (System.getenv("GITHUB_ACTIONS") == "true" || System.getenv("CI") == "true") {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://en-mirror.ir") }
        maven { url = uri("https://maven.myket.ir") }
        if (System.getenv("GITHUB_ACTIONS") != "true" && System.getenv("CI") != "true") {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("GITHUB_ACTIONS") == "true" || System.getenv("CI") == "true") {
            google()
            mavenCentral()
        }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://en-mirror.ir") }
        maven { url = uri("https://maven.myket.ir") }
        maven { url = uri("https://jitpack.io") }
        if (System.getenv("GITHUB_ACTIONS") != "true" && System.getenv("CI") != "true") {
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "YadavarPro"
include(":app")
