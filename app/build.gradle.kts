plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.avatarbot_mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.avatarbot_mobile"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Habilita suporte a vetores para ícones no Compose
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // === ATIVA O JETPACK COMPOSE ===
    buildFeatures {
        compose = true
    }

    composeOptions {
        // Versão do compilador do Compose (compatível com Kotlin 1.9.x)
        kotlinCompilerExtensionVersion = "1.5.1"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Bibliotecas Padrão (Mantidas do seu arquivo)
    implementation(libs.androidx.core.ktx)
    // implementation(libs.androidx.appcompat) // Pode remover ou manter, Compose não usa
    // implementation(libs.material) // Pode remover, Compose usa Material3
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // OkHttp (Sua dependência de rede)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // === DEPENDÊNCIAS DO COMPOSE (Adicionadas Manualmente) ===
    // BOM (Bill of Materials) para gerenciar versões
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Componentes principais do Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Integração com Activity (CRUCIAL para substituir AppCompatActivity por ComponentActivity)
    implementation("androidx.activity:activity-compose:1.8.2")

    // Ferramentas de Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}