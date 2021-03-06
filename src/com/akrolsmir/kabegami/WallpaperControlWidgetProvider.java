package com.akrolsmir.kabegami;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.widget.RemoteViews;

public class WallpaperControlWidgetProvider extends AppWidgetProvider {

	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		Log.d("TAG", "onUpdate");

		// Perform this loop procedure for each App Widget that belongs to this provider
		for (int appWidgetId : appWidgetIds) {

			RemoteViews views = new RemoteViews(context.getPackageName(),
					R.layout.wallpaper_control_widget);

			// Create an Intent to start the Service
			// Get the layout for the App Widget and attach an on-click listener
			// to the button
			Intent intent = new Intent(context, NextWallpaperService.class);
			PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, 0);
			views.setOnClickPendingIntent(R.id.skipButton, pendingIntent);

			intent = new Intent(context, MainActivity.class);
			pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
			views.setOnClickPendingIntent(R.id.manageButton, pendingIntent);

			intent = new Intent(context, FavoriteWallpaperService.class);
			pendingIntent = PendingIntent.getService(context, 0, intent, 0);
			views.setOnClickPendingIntent(R.id.favButton, pendingIntent);
			
			updateViews(context);

			// Tell the AppWidgetManager to perform an update on the current app widget
			appWidgetManager.updateAppWidget(appWidgetId, views);
		}
	}
	
	public static void updateViews(Context context) { 
		RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.wallpaper_control_widget);
		remoteViews.setImageViewResource(R.id.favButton,
				WallpaperManager.with(context).getCurrentWallpaper().imageInFavorites() ?
					android.R.drawable.star_big_on : 
					android.R.drawable.star_big_off);
		ComponentName thisWidget = new ComponentName(context, WallpaperControlWidgetProvider.class);
		AppWidgetManager.getInstance(context).updateAppWidget(thisWidget, remoteViews);
	}

	public static class FavoriteWallpaperService extends IntentService {

		public FavoriteWallpaperService() {
			super("FavoriteWallpaperService");
		}

		@Override
		protected void onHandleIntent(Intent intent) {
			WallpaperManager.with(this).toggleFavorite();
		}
	}

	public static class NextWallpaperService extends IntentService {

		public NextWallpaperService() {
			super("NextWallpaperService");
		}

		@Override
		protected void onHandleIntent(Intent intent) {
			Log.d("Changing wallpaper", "...");
			WallpaperManager.with(this).nextWallpaper();
		}
	}

	public static class RefreshService extends IntentService {
		
		public RefreshService() {
			super("RefreshService");
		}
		
		private static String KEY_CYCLING = "com.akrolsmir.kabegami.cycling";
		
		public static boolean isCycling(Context context) {
			return context.getSharedPreferences(KEY_CYCLING, 0).getBoolean(KEY_CYCLING, false);
		}
		
		private void setCycling(boolean value) {
			getSharedPreferences(KEY_CYCLING, 0).edit().putBoolean(KEY_CYCLING, value).commit();
		}
		
		public final static String TOGGLE = "com.akrolsmir.kabegami.TOGGLE",
				BOOT = "com.akrolsmir.kabegami.BOOT", UPDATE = "com.akrolsmir.kabegami.UPDATE";

		@Override
		protected void onHandleIntent(Intent intent) {

			Intent intent2 = new Intent(this, NextWallpaperService.class);
			PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent2, 0);

			AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

			long period = SettingsActivity.getRefreshSeconds(this) * 1000;
			alarmManager.cancel(pendingIntent); //Cancels any past refresh
			
			if (intent.getAction().equals(BOOT)) {
				if (isCycling(this)) {
					alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME,
							SystemClock.elapsedRealtime() + period / 2, period, pendingIntent);
				}
			} else if (intent.getAction().equals(TOGGLE)) {
				if (isCycling(this)) {
					// stop by doing nothing
				} else {
					alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 0, period, pendingIntent);
				}
				setCycling(!isCycling(this));
			} else if (intent.getAction().equals(UPDATE)) {
				if(isCycling(this)) {
					alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 
							SystemClock.elapsedRealtime() + period, period, pendingIntent);
				}
			}

		}
	}

	// Restart alarm on boot
	public static class BootBroadcastReceiver extends BroadcastReceiver {
		
		public BootBroadcastReceiver() {}

		@Override
		public void onReceive(Context context, Intent intent) {
			context.startService(new Intent(context, RefreshService.class).setAction(RefreshService.BOOT));
		}
	}
}
