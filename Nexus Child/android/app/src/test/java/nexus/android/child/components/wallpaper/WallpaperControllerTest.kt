package nexus.android.child.components.wallpaper

import org.junit.Assert.assertNotNull
import org.junit.Test

class WallpaperControllerTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.wallpaper.WallpaperController", Class.forName("nexus.android.child.components.wallpaper.WallpaperController", false, this::class.java.classLoader))
    }
}
