package nexus.android.child.components.storage

import org.junit.Assert.assertNotNull
import org.junit.Test

class FileSystemControllerTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.storage.FileSystemController", Class.forName("nexus.android.child.components.storage.FileSystemController", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.components.storage.FileSystemController\$StorageDescriptor", Class.forName("nexus.android.child.components.storage.FileSystemController\$StorageDescriptor", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.components.storage.FileSystemController\$FileMetadataPayload", Class.forName("nexus.android.child.components.storage.FileSystemController\$FileMetadataPayload", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.components.storage.FileSystemController\$FileTransferSession", Class.forName("nexus.android.child.components.storage.FileSystemController\$FileTransferSession", false, this::class.java.classLoader))
    }
}
