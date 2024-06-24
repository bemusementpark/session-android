package org.thoughtcrime.securesms

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import network.loki.messenger.BuildConfig
import org.session.libsession.utilities.prefs
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DeviceModule {
    @Provides
    @Singleton
    fun provides() = BuildConfig.DEVICE

    @Provides
    @Singleton
    fun prefs(@ApplicationContext context: Context) = context.prefs
}
