package com.educate.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

data class Badge(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val unlocked: Boolean
)

fun badgesForStudent(completedCourses: Int, completedModules: Int): List<Badge> = listOf(
    Badge(
        icon = Icons.Filled.Star,
        title = "Primer paso",
        description = "Completaste tu primer módulo",
        unlocked = completedModules >= 1
    ),
    Badge(
        icon = Icons.Filled.Explore,
        title = "Explorador",
        description = "Completaste 5 módulos",
        unlocked = completedModules >= 5
    ),
    Badge(
        icon = Icons.Filled.EmojiEvents,
        title = "Dedicado",
        description = "Completaste un curso completo",
        unlocked = completedCourses >= 1
    ),
    Badge(
        icon = Icons.Filled.School,
        title = "Académico",
        description = "Completaste 3 cursos",
        unlocked = completedCourses >= 3
    )
)
