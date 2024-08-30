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

rootProject.name = "VRC-OSC-Android"
include(":app")
include(":bhapticsVRCOSCQuery")
include(":osc_datasender")
include(":osc_datareceiver")
