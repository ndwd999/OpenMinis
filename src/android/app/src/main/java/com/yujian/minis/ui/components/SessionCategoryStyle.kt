package com.yujian.minis.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.yujian.minis.ui.theme.IosAccents
import com.yujian.minis.ui.theme.SessionCategoryColors

/** Icon + tint for a session category chip. */
data class SessionCategoryStyle(val icon: ImageVector, val color: Color)

/**
 * The category → (icon, tint) table for a session's `category` field, covering
 * the 16 categories iOS uses (ContentView.swift:1897-1916). Unknown, null, and
 * uncategorised sessions all fall back to the neutral grey chat glyph.
 *
 * Extracted so the main session list and the "move to session" sheet cannot
 * drift apart: they previously each carried a byte-identical private copy of
 * this `when`, so adding or recolouring a category meant editing both.
 */
fun sessionCategoryStyle(category: String?): SessionCategoryStyle {
    return when (category?.lowercase()) {
        "code"         -> SessionCategoryStyle(Icons.Outlined.Code, SessionCategoryColors.Amber)
        "writing"      -> SessionCategoryStyle(Icons.Outlined.Description, SessionCategoryColors.Azure)
        "research"     -> SessionCategoryStyle(Icons.Outlined.Language, SessionCategoryColors.Teal)
        "analysis"     -> SessionCategoryStyle(Icons.Outlined.BarChart, SessionCategoryColors.Indigo)
        "creative"     -> SessionCategoryStyle(Icons.Outlined.Brush, IosAccents.Pink)
        "chat"         -> SessionCategoryStyle(Icons.Outlined.Forum, IosAccents.Green)
        "math"         -> SessionCategoryStyle(Icons.Outlined.Calculate, SessionCategoryColors.Violet)
        "translation"  -> SessionCategoryStyle(Icons.Outlined.Translate, SessionCategoryColors.Cyan)
        "health"       -> SessionCategoryStyle(Icons.Outlined.Favorite, IosAccents.Red)
        "finance"      -> SessionCategoryStyle(Icons.Outlined.Payments, SessionCategoryColors.Mint)
        "travel"       -> SessionCategoryStyle(Icons.Outlined.Map, SessionCategoryColors.Amber)
        "education"    -> SessionCategoryStyle(Icons.Outlined.Book, SessionCategoryColors.Azure)
        "design"       -> SessionCategoryStyle(Icons.Outlined.Palette, IosAccents.Pink)
        "productivity" -> SessionCategoryStyle(Icons.Outlined.CalendarMonth, IosAccents.Yellow)
        "support"      -> SessionCategoryStyle(Icons.Outlined.Settings, SessionCategoryColors.Bronze)
        "other"        -> SessionCategoryStyle(Icons.Outlined.GridView, IosAccents.Gray)
        else           -> SessionCategoryStyle(Icons.Outlined.Forum, IosAccents.Gray)
    }
}