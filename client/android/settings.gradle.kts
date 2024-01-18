pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
        }
    }
}

rootProject.name = "Pushca-client-test"
include(":app")

