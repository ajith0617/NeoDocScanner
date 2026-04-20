package com.example.neodocscanner.core.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.neodocscanner.core.domain.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists user profile fields (name, designation, organisation, email).
 *
 * iOS equivalent: @AppStorage("profileName"), @AppStorage("profileDesignation"),
 * @AppStorage("profileOrganisation"), @AppStorage("profileEmail") in ProfileView.swift.
 *
 * Uses a dedicated DataStore instance ("neodocs_profile") kept separate from
 * the session and feature-preferences stores for clarity.
 */
private val Context.profileDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "neodocs_profile")

@Singleton
class ProfileDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val PROFILE_NAME         = stringPreferencesKey("profileName")
        val PROFILE_DESIGNATION  = stringPreferencesKey("profileDesignation")
        val PROFILE_ORGANISATION = stringPreferencesKey("profileOrganisation")
        val PROFILE_EMAIL        = stringPreferencesKey("profileEmail")
    }

    val profileFlow: Flow<UserProfile> = context.profileDataStore.data.map { prefs ->
        UserProfile(
            name         = prefs[Keys.PROFILE_NAME]         ?: "",
            designation  = prefs[Keys.PROFILE_DESIGNATION]  ?: "",
            organisation = prefs[Keys.PROFILE_ORGANISATION] ?: "",
            email        = prefs[Keys.PROFILE_EMAIL]        ?: ""
        )
    }

    suspend fun saveProfile(profile: UserProfile) {
        context.profileDataStore.edit { prefs ->
            prefs[Keys.PROFILE_NAME]         = profile.name
            prefs[Keys.PROFILE_DESIGNATION]  = profile.designation
            prefs[Keys.PROFILE_ORGANISATION] = profile.organisation
            prefs[Keys.PROFILE_EMAIL]        = profile.email
        }
    }
}
