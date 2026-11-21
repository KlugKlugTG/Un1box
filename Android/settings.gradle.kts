pluginManagement { repositories { google(); maven("https://maven.aliyun.com/repository/gradle-plugin"); maven("https://maven.aliyun.com/repository/central"); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); maven("https://maven.aliyun.com/repository/central"); mavenCentral() } }
rootProject.name = "Un1boxAndroid"
include(":app")
