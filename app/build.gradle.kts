plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "id.aksisoft.aksifile"
    compileSdk = 36
    defaultConfig { applicationId = "id.aksisoft.aksifile"; minSdk = 26; targetSdk = 36; versionCode = 4; versionName = "1.2.1" }
    val keystoreProps = rootProject.file("keystore.properties")
    val keystoreCfg = if (keystoreProps.exists()) {
        val map = keystoreProps.readLines().filter { "=" in it }.associate { l -> l.substringBefore("=").trim() to l.substringAfter("=").trim() }
        signingConfigs.create("release") {
            storeFile = file(map["storeFile"] ?: "tukangdigital.keystore")
            storePassword = map["storePassword"] ?: ""
            keyAlias = map["keyAlias"] ?: ""
            keyPassword = map["keyPassword"] ?: ""
        }
    } else null
    buildTypes {
        release {
            isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreCfg != null) signingConfig = keystoreCfg
        }
    }
    buildFeatures { compose = true }
    testOptions {
        unitTests { isReturnDefaultValues = true; isIncludeAndroidResources = true; all { it.maxHeapSize = "2g" } }
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.google.android.gms:play-services-ads:23.2.0")
    // QR scan (camera + from image)
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
    implementation("com.google.guava:guava:31.1-android")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    // FTP / SFTP
    implementation("commons-net:commons-net:3.11.1")
    implementation("com.github.mwiede:jsch:0.2.18")
    // Archives: zip / 7z / tar.gz (commons-compress), rar extract (junrar)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("org.tukaani:xz:1.9")
    implementation("com.github.junrar:junrar:7.5.5")
    // PDF: real merge / split / add text / extract text
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
