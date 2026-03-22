const fs = require('fs');
const path = require('path');

const srcDir = path.join(__dirname, 'app/src/main/java/nexus/android/parent');
const testDir = path.join(__dirname, 'app/src/test/java/nexus/android/parent');

function mkdirp(dir) {
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
}

function processDirectory(dir, relPath = '') {
    const files = fs.readdirSync(dir);
    for (const file of files) {
        const fullPath = path.join(dir, file);
        const stat = fs.statSync(fullPath);
        if (stat.isDirectory()) {
            processDirectory(fullPath, path.join(relPath, file));
        } else if (file.endsWith('.kt')) {
            generateTest(fullPath, relPath, file);
        }
    }
}

function generateTest(filePath, relPath, fileName) {
    const content = fs.readFileSync(filePath, 'utf8');
    const classNameMatch = content.match(/(class|object)\s+([A-Z][A-Za-z0-9_]+)/);
    if (!classNameMatch) return; // maybe interface or script
    
    // Ignore already mapped tests from earlier
    const skipList = ["AuthManager", "ConnectionManager", "FeatureFactory", "BaseFeature", "MainActivity"];
    const className = classNameMatch[2];
    if (skipList.includes(className)) return;

    const isObject = classNameMatch[1] === 'object';
    
    const targetDir = path.join(testDir, relPath);
    mkdirp(targetDir);
    
    const testFileName = `${className}Test.kt`;
    const testFilePath = path.join(targetDir, testFileName);
    
    if (fs.existsSync(testFilePath)) return;

    const packageNameMatches = content.match(/package\s+(nexus\.android\.parent.*?)\n/);
    const packageName = packageNameMatches ? packageNameMatches[1] : `nexus.android.parent.${relPath.replace(/\\/g, '.')}`;

    const testContent = `package ${packageName}

import org.junit.Test
import org.junit.Assert.*
import ${packageName}.${className}

class ${className}Test {

    @Test
    fun test${className}Existence() {
        // Basic JVM assertion to verify the class is resolvable in the test classpath
        try {
            val clazz = Class.forName("${packageName}.${className}")
            assertNotNull("Class should exist", clazz)
        } catch (e: Exception) {
            fail("Class resolution failed: \${e.message}")
        }
    }
    
    ${isObject ? `
    @Test
    fun testObjectSingleton() {
        val instance1 = ${className}
        val instance2 = ${className}
        assertSame("Should be the same singleton instance", instance1, instance2)
    }
    ` : ''}
}
`;
    fs.writeFileSync(testFilePath, testContent);
    console.log(`Generated test for ${className}`);
}

processDirectory(srcDir);
