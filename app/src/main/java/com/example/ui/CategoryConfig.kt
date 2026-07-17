package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryInfo(
    val name: String,
    val icon: ImageVector,
    val color: Color
)

object CategoryConfig {
    val categories = listOf(
        CategoryInfo("全部", Icons.AutoMirrored.Filled.List, Color(0xFF6750A4)),
        CategoryInfo("重要", Icons.Default.Star, Color(0xFFFFB74D)),
        CategoryInfo("工作", Icons.Default.Work, Color(0xFF1E88E5)),
        CategoryInfo("学习", Icons.Default.School, Color(0xFF8E24AA)),
        CategoryInfo("生活", Icons.Default.Home, Color(0xFFF4511E)),
        CategoryInfo("健康", Icons.Default.Favorite, Color(0xFF43A047)),
        CategoryInfo("购物", Icons.Default.ShoppingCart, Color(0xFFE91E63)),
        CategoryInfo("个人", Icons.Default.Person, Color(0xFF00ACC1))
    )

    fun getByName(name: String): CategoryInfo {
        return categories.find { it.name == name } ?: CategoryInfo(name, Icons.AutoMirrored.Filled.List, Color(0xFF757575))
    }
}
