plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.agents.pokeclaw"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("proguard-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "VERSION_NAME", "\"0.7.1-heavy\"")
        buildConfigField("int", "VERSION_CODE", "29")
        buildConfigField("String", "APPLICATION_ID", "\"com.aistudio.dailytodo.whkspr\"")
        buildConfigField("String", "VERSION_INFO", "\"embedded-heavy\"")
        buildConfigField("String", "APP_ORIGIN", "\"PokeClaw embedded in ToDoList\"")
        buildConfigField("String", "BUILD_FINGERPRINT", "\"heavy\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/LICENSE.txt",
            "META-INF/NOTICE",
            "META-INF/NOTICE.txt"
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("com.google.code.gson:gson:2.13.2")
    implementation("dev.langchain4j:langchain4j-core:1.12.2")
    implementation("dev.langchain4j:langchain4j-open-ai:1.12.2") {
        exclude(group = "dev.langchain4j", module = "langchain4j-http-client-jdk")
    }
    implementation("dev.langchain4j:langchain4j-anthropic:1.12.2") {
        exclude(group = "dev.langchain4j", module = "langchain4j-http-client-jdk")
    }
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    implementation("com.blankj:utilcodex:1.31.1")
    implementation("com.github.mrmike:ok2curl:0.8.0")
    implementation("com.tencent:mmkv-static:2.3.0")
    implementation("com.drakeet.multitype:multitype:4.3.0")
    implementation("com.github.bumptech.glide:glide:5.0.5")
    implementation("jp.wasabeef:glide-transformations:4.3.0")
    implementation("com.github.princekin-f:EasyFloat:2.0.4")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Retained for the channel subsystem requested by the heavy build.
    implementation("com.larksuite.oapi:oapi-sdk:2.5.3")
    implementation("com.dingtalk.open:app-stream-client:1.3.12")

    testImplementation("junit:junit:4.13.2")
}
