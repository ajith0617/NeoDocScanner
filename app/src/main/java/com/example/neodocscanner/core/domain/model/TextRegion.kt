package com.example.neodocscanner.core.domain.model

/**
 * A single text observation from OCR, stored alongside its bounding box.
 * x, y, w, h are normalised (0–1) coordinates.
 *
 * iOS equivalent: TextRegion struct in OCRService.swift.
 *
 * Coordinate origin note:
 *   iOS Vision uses bottom-left origin. ML Kit Text Recognition uses
 *   top-left origin (standard Android). No Y-flip is needed on Android.
 */
data class TextRegion(
    val text: String,
    val x: Float,    // minX, normalised
    val y: Float,    // minY, normalised (top-left origin on Android)
    val w: Float,    // width, normalised
    val h: Float     // height, normalised
)
