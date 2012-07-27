package es.olidroide.android.SoundCloudWallpaper;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;

public class WallpaperSettings extends PreferenceActivity {

	private SharedPreferences mSharedPreferences;
	public static final String USERNAME_KEY = "username_key";
	public static final String PASSWORD_KEY = "password_key";

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.layout.wallpaper_settings);

		/*
		 * mSharedPreferences = this.getSharedPreferences(
		 * Wallpaper.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
		 * 
		 * mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
		 */

		// EditTextPreference editTextUsername = (EditTextPreference)
		// findViewById(R.id.username_preference);
	}

	/*
	 * public void onSharedPreferenceChanged(SharedPreferences
	 * sharedPreferences, String key) { Log.d(Wallpaper.LOG_TAG,
	 * "onSharedPreferencesChanged() "+key);
	 * //mSharedPreferences.edit().putString(key, value) }
	 */

}
