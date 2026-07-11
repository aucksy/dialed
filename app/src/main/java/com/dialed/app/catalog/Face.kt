package com.dialed.app.catalog

import androidx.annotation.DrawableRes

/**
 * One bundled watch face in the Dialed collection.
 *
 * [previewRes] exists at build time (copied from the fablecollection submodule by
 * tools/gen-facepacks.mjs). [apkAsset] / [tokenAsset] are produced by the Phase-0 CI
 * pipeline and read at install time — shipping tokens as asset files (not string
 * resources) keeps the app buildable before any token exists.
 */
data class Face(
    val id: String,                 // "kinetik_orrery"
    val series: String,             // "Kinetik"
    val faceName: String,           // "Orrery"
    val displayName: String,        // shown large on detail
    val tag: String,                // subtitle, e.g. "Kinetik · Mechanical"
    val description: String,
    @param:DrawableRes val previewRes: Int,
    val packageName: String,        // com.dialed.app.watchfacepush.kinetik.orrery
    val apkAsset: String,           // assets path: "faces/kinetik_orrery.apk"
    val tokenAsset: String,         // assets path: "tokens/kinetik_orrery.token"
    val features: List<String> = emptyList(),   // complication chips
    val styleTags: List<String> = emptyList(),   // Home filter chips (Classic/Sport/Minimal…)
)
