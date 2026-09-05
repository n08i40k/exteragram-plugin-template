import com.android.tools.r8.CompilationMode
import com.android.tools.r8.Diagnostic
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.OutputMode
import com.android.tools.r8.R8
import com.android.tools.r8.R8Command
import com.android.tools.r8.origin.Origin
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private val ConfigurableFileCollection.paths: List<Path>
    get() =
        files.map(File::toPath)

abstract class BuildDexTask : DefaultTask() {
    /**
     * Plugin's fat-jar.
     */
    @get:Classpath
    abstract val programJars: ConfigurableFileCollection

    /**
     * Libraries that available on every device.
     */
    @get:Classpath
    abstract val bootClasspathJars: ConfigurableFileCollection

    /**
     * Libraries that available in target application (exteraGram).
     */
    @get:Classpath
    abstract val classpathJars: ConfigurableFileCollection

    /**
     * ProGuard rules.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val proguardFiles: ConfigurableFileCollection

    /**
     * Minimal SDK version.
     * Same as minSdk at compile time.
     */
    @get:Input
    abstract val minSdk: Property<Int>

    /**
     * Disable some optimizations in debug builds.
     */
    @get:Input
    abstract val release: Property<Boolean>

    /**
     * Output file that contains merged classpath to avoid collisions at r8 step.
     */
    @get:OutputFile
    abstract val mergedClasspathJar: RegularFileProperty

    /**
     * Path to output dex.
     */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        // Create parent dir.
        val output = outputDir.get().asFile
            .apply { mkdirs() }
            .toPath()

        val classpath = mergeClasspath()

        val command = R8Command.builder(GradleDiagnosticsHandler())
            .setMode(if (release.get()) CompilationMode.RELEASE else CompilationMode.DEBUG)
            .setMinApiLevel(minSdk.get())
            .setOutput(output, OutputMode.DexIndexed)
            .addProgramFiles(programJars.paths)
            .addClasspathFiles(classpath.toPath())
            .addLibraryFiles(bootClasspathJars.paths)
            .addProguardConfigurationFiles(proguardFiles.paths)
            // Ignore unresolved errors in telegram jar.
            .addProguardConfiguration(listOf("-ignorewarnings"), Origin.root())
            .build()

        R8.run(command)

        logger.lifecycle("dex written to ${output.resolve("classes.dex")}")
    }

    private fun mergeClasspath(): File {
        val merged = mergedClasspathJar.get().asFile
            .apply { parentFile.mkdirs() }

        val seen = HashSet<String>()

        ZipOutputStream(merged.outputStream().buffered()).use { out ->
            for (jar in classpathJars.files) {
                if (!jar.isFile) continue

                ZipFile(jar).use { zip ->
                    zip.entries()
                        .asSequence()
                        .filter { !it.isDirectory && it.name.endsWith(".class") }
                        .filter { seen.add(it.name) }
                        .forEach { entry ->
                            out.putNextEntry(ZipEntry(entry.name))
                            zip.getInputStream(entry).use { it.copyTo(out) }
                            out.closeEntry()
                        }
                }
            }
        }

        return merged
    }

    private inner class GradleDiagnosticsHandler : DiagnosticsHandler {
        override fun info(diagnostic: Diagnostic) = logger.info(diagnostic.diagnosticMessage)
        override fun warning(diagnostic: Diagnostic) = logger.warn(diagnostic.diagnosticMessage)
        override fun error(diagnostic: Diagnostic) = logger.error(diagnostic.diagnosticMessage)
    }
}
