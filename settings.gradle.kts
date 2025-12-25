pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://mirrors.cloud.tencent.com/android/maven/google") }
        maven { url = uri("https://mirrors.cloud.tencent.com/android/maven/public") }
        maven { url = uri("https://mirrors.cloud.tencent.com/gradle-plugin") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://mirrors.cloud.tencent.com/android/maven/google") }
        maven { url = uri("https://mirrors.cloud.tencent.com/android/maven/public") }
        maven { url = uri("https://mirrors.cloud.tencent.com/gradle-plugin") }
    }
}

rootProject.name = "Open-AutoGLM-Android"
include(":app")
 