package es.olidroide.android.SoundCloudWallpaper;

import java.io.IOException;
import java.util.Random;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.util.Log;

import com.soundcloud.api.ApiWrapper;
import com.soundcloud.api.Env;
import com.soundcloud.api.Http;
import com.soundcloud.api.Request;
import com.soundcloud.api.Token;

public class SoundCloudHelper {
	public static final String SC_ACCOUNT_TYPE = "com.soundcloud.android.account";
	public static final String ACCESS_TOKEN = "access_token";

	//Put your private ID. Got it at http://soundcloud.com/you/apps
	public static final String PRIVATE_CLIENT_ID_API = "";
	public static final String PRIVATE_CLIENT_SECRET_API = "";

	private ApiWrapper mApiWrapper = null;
	private boolean mUserAlreadyLogin = false;
	private AccountManager mAccountManager = null;
	private Token mToken;
	private int mCodeLastError = CODES.OK;

	public int getmCodeLastError() {
		return mCodeLastError;
	}

	public class CODES {
		public static final int OK = 0;
		public static final int KO = -1;
		public static final int INVALID_USER = 401;
		public static final int NO_CONNECTION = 100;
		public static final int NO_USER_PASS = 200;
	}

	public class Song {
		private String waveformUrl;
		private String permaLink;
		private String title;

		public Song(String waveform, String permaLinkRec, String titleRec) {
			waveformUrl = waveform;
			permaLink = permaLinkRec;
			title = titleRec;
		}

		public String getWaveformUrl() {
			return waveformUrl;
		}

		public String getPermaLink() {
			return permaLink;
		}

		public String getTitle() {
			return title;
		}

		public void clear() {
			waveformUrl = null;
			permaLink = null;
			title = null;
		}

	}

	public SoundCloudHelper() {
		mApiWrapper = new ApiWrapper(PRIVATE_CLIENT_ID_API,
				PRIVATE_CLIENT_SECRET_API, null, null, Env.LIVE);
		
	}

	public boolean login(String user, String pass) {
		Boolean loginSuccess = false;
		mCodeLastError = CODES.KO;

		if (!user.isEmpty() && !pass.isEmpty()) {
			try {
				mApiWrapper.login(user, pass);
				loginSuccess = true;
				mCodeLastError = CODES.OK;
			} catch (IOException e) {
				String errorMsg = e.getMessage();

				Log.e(Wallpaper.LOG_TAG, "login error! " + errorMsg);

				if (errorMsg.contains("invalid_grant")) {
					mCodeLastError = CODES.INVALID_USER;
				} else if (errorMsg.contains("timed out")||
						errorMsg.contains("refused") ||
						errorMsg.contains("api.soundcloud.com")) {
					mCodeLastError = CODES.NO_CONNECTION;
				}

				if (user.isEmpty() || pass.isEmpty()) {
					mCodeLastError = CODES.NO_USER_PASS;
				}
			}
		}else{
			mCodeLastError = CODES.NO_USER_PASS;
		}

		return loginSuccess;
	}

	public String getUserFavorites() {
		String jsonFavorites = "";
		HttpResponse httpResponse;
		try {
			httpResponse = mApiWrapper.get(Request.to("/me/favorites"));

			if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {

				jsonFavorites = Http.formatJSON(Http.getString(httpResponse));

			}
		} catch (IOException e) {
			Log.e(Wallpaper.LOG_TAG,
					"getUserFavorites error! " + e.getMessage());
		}

		return jsonFavorites;
	}

	public Song getRandomSong(String jsonFavorites) {
		Song song = null;
		String urlWaveform = "";
		String titleSong = "";
		String permaLink = "";
		JSONArray jsonArray = null;
		JSONObject jsonObject = null;
		int randomWaveform = 0;

		// Get random value to get new waveform
		Random random = new Random();

		try {
			jsonArray = new JSONArray(jsonFavorites);
			randomWaveform = random.nextInt(jsonArray.length() - 1);
			jsonObject = jsonArray.getJSONObject(randomWaveform);
			urlWaveform = jsonObject.getString("waveform_url");
			permaLink = jsonObject.getString("permalink_url");
			titleSong = jsonObject.getString("title");
		} catch (JSONException e) {
			Log.e(Wallpaper.LOG_TAG, "Error to parse JSON " + e.getMessage());
		}

		song = new Song(urlWaveform, permaLink, titleSong);

		Log.d(Wallpaper.LOG_TAG, "soundCloudAPI() random n¼ " + randomWaveform
				+ " waveform url : " + urlWaveform);

		return song;
	}

	public Account getAccount(AccountManager accountManager) {
		mAccountManager = accountManager;
		Account[] accounts = mAccountManager.getAccountsByType(SC_ACCOUNT_TYPE);
		if (accounts.length > 0) {
			return accounts[0];
		} else {
			return null;
		}
	}

	public void setToken(Account account) {
		if (account != null) {
			new Thread(mGetToken).start();
		} else {
			// addAccount();
		}
	}

	private final Runnable mGetToken = new Runnable() {
		public void run() {
			mToken = getToken(getAccount(mAccountManager));
			if (mToken != null) {
				Log.d(Wallpaper.LOG_TAG, "Token received from AccountManager ");
			} else {
				Log.w(Wallpaper.LOG_TAG, "error couldnt get the token");
				mToken = null;
			}
		}
	};

	private Token getToken(Account account) {
		try {
			String access = mAccountManager.blockingGetAuthToken(account,
					ACCESS_TOKEN, false);
			return new Token(access, null, Token.SCOPE_NON_EXPIRING);
		} catch (OperationCanceledException e) {
			Log.w(Wallpaper.LOG_TAG, "error", e);
			return null;
		} catch (IOException e) {
			Log.w(Wallpaper.LOG_TAG, "error", e);
			return null;
		} catch (AuthenticatorException e) {
			Log.w(Wallpaper.LOG_TAG, "error", e);
			return null;
		}
	}

}
