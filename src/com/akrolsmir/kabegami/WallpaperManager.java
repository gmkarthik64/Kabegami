package com.akrolsmir.kabegami;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class WallpaperManager {

	private SharedPreferences settings;
	private Context context;
	private static WallpaperManager instance;
	private boolean setNextWallpaperAsBG = false;

	private WallpaperManager(Context context) {
		this.settings = context.getSharedPreferences(
				"com.akrolsmir.kabegami.WallpaperManager", 0);
		this.context = context;
		fetchNextUrls();
	}

	public static WallpaperManager with(Context context) {
		return instance == null ? instance = new WallpaperManager(context)
				: instance;
	}

	public void setWallpaper(File file) {
		getCurrentWallpaper().uncache();
		String last = settings.getString((file.toURI() + "").substring((file.toURI() + "")
				.lastIndexOf('/') + 1) + "_url", file.toURI() + "")
				+ "|"
				+ (file.toURI() + "").substring((file.toURI() + "").lastIndexOf('/') + 1) + " ";
		setHistory(last + getHistory());
		getCurrentWallpaper().setAsBackground();
		// Notify MainActivity and the widget to update their views
		LocalBroadcastManager.getInstance(context).sendBroadcast(
				new Intent(MainActivity.NEXT));
		WallpaperControlWidgetProvider.updateViews(context);
	}

	public void nextWallpaper() {
		if (!getQueue().contains(" ")) {
			ConnectivityManager connectivityManager = (ConnectivityManager) context
					.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo activeNetworkInfo;
			if(SettingsActivity.allowData(context))
				activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
			else
				activeNetworkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
			if (activeNetworkInfo == null || !activeNetworkInfo.isConnected())
				Toast.makeText(context,
						"Connect to the Internet and try again.",
						Toast.LENGTH_LONG).show();
			else {
				Toast.makeText(
						context,
						"Out of unique images. Try changing settings to avoid this issue.",
						Toast.LENGTH_LONG).show();
			}
			return;
		}
		if (!getNextWallpaper().imageInCache()) {
			resetQueue();
			Toast.makeText(
					context,
					"Finding more images. Wait a few seconds and try again.",
					Toast.LENGTH_LONG).show();
			return;
		}
		getCurrentWallpaper().uncache();
		advanceCurrent();
		getCurrentWallpaper().setAsBackground();
		// Notify MainActivity and the widget to update their views
		LocalBroadcastManager.getInstance(context).sendBroadcast(
				new Intent(MainActivity.NEXT));
		WallpaperControlWidgetProvider.updateViews(context);
	}

	public void toggleFavorite() {
		getCurrentWallpaper().toggleFavorite();
		// Notify MainActivity and the widget to update their views
		LocalBroadcastManager.getInstance(context).sendBroadcast(
				new Intent(MainActivity.FAVORITE));
		WallpaperControlWidgetProvider.updateViews(context);
	}

	public Wallpaper getCurrentWallpaper() {
		return new Wallpaper(context, getCurrentWallpaperURL());
	}
	
	public Wallpaper getNextWallpaper() {
		return new Wallpaper(context, getNextWallpaperURL());
	}

	public void resetQueue() {
		setQueue("");
		for (File file : context.getExternalCacheDir().listFiles())
			if (!file.equals(getCurrentWallpaper().getCacheFile()))
				file.delete();
		fetchNextUrls();
	}
	
	@SuppressLint("NewApi")
	public void cropWallpaper( Context cont ) {
		// TODO Try using WPM.getCropAndSetWallpaperIntent on sdk 19 and
		// higher
		android.app.WallpaperManager wpm = android.app.WallpaperManager
				.getInstance(cont);
		if (android.os.Build.VERSION.SDK_INT >= 19) {
			try {
				Uri contUri = Uri.parse(android.provider.MediaStore.Images.Media
						.insertImage(cont.getContentResolver(),
								getCurrentWallpaper()
										.getCacheFile()
										.getAbsolutePath(), null, null));
				Intent cropSetIntent = wpm
						.getCropAndSetWallpaperIntent(contUri);
				cropSetIntent.setDataAndType(contUri,"image/*");
				cont.startActivity(cropSetIntent);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				backupCrop(wpm, cont);
				e.printStackTrace();
			} 
		} else {
			backupCrop(wpm, cont);
		}
	}
	
	public void removeFavorite(File f) {
		String canonicalPath = "";
		try {
			canonicalPath = f.getCanonicalPath();
		} catch (IOException e) {
			canonicalPath = f.getAbsolutePath();
		}
		if (!getCurrentWallpaperURL().contains(canonicalPath.substring(canonicalPath.lastIndexOf("/") + 1)))
			removeInfo(canonicalPath.substring(canonicalPath.lastIndexOf("/") + 1));
		final Uri uri = MediaStore.Files.getContentUri("external");
		final int result = context.getContentResolver().delete(uri,
				MediaStore.Files.FileColumns.DATA + "=?",
				new String[] { canonicalPath });
		if (result == 0) {
			final String absolutePath = f.getAbsolutePath();
			if (!absolutePath.equals(canonicalPath)) {
				context.getContentResolver().delete(uri,
						MediaStore.Files.FileColumns.DATA + "=?",
						new String[] { absolutePath });
			}
		}
	}

	/* Private helper methods */

	private void backupCrop( android.app.WallpaperManager wpm, Context cont)
	{
		Intent cropIntent = new Intent(
				"com.android.camera.action.CROP");
		cropIntent.setDataAndType(
				Uri.fromFile(getCurrentWallpaper().getCacheFile()),
				"image/*");
		cropIntent
				.setClassName("com.google.android.apps.plus",
						"com.google.android.apps.photoeditor.fragments.PlusCropActivityAlias");
		cropIntent.putExtra("crop", "true");
		cropIntent.putExtra("aspectX", wpm.getDesiredMinimumWidth());
		cropIntent.putExtra("aspectY",
				wpm.getDesiredMinimumHeight());
		cropIntent.putExtra("outputX", wpm.getDesiredMinimumWidth());
		cropIntent.putExtra("outputY",
				wpm.getDesiredMinimumHeight());
		cropIntent.putExtra("return-data", true);
		try {
			((Activity)cont).startActivityForResult(cropIntent, 1);
		} catch (ActivityNotFoundException anfe) {
			try {
				cropIntent
						.setClassName(
								"com.google.android.apps.plus",
								"com.google.android.apps.photoeditor.fragments.PlusCropActivity");
				((Activity)cont).startActivityForResult(cropIntent, 1);
			} catch (ActivityNotFoundException anfe2) {
				Toast.makeText(context,
						"Cropping requires the latest Google+.",
						Toast.LENGTH_LONG).show();
			}
		}
	}
	public void fetchNextUrls() {
		ConnectivityManager connectivityManager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetworkInfo;
		if(SettingsActivity.allowData(context))
			activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
		else
			activeNetworkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		if (activeNetworkInfo == null || !activeNetworkInfo.isConnected())
			return;
		int index = settings.getInt("index", 0);
		int total = StringUtils.countOccurrencesOf(getQueue(), " ");
		fetchNextUrls(3 + index - total);
	}

	private void fetchNextUrls(final int numToFetch) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				int offs[] = {0,0,0,0,0,0,0,0,0,0};
				RestTemplate restTemplate = new RestTemplate();
				restTemplate.getMessageConverters().add(
						new StringHttpMessageConverter());
				String rawJson = "";
				for (int i = 0; i < numToFetch; i++) {
					int sr = (int) (10 * Math.random());
					if (getSubreddit(sr).length() == 0)
						i--;
					else {
						try{
						rawJson = restTemplate.getForObject(
								"http://api.tumblr.com/v2/blog/" + getSubreddit(sr)
										+ ".tumblr.com/posts/photo?api_key=fuiKNFp9vQFvjLNvx4sUwti4Yb5yGutBN4Xh10LXZhhRKjWlV4&offset="+(20*offs[sr]),
								String.class);
						}
						catch(Exception e)
						{
							
							break;
						}
						if (!parseUrlFromReddit(rawJson))
						{
							i--;
							offs[sr]++;
						}
					}
				}
			}
		}).start();
	}

	private String getSubreddit(int index) {
		return SettingsActivity.getSubreddit(context, index);
	}

	private boolean parseUrlFromReddit(String rawJson) {
		try {
			JsonElement object = new JsonParser().parse(rawJson);
			JsonArray children = object.getAsJsonObject().get("response").getAsJsonObject().get("posts").getAsJsonArray();
			boolean nsfw = object.getAsJsonObject().get("response").getAsJsonObject().get("blog")
					.getAsJsonObject().get("is_nsfw").getAsBoolean();
			Log.d("LOG","reached");
			for(JsonElement inter: children){
			for (JsonElement child : inter.getAsJsonObject().get("photos").getAsJsonArray()) {
				String url = child.getAsJsonObject().get("original_size")
						.getAsJsonObject().get("url").getAsString().replace("\\", "");
				if (url.contains("imgur.com") && !url.contains("i.imgur.com")
						&& !url.contains("imgur.com/gallery/")
						&& !url.contains("imgur.com/a/"))
					url += ".jpg";
				
				if (validImageUrl(url)
						&& (!nsfw || SettingsActivity.showNSFW(context))) {
					String postURL = inter.getAsJsonObject().get("post_url").getAsString();
					String subreddit = object.getAsJsonObject().get("response").getAsJsonObject().get("blog")
							.getAsJsonObject().get("name").getAsString();
					String title = inter.getAsJsonObject().get("caption").getAsString();
					enqueueURL(url, url.substring(1+url.lastIndexOf('/')));
					addInfo(url.substring(1+url.lastIndexOf('/')), subreddit, title, postURL, url);
					return true;
				}
			}
		}
		}catch (Exception e) {
			return false;
		}
		return false;
		// TODO handle case when out of images
		// return "http://i.imgur.com/iCQxSJZ.jpg";
	}

	// Returns true if URL has no spaces, ends in .jpg/.png and is not enqueued
	private boolean validImageUrl(String imageURL) {
		return !imageURL.contains(" ")
				&& imageURL.matches("https?://.*\\.(jpg|png)$")
				&& isNew(imageURL);
	}

	private boolean isNew(String imageURL) {
		return !(getHistory() + getQueue()).contains(imageURL);
	}

	private void enqueueURL(String imageURL, String imageName) {
		Log.d("enqueueURL", imageURL + "|" + imageName);
		setQueue(getQueue() + imageURL + "|" + imageName + " ");
		if (setNextWallpaperAsBG) { // || settings.getString(QUEUE, "").length
									// == 0
			nextWallpaper();
			setNextWallpaperAsBG = false;
		} else {
			new Wallpaper(context, imageURL + "|" + imageName).cache();
		}
	}
	

	private void addInfo(String name, String sr, String title, String postURL,
			String url) {
		Log.d("AddInfo", name);
		settings.edit().putString(name + "_sr", sr).apply();
		settings.edit().putString(name + "_title", title).apply();
		settings.edit().putString(name + "_postURL", postURL).apply();
		settings.edit().putString(name + "_url", url).apply();
	}

	public void removeInfo(String name) {
		Log.d("RemoveInfo ", name);
		settings.edit().remove(name + "_sr").commit();
		settings.edit().remove(name + "_title").commit();
		settings.edit().remove(name + "_postURL").commit();
		settings.edit().remove(name + "_url").commit();
	}

	public void displayInfo(Context context) {
		displayInfo(getCurrentWallpaperURL().split(Pattern.quote("|"))[1],
				context);
	}

	public void displayInfo(String name, final Context context) {
		Log.d("DisplayInfo", name);
		final String[] rawInfo = { settings.getString(name + "_sr", "N/A"),
				settings.getString(name + "_postURL", "N/A"),
				settings.getString(name + "_url", "N/A"),
				settings.getString(name + "_title", "N/A") };
		Spanned[] info = {
				Html.fromHtml("<b>Subreddit:</b><br/>" + rawInfo[0]),
				Html.fromHtml("<b>Post Title:</b><br/>" + rawInfo[3]),
				Html.fromHtml("<b>Image URL:</b><br/>" + rawInfo[2]) };
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle("Details")
				.setItems(info, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if (!rawInfo[which].equals("N/A")) {
							Intent browserIntent;
							if (which == 0)
								browserIntent = new Intent(Intent.ACTION_VIEW,
										Uri.parse("http://"+rawInfo[which]+".tumblr.com"));
							else
								browserIntent = new Intent(Intent.ACTION_VIEW,
										Uri.parse(rawInfo[which]));
							context.startActivity(browserIntent);
						}

					}
				})
				.setPositiveButton("Done",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
							}
						});
		builder.show();
	}

	// The current wallpaper is at the top of the history stack
	private String DEFAULT_URL = "http://cdn.awwni.me/maav.jpg|default0.jpg";

	public String getCurrentWallpaperURL() {
		String url = getHistory().split(" ")[0];
		return url.contains("/") ? url : DEFAULT_URL;
	}
	
	public String getNextWallpaperURL() {
		String url = getQueue().split(" ")[0];
		return url.contains("/") ? url : DEFAULT_URL;
	}

	// Push the head of the queue onto history, which marks it as current
	private void advanceCurrent() {
		setQueue(getQueue().substring(getQueue().indexOf(" ") + 1));
		setHistory(getQueue().split(" ")[0] + " " + getHistory());
		Log.d("HISTORY", getHistory());
		Log.d("QUEUE", getQueue());

		fetchNextUrls();
	}
	
	private String HISTORY = "history", QUEUE = "queue";
	
	private String getHistory() {
		return settings.getString(HISTORY, DEFAULT_URL + " ");
	}
	
	private String getQueue() {
		return settings.getString(QUEUE, DEFAULT_URL + " ");
	}
	
	private void setHistory(String history) {
		settings.edit().putString(HISTORY, history).apply();
	}
	
	private void setQueue(String queue) {
		settings.edit().putString(QUEUE, queue).apply();
	}
}
