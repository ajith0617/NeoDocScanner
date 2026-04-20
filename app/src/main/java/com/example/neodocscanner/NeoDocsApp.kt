package com.example.neodocscanner

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Required by Hilt to trigger code generation
 * for dependency injection across the app.
 *
 * iOS equivalent: NeoDocsApp.swift — the @main struct that bootstraps
 * the SwiftData ModelContainer and the View hierarchy.
 */
@HiltAndroidApp
class NeoDocsApp : Application()
