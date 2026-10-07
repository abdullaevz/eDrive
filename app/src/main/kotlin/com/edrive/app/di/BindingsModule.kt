package com.edrive.app.di

import com.edrive.app.data.vault.UploadScheduler
import com.edrive.app.data.vault.WorkManagerUploadScheduler
import com.edrive.app.drive.DriveAuth
import com.edrive.app.drive.DriveAuthorizer
import com.edrive.app.drive.DriveClientProvider
import com.edrive.app.drive.GoogleDriveClientProvider
import com.edrive.app.security.AndroidBiometricGate
import com.edrive.app.security.Argon2Android
import com.edrive.app.security.BiometricGate
import com.edrive.app.security.DeviceKeyStore
import com.edrive.app.security.KeystoreDeviceKeys
import com.edrive.app.security.KeystorePinHasher
import com.edrive.app.security.PinHasher
import com.edrive.crypto.PasswordKdf
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * İnterfeys → real implementasiya bağlantıları (Dependency Inversion).
 * Xidmətlər yalnız interfeysi tanıyır; testlərdə saxta implementasiyalar əl ilə ötürülür.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun passwordKdf(impl: Argon2Android): PasswordKdf
    @Binds abstract fun biometricGate(impl: AndroidBiometricGate): BiometricGate
    @Binds abstract fun deviceKeyStore(impl: KeystoreDeviceKeys): DeviceKeyStore
    @Binds abstract fun pinHasher(impl: KeystorePinHasher): PinHasher
    @Binds abstract fun driveClientProvider(impl: GoogleDriveClientProvider): DriveClientProvider
    @Binds abstract fun driveAuthorizer(impl: DriveAuth): DriveAuthorizer
    @Binds abstract fun uploadScheduler(impl: WorkManagerUploadScheduler): UploadScheduler
}
