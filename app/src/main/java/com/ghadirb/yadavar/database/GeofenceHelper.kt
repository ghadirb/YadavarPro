package com.ghadirb.yadavar.database

import android.content.Context
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * Wraps Play Services Geofencing for LOCATION_BASED reminders ("remind me when I get to
 * the pharmacy"). Requires ACCESS_FINE_LOCATION to already be granted by the caller -
 * MainActivity should request it before letting the user create this reminder type.
 */
object GeofenceHelper {

    private fun client(context: Context): GeofencingClient =
        LocationServices.getGeofencingClient(context)

    fun registerGeofence(context: Context, reminder: ReminderEntity) {
        val lat = reminder.locationLat ?: return
        val lng = reminder.locationLng ?: return

        val geofence = Geofence.Builder()
            .setRequestId(reminder.id.toString())
            .setCircularRegion(lat, lng, reminder.locationRadius.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        // pendingIntent construction / permission checks intentionally left to the
        // integrator - wire this up to a GeofenceBroadcastReceiver once location
        // permission UX is in place.
        Log.d("GeofenceHelper", "Registering geofence for reminder ${reminder.id}")
        // client(context).addGeofences(request, pendingIntent)
    }

    fun removeGeofence(context: Context, reminder: ReminderEntity) {
        client(context).removeGeofences(listOf(reminder.id.toString()))
    }
}
