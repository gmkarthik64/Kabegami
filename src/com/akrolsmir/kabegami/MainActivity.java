package com.akrolsmir.kabegami;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore.Images.Media;
import android.support.v4.content.LocalBroadcastManager;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.akrolsmir.kabegami.WallpaperControlWidgetProvider.RefreshService;
import com.squareup.picasso.Picasso;

public class MainActivity extends Activity {

	private SharedPreferences prefs;
	private boolean filtered;
	private Menu menu;
	public static final String FIRST_TIME = "need tutorial";
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		filtered = false;
		if(menu != null)
			menu.findItem(R.id.action_filter).setTitle("Filter");
		requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		setContentView(R.layout.activity_main);

		findViewById(R.id.favButton).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				WallpaperManager.with(MainActivity.this).toggleFavorite();
			}
		});

		ImageButton nextButton = (ImageButton) findViewById(R.id.nextButton);
		nextButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				WallpaperManager.with(MainActivity.this).nextWallpaper();
			}
		});

		final ImageButton playPauseButton = (ImageButton) findViewById(R.id.pausePlayButton);
		playPauseButton
				.setImageResource(RefreshService.isCycling(this) ? android.R.drawable.ic_media_pause
						: android.R.drawable.ic_media_play);
		playPauseButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				playPauseButton.setImageResource(RefreshService
						.isCycling(MainActivity.this) ? android.R.drawable.ic_media_play
						: android.R.drawable.ic_media_pause);
				Intent intent = new Intent(MainActivity.this,
						RefreshService.class);
				startService(intent.setAction(RefreshService.TOGGLE));
			}
		});

		ImageButton cropButton = (ImageButton) findViewById(R.id.cropButton);
		cropButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				WallpaperManager.with(MainActivity.this).cropWallpaper(MainActivity.this);
			}
		});

		findViewById(R.id.currentBG).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setAction(Intent.ACTION_VIEW);
				intent.setDataAndType(
						Uri.fromFile(WallpaperManager.with(MainActivity.this)
								.getCurrentWallpaper().getCacheFile()),
						"image/*");
				startActivity(intent);
			}
		});
		
		// First time stuff
		prefs = getSharedPreferences("com.akrolsmir.kabegami.main",0);
		if (prefs.getBoolean(FIRST_TIME, true)) {
			// PRETTY UGLY HACKS ALL DAY
			prefs.edit().putBoolean(FIRST_TIME, false).apply();
			new Wallpaper(this); // Creates the default wallpaper
			playPauseButton.performClick();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		menu.findItem(R.id.action_settings).setIntent(
				new Intent(this, SettingsActivity.class));
		this.menu = menu;
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_info:
			WallpaperManager.with(this).displayInfo(this);
			return true;
		case R.id.action_filter:
			if(filtered)
			{
				((FavoritesView)findViewById(R.id.favorites)).unfilter();
				filtered = false;
				item.setTitle("Filter");
			}
			else
			{
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				final EditText input = new EditText(this);
				input.setInputType(InputType.TYPE_CLASS_TEXT);
				builder.setTitle("Filter Favorites by Keyword")
						.setView(input)
						.setPositiveButton("OK",
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog,
											int which) {
										InputMethodManager inputMethodManager = (InputMethodManager)  getSystemService(INPUT_METHOD_SERVICE);
									    inputMethodManager.hideSoftInputFromWindow(input.getWindowToken(), 0);
										((FavoritesView) findViewById(R.id.favorites)).filter(input.getText().toString());
										filtered = true;
										item.setTitle("Unfilter");
									}
								})
						.setNegativeButton("Cancel",
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog,
											int which) {
										InputMethodManager inputMethodManager = (InputMethodManager)  getSystemService(INPUT_METHOD_SERVICE);
									    inputMethodManager.hideSoftInputFromWindow(input.getWindowToken(), 0);
										dialog.cancel();
									}
								});
				AlertDialog ad = builder.create();
				ad.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
				ad.show();
			}
			return true;
		case R.id.action_reload:
			ConnectivityManager connectivityManager = (ConnectivityManager) this
					.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo activeNetworkInfo;
			if (SettingsActivity.allowData(this))
				activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
			else
				activeNetworkInfo = connectivityManager
						.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
			if (activeNetworkInfo == null || !activeNetworkInfo.isConnected())
				Toast.makeText(this, "Connect to the Internet and try again.",
						Toast.LENGTH_LONG).show();
			else {
				final WallpaperManager wpm = WallpaperManager.with(MainActivity.this);
				final Wallpaper wp = wpm.getCurrentWallpaper();
				wp.reload(new Wallpaper.ReloadCallback() {
					
					@Override
					public void onFinish() {
						onNextBG(); // Signal app to refresh views
					}
				});
				return true;
			}
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/* Listen for changes made by services/the widget */

	public final static String NEXT = "com.akrolsmir.kabegami.NEXT";
	public final static String FAVORITE = "com.akrolsmir.kabegami.FAVORITE";

	@Override
	protected void onResume() {
		getActionBar().setTitle("");  
		onNextBG();
		filtered = false;
		if(menu != null)
			menu.findItem(R.id.action_filter).setTitle("Filter");
		LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
		manager.registerReceiver(updateReceiver, new IntentFilter(NEXT));
		manager.registerReceiver(updateReceiver, new IntentFilter(FAVORITE));
		super.onResume();
	}

	@Override
	protected void onPause() {
		LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
		manager.unregisterReceiver(updateReceiver);
		super.onPause();
	}

	private BroadcastReceiver updateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(NEXT)) {
				onNextBG();
			} else if (intent.getAction().equals(FAVORITE)) {
				onFavorite();
			}
		}
	};

	private void onNextBG() {
		ImageView currentBG = (ImageView) findViewById(R.id.currentBG);
		Wallpaper wp = WallpaperManager.with(this).getCurrentWallpaper();
		File file = wp.imageInFavorites() ? wp.getFavoriteFile() : wp.getCacheFile();
		Picasso.with(this).load(file).fit().centerInside().into(currentBG);
		onFavorite();
	}

	private void onFavorite() {
		((FavoritesView) findViewById(R.id.favorites)).onFavorite();

		((ImageButton) findViewById(R.id.favButton))
				.setImageResource(WallpaperManager.with(this)
						.getCurrentWallpaper().imageInFavorites() ? android.R.drawable.star_big_on
						: android.R.drawable.star_big_off);
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 1 && resultCode == RESULT_OK) {
			android.app.WallpaperManager wpm = android.app.WallpaperManager
					.getInstance(MainActivity.this);
			Uri selectedImage = data.getData();
			try {
				Bitmap pic = Media.getBitmap(this.getContentResolver(),
						selectedImage);
				wpm.setBitmap(pic);
				getContentResolver().delete(selectedImage, null, null);
			} catch (FileNotFoundException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
	}
}
