dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}

// Use an integer for version numbers
version = 1

cloudstream {
    description = "Animexin multi-quality plugin"
    authors = listOf("Treckified")

    status = 1 // 0: Down, 1: Ok, 2: Slow, 3: Beta-only
    tvTypes = listOf("Anime", "AnimeMovie")

    requiresResources = true
    language = "en"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}
