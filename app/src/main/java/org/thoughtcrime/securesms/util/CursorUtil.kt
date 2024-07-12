package org.thoughtcrime.securesms.util

import android.database.Cursor

fun Cursor.asSequence(): Sequence<Cursor> =
    generateSequence { takeIf { moveToNext() } }

fun <R> Cursor.map(transform: (Cursor) -> R): Sequence<R> = asSequence().map(transform)
