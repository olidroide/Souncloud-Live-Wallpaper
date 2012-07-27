package es.olidroide.android.SoundCloudWallpaper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import com.nostra13.universalimageloader.core.download.URLConnectionImageDownloader;

import es.olidroide.android.SoundCloudWallpaper.SoundCloudHelper.Song;

public class Wallpaper extends WallpaperService {

	public static final String LOG_TAG = "soundcloudLiveWallpaper";
	public static final String SHARED_PREFS_NAME = "soundCloudWallpaperSettings";

	private static final int MILLIS_TO_RELOAD = 10000;

	private GestureDetector gestureDetector;
	private SharedPreferences mSharedPreferences;
	private Long timeInMillisPreviousLoad = Long.MIN_VALUE;
	private AccountManager mAccountManager;

	private SoundCloudHelper mSoundCloudHelper;
	private Song mSong;
	private float mOffset;
	private final Handler mHandler = new Handler();
	private BroadcastReceiver mChecckInternetConnection;
	private boolean mAlreadyConnected = false;

	// For check if the wallpaper are visible to user or not
	private boolean mVisible;

	@Override
	public void onCreate() {
		super.onCreate();
		// Create a SoundCloudHelper
		mSoundCloudHelper = new SoundCloudHelper();

		mAccountManager = AccountManager.get(this);

		// To get account from Android Account Manager
		final Account account = mSoundCloudHelper.getAccount(mAccountManager);
		mSoundCloudHelper.setToken(account);

	}

	@Override
	public Engine onCreateEngine() {
		return new WaveformEngine();
	}

	class WaveformEngine extends Engine implements
			SharedPreferences.OnSharedPreferenceChangeListener {

		// Handler and Runnable to run animated wallpaper

		private final Runnable mDrawWaveform = new Runnable() {
			public void run() {
				/*
				 * Log.d(LOG_TAG, "Runnable drawform code: " +
				 * mSoundCloudHelper.getmCodeLastError());
				 */
				switch (mSoundCloudHelper.getmCodeLastError()) {
				case SoundCloudHelper.CODES.OK:
					drawFrame();
					break;
				case SoundCloudHelper.CODES.NO_CONNECTION:
					// TODO check internet connection
					if (mAlreadyConnected) {
						drawFrame();
						mAlreadyConnected = false;
					}
					break;

				default:
					drawFrame();
					break;
				}

			}
		};

		public WaveformEngine() {
			final IntentFilter intentFilter = new IntentFilter();
			intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
			mChecckInternetConnection = new checkInternetConnection(mHandler,
					mDrawWaveform);
			registerReceiver(mChecckInternetConnection, intentFilter);
		}

		@Override
		public SurfaceHolder getSurfaceHolder() {
			return super.getSurfaceHolder();

		}

		@Override
		public void onCreate(SurfaceHolder surfaceHolder) {
			super.onCreate(surfaceHolder);

			// For Souncloud jar library and Honeycomb strictmode error
			if (android.os.Build.VERSION.SDK_INT > 9) {
				StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
						.permitAll().build();
				StrictMode.setThreadPolicy(policy);
			}

			// Get sharedPreferences for user & pass
			mSharedPreferences = PreferenceManager
					.getDefaultSharedPreferences(getApplicationContext());

			// Set a gestureDetector for doubleTap
			gestureDetector = new GestureDetector(getApplicationContext(),
					new GestureListener());
			setTouchEventsEnabled(true);

			mHandler.post(mDrawWaveform);
		}

		@Override
		public void onDestroy() {
			super.onDestroy();
			mHandler.removeCallbacks(mDrawWaveform);
			unregisterReceiver(mChecckInternetConnection);
		}

		@Override
		public void onTouchEvent(MotionEvent event) {
			// Log.d(LOG_TAG, "TouchEvent() MotionEvent " + event.getAction());
			gestureDetector.onTouchEvent(event);

			// mHandler.post(mDrawWaveform);
		}

		private class GestureListener extends
				GestureDetector.SimpleOnGestureListener {

			@Override
			public boolean onDown(MotionEvent e) {
				return true;
			}

			// event when double tap occurs
			@Override
			public boolean onDoubleTap(MotionEvent e) {
				if (mSong != null) {
					if (!mSong.getPermaLink().isEmpty()) {
						final Intent intent = new Intent(Intent.ACTION_VIEW,
								Uri.parse(mSong.getPermaLink()));
						intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						getApplication().startActivity(intent);
					}
				} else if (mSoundCloudHelper.getmCodeLastError() == SoundCloudHelper.CODES.INVALID_USER
						|| mSoundCloudHelper.getmCodeLastError() == SoundCloudHelper.CODES.NO_USER_PASS) {

					final Intent intent = new Intent(getBaseContext(),
							WallpaperSettings.class);

					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					getApplication().startActivity(intent);
				}
				return true;
			}
		}

		// These methods are relevant to draw the live wallpaper
		@Override
		public void onVisibilityChanged(boolean visible) {
			// Log.d(LOG_TAG, "onVisibilityChanged()");
			mVisible = visible;
			if (mVisible) {
				// drawFrame();
				mHandler.post(mDrawWaveform);
			} else {
				mHandler.removeCallbacks(mDrawWaveform);
			}
		}

		@Override
		public void onSurfaceChanged(SurfaceHolder holder, int format,
				int width, int height) {
			// Log.d(LOG_TAG, "onSurfaceChanged() ");

			super.onSurfaceChanged(holder, format, width, height);

			// TODO Check the position

			mHandler.post(mDrawWaveform);
			// drawFrame();
		}

		@Override
		public void onOffsetsChanged(float xOffset, float yOffset,
				float xOffsetStep, float yOffsetStep, int xPixelOffset,
				int yPixelOffset) {
			// Log.d(LOG_TAG, "onOffsetsChanged() ");
			mOffset = xOffset;
			mHandler.post(mDrawWaveform);
		}

		/**
		 * Draw a frame of live wallpaper
		 */
		void drawFrame() {
			// Log.d(LOG_TAG, "Start drawFrame()");

			// Get the frame to draw something
			final SurfaceHolder surfaceHolder = getSurfaceHolder();
			final Rect frame = surfaceHolder.getSurfaceFrame();
			final long currentTimeMillis = System.currentTimeMillis();
			Canvas canvas = null;

			/*
			 * Log.d(LOG_TAG, "previousTime " + timeInMillisPreviousLoad +
			 * ", current : " + currentTimeMillis + " difference: " +
			 * (currentTimeMillis - timeInMillisPreviousLoad) + " check if > " +
			 * millisToReload);
			 */
			if (timeInMillisPreviousLoad == Long.MIN_VALUE
					|| currentTimeMillis - timeInMillisPreviousLoad > MILLIS_TO_RELOAD) {

				// Create the canvas
				try {
					// Lock the canvas to draw it
					canvas = surfaceHolder.lockCanvas();

					if (canvas != null) {
						drawWaveform(canvas);
					}

					// Draw the canvas
				} finally {
					if (canvas != null) {
						surfaceHolder.unlockCanvasAndPost(canvas);
						timeInMillisPreviousLoad = currentTimeMillis;
					}
				}

				mHandler.removeCallbacks(mDrawWaveform);

				if (mVisible) {
					Log.d(LOG_TAG, "reload new at " + MILLIS_TO_RELOAD + "ms");

					mHandler.postDelayed(mDrawWaveform, MILLIS_TO_RELOAD);

				}
			}

		}

		void drawWaveform(Canvas canvas) {
			final SurfaceHolder surfaceHolder = getSurfaceHolder();
			final Rect frame = surfaceHolder.getSurfaceFrame();
			final int frameWidth = frame.width();
			final int framHeight = frame.height();
			String username = mSharedPreferences.getString(
					WallpaperSettings.USERNAME_KEY, "");
			String password = mSharedPreferences.getString(
					WallpaperSettings.PASSWORD_KEY, "");

			Log.d(LOG_TAG, "Start drawWaveform()");
			// canvas.save();

			Bitmap b = null;
			Paint paint = new Paint();
			paint.setAntiAlias(true);
			paint.setFilterBitmap(true);

			if (mSoundCloudHelper.login(username, password)) {
				String jsonFavorites = mSoundCloudHelper.getUserFavorites();
				mSong = mSoundCloudHelper.getRandomSong(jsonFavorites);

				if (mSong.getWaveformUrl() != null) {
					URLConnectionImageDownloader urlConnectionImageDownloader = new URLConnectionImageDownloader(
							1000, 1000);
					try {
						Log.d(LOG_TAG,
								"url of waveform " + mSong.getWaveformUrl());
						URI uriWaveform = new URI(mSong.getWaveformUrl());
						b = BitmapFactory
								.decodeStream(urlConnectionImageDownloader
										.getStream(uriWaveform));
					} catch (IOException e) {
						Log.e(LOG_TAG, "drawWaveForm() " + e.getMessage());
					} catch (URISyntaxException e) {
						Log.e(LOG_TAG, "drawWaveForm() " + e.getMessage());
					}

					if (b != null) {

						paint.setColor(Color.BLACK);
						b = scaleBitmap(b, frameWidth, framHeight / 2);
						// Clear the background

						canvas.drawColor(Color.rgb(239, 239, 239), Mode.CLEAR);
						canvas.drawColor(Color.rgb(239, 239, 239));
						canvas.save();
						// draw the waveform
						canvas.drawRect(0, framHeight / 4, frameWidth,
								(frame.height() / 2) + framHeight / 4, paint);
						canvas.drawBitmap(b, 0, (framHeight / 4), null);
						canvas.restore();

						// TODO Modify to improve text style

						paint.setTextSize(22);

						float mt = paint.measureText(mSong.getTitle());

						int cx = (int) ((frameWidth / 2) - (mt / 2));
						int cy = framHeight / 2;
						paint.setColor(Color.GRAY);

						canvas.drawText(mSong.getTitle(), cx, cy, paint);

						// canvas.save();
						canvas.scale(1f, -0.5f, cx, cy);
						paint.setColor(Color.DKGRAY);
						canvas.drawText(mSong.getTitle(), cx, cy, paint);

						// canvas.restore();

					}

				}

			} else {
				Log.e(LOG_TAG, "Error! login function");
				String error = "";
				String secondMsg = "";
				mSong = null;

				switch (mSoundCloudHelper.getmCodeLastError()) {
				case SoundCloudHelper.CODES.INVALID_USER:
					error = getResources().getString(
							R.string.error_invalid_user_pass);
					secondMsg = getResources().getString(
							R.string.doubletap_to_preferences);
					break;
				case SoundCloudHelper.CODES.NO_CONNECTION:
					error = getResources().getString(
							R.string.error_no_api_connection);
					break;
				case SoundCloudHelper.CODES.NO_USER_PASS:
					error = getResources().getString(
							R.string.error_empty_user_pass);
					secondMsg = getResources().getString(
							R.string.doubletap_to_preferences);
					break;
				default:
					error = "ERROR CODE : "
							+ mSoundCloudHelper.getmCodeLastError();
					break;
				}

				b = BitmapFactory.decodeResource(getResources(),
						R.drawable.soundcloud_logo);

				canvas.drawColor(Color.rgb(239, 239, 239), Mode.CLEAR);
				canvas.drawColor(Color.rgb(239, 239, 239));
				canvas.save();
				b = scaleBitmap(b, frameWidth, framHeight / 2);
				canvas.drawBitmap(b, 0, (framHeight / 4), null);
				paint.setColor(Color.DKGRAY);
				canvas.restore();

				paint.setTextSize(22);
				float mt = paint.measureText(error);
				int cx = (int) ((frameWidth / 2) - (mt / 2));
				int cy = framHeight / 2;
				canvas.drawText(error, cx, 100, paint);

				if (!secondMsg.isEmpty()) {
					paint.setColor(Color.GRAY);
					mt = paint.measureText(secondMsg);
					cx = (int) ((frameWidth / 2) - (mt / 2));
					cy = framHeight / 2;
					canvas.drawText(secondMsg, cx, 150, paint);
				}
			}

			Log.d(LOG_TAG, "Finish! drawWaveform()");

		}

		/**
		 * Change the size of a Bitmap.
		 * 
		 * @param bitmapToScale
		 *            Bitmap
		 * @param newWidth
		 *            width
		 * @param newHeight
		 *            height
		 * @return Bitmap
		 */
		public Bitmap scaleBitmap(Bitmap bitmapToScale, float newWidth,
				float newHeight) {
			if (bitmapToScale == null)
				return null;
			// get the original width and height
			int width = bitmapToScale.getWidth();
			int height = bitmapToScale.getHeight();
			// create a matrix for the manipulation
			Matrix matrix = new Matrix();

			// resize the bit map
			matrix.postScale(newWidth / width, newHeight / height);

			// recreate the new Bitmap and set it back
			return Bitmap.createBitmap(bitmapToScale, 0, 0,
					bitmapToScale.getWidth(), bitmapToScale.getHeight(),
					matrix, true);
		}

		public void onSharedPreferenceChanged(
				SharedPreferences sharedPreferences, String key) {
			Log.d(Wallpaper.LOG_TAG, "Wallpaper.onSharedPreferencesChanged() "
					+ key);

			mHandler.post(mDrawWaveform);

		}

	}

	public class checkInternetConnection extends BroadcastReceiver {

		private Handler mHandler;
		private Runnable mDrawWaveform;

		public checkInternetConnection(Handler handler, Runnable drawWaveform) {
			mHandler = handler;
			mDrawWaveform = drawWaveform;
		}

		@Override
		public void onReceive(Context context, Intent intent) {
			String username = mSharedPreferences.getString(
					WallpaperSettings.USERNAME_KEY, "");
			String password = mSharedPreferences.getString(
					WallpaperSettings.PASSWORD_KEY, "");

			ConnectivityManager connectivityManager = (ConnectivityManager) context
					.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo activeNetInfo = connectivityManager
					.getActiveNetworkInfo();
			NetworkInfo mobNetInfo = connectivityManager
					.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

			if (activeNetInfo != null) {
				Log.d(LOG_TAG, "Active connection redraw the wallpaper");
				mSoundCloudHelper.login(username, password);
				mHandler.post(mDrawWaveform);
				mAlreadyConnected = true;

			}
			if (mobNetInfo != null) {
				Log.d(LOG_TAG,
						"Inactive connection redraw the wallpaper");
				mSoundCloudHelper.login(username, password);
				//mHandler.removeCallbacks(mDrawWaveform);
				mHandler.post(mDrawWaveform);
				
			}

		}

	}
}
