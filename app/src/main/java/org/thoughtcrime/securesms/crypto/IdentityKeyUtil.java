/*
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2013 Open Whisper Systems
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.backup.BackupProtos;
import org.thoughtcrime.securesms.util.Base64;
import org.session.libsignal.libsignal.IdentityKey;
import org.session.libsignal.libsignal.IdentityKeyPair;
import org.session.libsignal.libsignal.InvalidKeyException;
import org.session.libsignal.libsignal.ecc.Curve;
import org.session.libsignal.libsignal.ecc.ECKeyPair;
import org.session.libsignal.libsignal.ecc.ECPrivateKey;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Utility class for working with identity keys.
 * 
 * @author Moxie Marlinspike
 */
//TODO AC: Delete
public class IdentityKeyUtil {

  private static final String MASTER_SECRET_UTIL_PREFERENCES_NAME = "SecureSMS-Preferences";

  @SuppressWarnings("unused")
  private static final String TAG = IdentityKeyUtil.class.getSimpleName();

  public static final String IDENTITY_PUBLIC_KEY_PREF                    = "pref_identity_public_v3";
  public static final String IDENTITY_PRIVATE_KEY_PREF                   = "pref_identity_private_v3";
  public static final String ED25519_PUBLIC_KEY                          = "pref_ed25519_public_key";
  public static final String ED25519_SECRET_KEY                          = "pref_ed25519_secret_key";
  public static final String LOKI_SEED                                   = "loki_seed";

  public static boolean hasIdentityKey(Context context) {
    SharedPreferences preferences = context.getSharedPreferences(MASTER_SECRET_UTIL_PREFERENCES_NAME, 0);

    return
        preferences.contains(IDENTITY_PUBLIC_KEY_PREF) &&
        preferences.contains(IDENTITY_PRIVATE_KEY_PREF);
  }

  public static @NonNull IdentityKey getIdentityKey(@NonNull Context context) {
    if (!hasIdentityKey(context)) throw new AssertionError("There isn't one!");

    try {
      byte[] publicKeyBytes = Base64.decode(retrieve(context, IDENTITY_PUBLIC_KEY_PREF));
      return new IdentityKey(publicKeyBytes, 0);
    } catch (IOException | InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  public static @NonNull IdentityKeyPair getIdentityKeyPair(@NonNull Context context) {
    if (!hasIdentityKey(context)) throw new AssertionError("There isn't one!");

    try {
      IdentityKey  publicKey  = getIdentityKey(context);
      ECPrivateKey privateKey = Curve.decodePrivatePoint(Base64.decode(retrieve(context, IDENTITY_PRIVATE_KEY_PREF)));

      return new IdentityKeyPair(publicKey, privateKey);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  public static List<BackupProtos.SharedPreference> getBackupRecords(@NonNull Context context) {
    final String prefName = MASTER_SECRET_UTIL_PREFERENCES_NAME;
    SharedPreferences preferences = context.getSharedPreferences(prefName, 0);

    LinkedList<BackupProtos.SharedPreference> prefList = new LinkedList<>();

    prefList.add(BackupProtos.SharedPreference.newBuilder()
            .setFile(prefName)
            .setKey(IDENTITY_PUBLIC_KEY_PREF)
            .setValue(preferences.getString(IDENTITY_PUBLIC_KEY_PREF, null))
            .build());
    prefList.add(BackupProtos.SharedPreference.newBuilder()
            .setFile(prefName)
            .setKey(IDENTITY_PRIVATE_KEY_PREF)
            .setValue(preferences.getString(IDENTITY_PRIVATE_KEY_PREF, null))
            .build());
    if (preferences.contains(ED25519_PUBLIC_KEY)) {
      prefList.add(BackupProtos.SharedPreference.newBuilder()
              .setFile(prefName)
              .setKey(ED25519_PUBLIC_KEY)
              .setValue(preferences.getString(ED25519_PUBLIC_KEY, null))
              .build());
    }
    if (preferences.contains(ED25519_SECRET_KEY)) {
      prefList.add(BackupProtos.SharedPreference.newBuilder()
              .setFile(prefName)
              .setKey(ED25519_SECRET_KEY)
              .setValue(preferences.getString(ED25519_SECRET_KEY, null))
              .build());
    }
    prefList.add(BackupProtos.SharedPreference.newBuilder()
            .setFile(prefName)
            .setKey(LOKI_SEED)
            .setValue(preferences.getString(LOKI_SEED, null))
            .build());

    return prefList;
  }

  public static String retrieve(Context context, String key) {
    SharedPreferences preferences = context.getSharedPreferences(MASTER_SECRET_UTIL_PREFERENCES_NAME, 0);
    return preferences.getString(key, null);
  }

  public static void save(Context context, String key, String value) {
    SharedPreferences preferences   = context.getSharedPreferences(MASTER_SECRET_UTIL_PREFERENCES_NAME, 0);
    Editor preferencesEditor        = preferences.edit();

    preferencesEditor.putString(key, value);
    if (!preferencesEditor.commit()) throw new AssertionError("failed to save identity key/value to shared preferences");
  }

  public static void delete(Context context, String key) {
    context.getSharedPreferences(MASTER_SECRET_UTIL_PREFERENCES_NAME, 0).edit().remove(key).commit();
  }
}
