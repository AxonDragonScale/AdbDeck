package com.github.axondragonscale.adbdeck.adb

import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet

/**
 * Detects Android application IDs from the project's Gradle modules.
 */
@Service(Service.Level.PROJECT)
class PackageDetectionService(private val project: Project) {

    private val logger = thisLogger()

    companion object {
        private val SOURCE_SET_SUFFIXES = setOf(
            "main", "test", "unitTest", "androidTest",
            "testDebug", "testRelease",
            "debugAndroidTest", "releaseAndroidTest",
            "testFixtures",
        )
    }

    /**
     * Detects all application IDs from app-type Android modules in the project.
     */
    fun detectApplicationIds(): List<String> {
        return ModuleManager.getInstance(project).modules.mapNotNull { module ->
            try {
                if (module.isSourceSetSubModule()) return@mapNotNull null
                AndroidFacet.getInstance(module) ?: return@mapNotNull null
                val androidModel = GradleAndroidModel.get(module) ?: return@mapNotNull null
                val androidProject = androidModel.androidProject

                if (androidProject.projectType != IdeAndroidProjectType.PROJECT_TYPE_APP) {
                    return@mapNotNull null
                }

                androidModel.applicationId
                    ?: androidProject.namespace
            } catch (e: Exception) {
                logger.warn("Failed to detect applicationId for module ${module.name}", e)
                null
            }
        }
    }

    /**
     * Returns the primary (first detected) application ID, or null.
     */
    fun detectPrimaryApplicationId(): String? = detectApplicationIds().firstOrNull()

    private fun com.intellij.openapi.module.Module.isSourceSetSubModule(): Boolean {
        if (!name.contains('.')) return false
        return name.substringAfterLast('.') in SOURCE_SET_SUFFIXES
    }
}

