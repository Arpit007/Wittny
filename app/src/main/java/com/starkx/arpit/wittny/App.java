package com.starkx.arpit.wittny;

import android.app.Application;
import android.support.v7.app.AppCompatDelegate;

import com.simperium.Simperium;
import com.simperium.client.Bucket;
import com.simperium.client.BucketNameInvalid;
import com.starkx.arpit.wittny.models.Note;
import com.starkx.arpit.wittny.models.NoteCountIndexer;
import com.starkx.arpit.wittny.models.Tag;

public class App extends Application {

	// log tag
	public static final String TAG = "App";
	public static final String SIMPERIUM_APP_ID = "history-analyst-dad";
	public static final String SIMPERIUM_APP_KEY = "dccacc59bbef4982a15abaeafaf7bc8a";
	public static final int INTENT_PREFERENCES = 1;
	public static final int INTENT_EDIT_NOTE = 2;
	public static final String DELETED_NOTE_ID = "deletedNoteId";
	private Simperium mSimperium;
	private Bucket<Note> mNotesBucket;

	public void onCreate() {
		super.onCreate();

		mSimperium = Simperium.newClient(
				SIMPERIUM_APP_ID,
				SIMPERIUM_APP_KEY,
				this
		);

		try {
			mNotesBucket = mSimperium.bucket(new Note.Schema());
			Tag.Schema tagSchema = new Tag.Schema();
			tagSchema.addIndex(new NoteCountIndexer(mNotesBucket));
		}
		catch (BucketNameInvalid e) {
			throw new RuntimeException("Could not create bucket", e);
		}

		AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
	}

	public Simperium getSimperium() {
		return mSimperium;
	}

	public Bucket<Note> getNotesBucket() {
		return mNotesBucket;
	}
}
