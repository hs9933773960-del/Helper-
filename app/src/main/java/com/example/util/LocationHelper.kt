package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class ResolvedLocation(
    val city: String,
    val area: String,
    val latitude: Double?,
    val longitude: Double?
)

@SuppressLint("MissingPermission")
suspend fun fetchCurrentCityAndArea(context: Context): Result<ResolvedLocation> = try {
    val client = LocationServices.getFusedLocationProviderClient(context)
    val cts = CancellationTokenSource()
    val location = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
        .await() ?: client.lastLocation.await()

    if (location == null) {
        Result.failure(IllegalStateException("Location unavailable. Please select city manually."))
    } else {
        val resolved = reverseGeocode(context, location.latitude, location.longitude)
        Result.success(resolved)
    }
} catch (e: Exception) {
    Result.failure(e)
}

private suspend fun reverseGeocode(
    context: Context,
    lat: Double,
    lon: Double
): ResolvedLocation = withContext(Dispatchers.IO) {
    val geocoder = Geocoder(context, Locale.getDefault())
    @Suppress("DEPRECATION")
    val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { cont ->
            geocoder.getFromLocation(lat, lon, 1) { list ->
                cont.resume(list)
            }
        }
    } else {
        geocoder.getFromLocation(lat, lon, 1).orEmpty()
    }
    val addr = addresses.firstOrNull()
    val city = addr?.locality ?: addr?.subAdminArea ?: addr?.adminArea ?: "Kolkata"
    val area = addr?.subLocality ?: addr?.thoroughfare ?: ""
    ResolvedLocation(
        city = city,
        area = area,
        latitude = lat,
        longitude = lon
    )
}

fun launchDialer(context: Context, phoneNumber: String): Boolean {
    val digits = phoneNumber.filter { it.isDigit() || it == '+' }
    if (digits.isBlank()) return false
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))
    return try {
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}

fun launchUpiPaymentApp(
    context: Context,
    upiId: String,
    payeeName: String,
    amountInr: Long,
    note: String,
    targetPackage: String? = null
): Boolean {
    val uri = Uri.Builder()
        .scheme("upi")
        .authority("pay")
        .appendQueryParameter("pa", upiId)
        .appendQueryParameter("pn", payeeName)
        .appendQueryParameter("tn", note)
        .appendQueryParameter("am", amountInr.toString())
        .appendQueryParameter("cu", "INR")
        .build()

    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        if (!targetPackage.isNullOrBlank()) {
            setPackage(targetPackage)
        }
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        if (!targetPackage.isNullOrBlank()) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                true
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }
    }
}
