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
    }
}

rootProject.name = "DiffViewer"
include(":app")
include(":core:domain")
include(":core:data")
include(":core:designsystem")
include(":core:diffui")
include(":feature:repository")
include(":feature:filediff")
include(":feature:alldiffs")
