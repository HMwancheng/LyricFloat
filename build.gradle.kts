plugins {
    id("com.android.application") apply false
    id("org.jetbrains.kotlin.android") apply false
}

// 替换废弃的buildDir引用，使用layout.buildDirectory
tasks.register("clean", Delete::class) {
    delete(layout.buildDirectory)
}
