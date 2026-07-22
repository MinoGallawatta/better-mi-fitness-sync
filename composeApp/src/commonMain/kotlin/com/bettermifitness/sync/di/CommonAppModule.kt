package com.bettermifitness.sync.di

import com.bettermifitness.sync.data.MiRegionDiscovery
import com.bettermifitness.sync.data.MiSessionManager
import com.bettermifitness.sync.data.preferences.CredentialsPort
import com.bettermifitness.sync.data.preferences.CredentialsStore
import com.bettermifitness.sync.data.preferences.SyncPreferences
import com.bettermifitness.sync.data.preferences.SyncPreferencesPort
import com.bettermifitness.sync.data.preferences.SyncSessionPort
import com.bettermifitness.sync.data.preferences.TokenStore
import com.bettermifitness.sync.data.repository.HealthRepository
import com.bettermifitness.sync.data.repository.HealthSyncRunner
import com.bettermifitness.sync.health.HealthAvailability
import com.bettermifitness.sync.health.HealthPermissionRequester
import com.bettermifitness.sync.health.HealthSampleWriter
import com.bettermifitness.sync.health.HealthStore
import com.bettermifitness.sync.health.HealthWriter
import com.bettermifitness.sync.strava.StravaAuth
import com.bettermifitness.sync.strava.StravaCredentialsStore
import com.bettermifitness.sync.strava.StravaSessionManager
import com.bettermifitness.sync.strava.StravaUploadApi
import com.bettermifitness.sync.strava.StravaUploadLedger
import com.bettermifitness.sync.strava.StravaWorkoutSyncer
import com.bettermifitness.sync.sync.SyncCoordinator
import com.bettermifitness.sync.ui.home.HomeViewModel
import com.bettermifitness.sync.ui.login.LoginViewModel
import com.bettermifitness.sync.ui.settings.SettingsViewModel
import com.bettermifitness.sync.ui.sync.SyncViewModel
import com.mifitness.miclient.auth.MiAuth
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Shared Koin graph for Android and iOS.
 * Platform-only factories (DataStore path, HealthWriter instance) live in [platformModule].
 */
fun commonAppModule(): Module = module {
    single { CredentialsStore(get()) }
    single<CredentialsPort> { get<CredentialsStore>() }
    single { SyncPreferences(get()) }
    single<SyncPreferencesPort> { get<SyncPreferences>() }
    single { TokenStore(get(), get(), get()) }

    single { MiAuth() }
    single { MiSessionManager(credentialsStore = get(), miAuth = get()) }
    single<SyncSessionPort> { get<MiSessionManager>() }
    single { MiRegionDiscovery() }

    // Platform module registers HealthWriter; bind ISP ports to the same instance.
    single<HealthStore> { get<HealthWriter>() }
    single<HealthSampleWriter> { get<HealthWriter>() }
    single<HealthAvailability> { get<HealthWriter>() }
    single<HealthPermissionRequester> { get<HealthWriter>() }

    single { StravaCredentialsStore(get()) }
    single { StravaAuth() }
    single { StravaSessionManager(get(), get()) }
    single { StravaUploadApi() }
    single { StravaUploadLedger(get()) }
    single { StravaWorkoutSyncer(get(), get(), get()) }

    single { HealthRepository(get(), get<HealthSampleWriter>(), get()) }
    single<HealthSyncRunner> { get<HealthRepository>() }
    single {
        SyncCoordinator(
            session = get<SyncSessionPort>(),
            credentials = get<CredentialsPort>(),
            syncPreferences = get<SyncPreferencesPort>(),
            healthAvailability = get<HealthAvailability>(),
            healthPermissions = get<HealthPermissionRequester>(),
            repository = get<HealthSyncRunner>(),
        )
    }

    factory {
        LoginViewModel(
            miAuth = get(),
            sessionManager = get(),
            credentialsStore = get(),
            regionDiscovery = get(),
        )
    }
    factory { HomeViewModel(session = get(), tokenStore = get(), healthAvailability = get()) }
    factory {
        SyncViewModel(
            repository = get(),
            healthAvailability = get(),
            syncPreferences = get(),
            syncCoordinator = get(),
        )
    }
    factory {
        SettingsViewModel(
            syncPreferences = get(),
            healthAvailability = get(),
        )
    }
}
