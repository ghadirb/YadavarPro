package com.ghadirb.yadavar.database

data class CategoryDef(
    val name: String,
    val color: String,
    val emoji: String
)

object CategoryCatalog {
    val defaults = listOf(
        CategoryDef("کار", "#6D5EF5", "کار"),
        CategoryDef("شخصی", "#14B8A6", "شخصی"),
        CategoryDef("سلامت", "#22C55E", "سلامت"),
        CategoryDef("دارو", "#EF4444", "دارو"),
        CategoryDef("خرید", "#F59E0B", "خرید"),
        CategoryDef("قبض", "#0EA5E9", "قبض"),
        CategoryDef("مهمانی", "#A78BFA", "مهمانی"),
        CategoryDef("عمومی", "#64748B", "عمومی")
    )

    fun colorFor(name: String): String =
        defaults.firstOrNull { it.name == name }?.color ?: "#6D5EF5"

    fun allNames(): List<String> = defaults.map { it.name }
}
