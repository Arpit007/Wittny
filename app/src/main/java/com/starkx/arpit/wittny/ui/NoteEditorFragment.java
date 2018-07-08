package com.starkx.arpit.wittny.ui;

import android.Manifest;
import android.app.LauncherActivity;
import android.app.ProgressDialog;
import android.app.Service;
import android.app.TabActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.Fragment;
import android.support.v4.app.ShareCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatEditText;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;

import com.simperium.client.Bucket;
import com.simperium.client.BucketObjectMissingException;
import com.starkx.arpit.wittny.App;
import com.starkx.arpit.wittny.R;
import com.starkx.arpit.wittny.models.Note;
import com.starkx.arpit.wittny.src.GTranslate;
import com.starkx.arpit.wittny.src.GVision;
import com.starkx.arpit.wittny.src.Rephrase;
import com.starkx.arpit.wittny.src.Spell;
import com.starkx.arpit.wittny.utils.DrawableUtils;
import com.starkx.arpit.wittny.utils.PermissionUtils;

import java.io.File;
import java.util.Calendar;
import java.util.Locale;

import static android.app.Activity.RESULT_OK;

public class NoteEditorFragment extends Fragment implements TextWatcher, TextToSpeech.OnInitListener  {
	public static final String ARG_ITEM_ID = "item_id";
	public static final String ARG_NEW_NOTE = "new_note";
	static public final String ARG_MATCH_OFFSETS = "match_offsets";
	static public final String ARG_MARKDOWN_ENABLED = "markdown_enabled";
	private static final int AUTOSAVE_DELAY_MILLIS = 2000;
	public static final int GALLERY_PERMISSIONS_REQUEST = 0;
	public static final int GALLERY_IMAGE_REQUEST = 1;
	public static final int CAMERA_PERMISSIONS_REQUEST = 2;
	public static final int CAMERA_IMAGE_REQUEST = 3;
	private static final String TAG = NoteEditorFragment.class.getSimpleName();
	public static final String FILE_NAME = "temp.jpg";

	private TextToSpeech tts;
	private ProgressDialog dialog;
	private Note mNote;
	private final Runnable mAutoSaveRunnable = new Runnable() {
		@Override
		public void run() {
			saveAndSyncNote();
		}
	};
	private AppCompatEditText mContentEditText;
	private Handler mAutoSaveHandler;
	private LinearLayout mPlaceholderView;
	private boolean mIsNewNote;

	public NoteEditorFragment() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mAutoSaveHandler = new Handler();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		setHasOptionsMenu(true);
		View rootView = inflater.inflate(R.layout.fragment_note_editor, container, false);
		mContentEditText = rootView.findViewById(R.id.note_content);
		tts = new TextToSpeech(getContext(), this);
		mPlaceholderView = rootView.findViewById(R.id.placeholder);

		// Load note if we were passed a note Id
		Bundle arguments = getArguments();
		if (arguments != null && arguments.containsKey(ARG_ITEM_ID)) {
			String key = arguments.getString(ARG_ITEM_ID);
			new loadNoteTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, key);
			setIsNewNote(getArguments().getBoolean(ARG_NEW_NOTE, false));
		}

		return rootView;
	}

	@Override
	public void onDestroy() {
		// Don't forget to shutdown tts!
		if (tts != null) {
			tts.stop();
			tts.shutdown();
		}
		super.onDestroy();
	}

	@Override
	public void onInit(int status) {
		if (status == TextToSpeech.SUCCESS) {

			int result = tts.setLanguage(Locale.US);

			if (result == TextToSpeech.LANG_MISSING_DATA
					|| result == TextToSpeech.LANG_NOT_SUPPORTED) {
				Log.e("TTS", "This Language is not supported");
			}
		} else {
			Log.e("TTS", "Initilization Failed!");
		}

	}

	private void speakOut() {

		String text = getNoteContentString();

		tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
	}

	@Override
	public void onResume() {
		super.onResume();

		if (mContentEditText != null) {
			mContentEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		}
	}

	@Override
	public void onPause() {
		// Hide soft keyboard if it is showing...
		if (getActivity() != null) {
			InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
			if (inputMethodManager != null) {
				inputMethodManager.hideSoftInputFromWindow(mContentEditText.getWindowToken(), 0);
			}
		}

		// Delete the note if it is new and has empty fields
		if (mNote != null && mIsNewNote && noteIsEmpty()) {
			mNote.delete();
		}
		else {
			saveNote();
		}

		if (mAutoSaveHandler != null) {
			mAutoSaveHandler.removeCallbacks(mAutoSaveRunnable);
		}

		super.onPause();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		if (!isAdded()) {
			return;
		}

		inflater.inflate(R.menu.note_editor, menu);

		if (mNote != null) {

			MenuItem trashItem = menu.findItem(R.id.menu_delete).setTitle(R.string.restore);

			if (mNote.isDeleted()) {
				trashItem.setTitle(R.string.restore);
				trashItem.setIcon(R.drawable.ic_trash_restore_24dp);
			}
			else {
				trashItem.setTitle(R.string.delete);
				trashItem.setIcon(R.drawable.ic_trash_24dp);
			}
		}

		DrawableUtils.tintMenuWithAttribute(getActivity(), menu, R.attr.actionBarTextColor);

		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_delete:
				if (!isAdded()) {
					return false;
				}
				deleteNote();
				return true;
			case android.R.id.home:
				if (!isAdded()) {
					return false;
				}
				getActivity().finish();
				return true;
			case R.id.menu_pic:
				getImage();
				return true;
			case R.id.menu_translate:
				translate();
				return true;
			case R.id.menu_spell:
				spell();
				return true;
			case R.id.menu_share:
				share();
				return true;
			case R.id.menu_rephrase:
				rephrase();
				return true;
			case R.id.menu_speak:
				speakOut();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void deleteNote() {
		if (mNote != null) {
			mNote.setDeleted(!mNote.isDeleted());
			mNote.setModificationDate(Calendar.getInstance());
			mNote.save();
			Intent resultIntent = new Intent();
			if (mNote.isDeleted()) {
				resultIntent.putExtra(App.DELETED_NOTE_ID, mNote.getSimperiumKey());
			}
			getActivity().setResult(RESULT_OK, resultIntent);

		}
		getActivity().finish();
	}

	private boolean noteIsEmpty() {
		return ( getNoteContentString().trim().length() == 0 );
	}

	public void setNote(String noteID) {
		if (mAutoSaveHandler != null) {
			mAutoSaveHandler.removeCallbacks(mAutoSaveRunnable);
		}

		mPlaceholderView.setVisibility(View.GONE);

		// If we have a note already (on a tablet in landscape), save the note.
		if (mNote != null) {
			if (mIsNewNote && noteIsEmpty()) {
				mNote.delete();
			}
			else if (mNote != null) {
				saveNote();
			}
		}

		new loadNoteTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, noteID);
	}

	private void refreshContent(boolean isNoteUpdate) {
		if (mNote != null) {
			// Restore the cursor position if possible.

			int cursorPosition = newCursorLocation(mNote.getContent(), getNoteContentString(), mContentEditText.getSelectionEnd());

			mContentEditText.setText(mNote.getContent());

			if (isNoteUpdate) {
				// Save the note so any local changes get synced
				mNote.save();

				if (mContentEditText.hasFocus() && cursorPosition != mContentEditText.getSelectionEnd()) {
					mContentEditText.setSelection(cursorPosition);
				}
			}

			afterTextChanged(mContentEditText.getText());
		}
	}

	private int newCursorLocation(String newText, String oldText, int cursorLocation) {
		// Ported from the iOS app :)
		// Cases:
		// 0. All text after cursor (and possibly more) was removed ==> put cursor at end
		// 1. Text was added after the cursor ==> no change
		// 2. Text was added before the cursor ==> location advances
		// 3. Text was removed after the cursor ==> no change
		// 4. Text was removed before the cursor ==> location retreats
		// 5. Text was added/removed on both sides of the cursor ==> not handled

		int newCursorLocation = cursorLocation;

		int deltaLength = newText.length() - oldText.length();

		// Case 0
		if (newText.length() < cursorLocation) {
			return newText.length();
		}

		boolean beforeCursorMatches = false;
		boolean afterCursorMatches = false;

		try {
			beforeCursorMatches = oldText.substring(0, cursorLocation).equals(newText.substring(0, cursorLocation));
			afterCursorMatches = oldText.substring(cursorLocation, oldText.length()).equals(newText.substring(cursorLocation + deltaLength, newText.length()));
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		// Cases 2 and 4
		if (!beforeCursorMatches && afterCursorMatches) {
			newCursorLocation += deltaLength;
		}

		// Cases 1, 3 and 5 have no change
		return newCursorLocation;
	}

	@Override
	public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
		// Unused
	}

	@Override
	public void afterTextChanged(Editable editable) {
		setTitleSpan(editable);

		float lineSpacingExtra = mContentEditText.getLineSpacingExtra();
		float lineSpacingMultiplier = mContentEditText.getLineSpacingMultiplier();
		mContentEditText.setLineSpacing(0.0f, 1.0f);
		mContentEditText.setLineSpacing(lineSpacingExtra, lineSpacingMultiplier);
	}

	@Override
	public void onTextChanged(CharSequence charSequence, int start, int before, int count) {

		// When text changes, start timer that will fire after AUTOSAVE_DELAY_MILLIS passes
		if (mAutoSaveHandler != null) {
			mAutoSaveHandler.removeCallbacks(mAutoSaveRunnable);
			mAutoSaveHandler.postDelayed(mAutoSaveRunnable, AUTOSAVE_DELAY_MILLIS);
		}
	}

	private void setTitleSpan(Editable editable) {
		// Set the note title to be a larger size
		// Remove any existing size spans
		RelativeSizeSpan spans[] = editable.getSpans(0, editable.length(), RelativeSizeSpan.class);
		for (RelativeSizeSpan span : spans) {
			editable.removeSpan(span);
		}
		int newLinePosition = getNoteContentString().indexOf("\n");
		if (newLinePosition == 0) {
			return;
		}
		editable.setSpan(new RelativeSizeSpan(1.227f), 0, ( newLinePosition > 0 ) ? newLinePosition : editable.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
	}

	private void saveAndSyncNote() {
		if (mNote == null) {
			return;
		}

		new saveNoteTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	public void setIsNewNote(boolean isNewNote) {
		this.mIsNewNote = isNewNote;
	}

	private String getNoteContentString() {
		if (mContentEditText == null || mContentEditText.getText() == null) {
			return "";
		}
		else {
			return mContentEditText.getText().toString();
		}
	}

	protected void saveNote() {
		if (mNote == null) {
			return;
		}

		String content = getNoteContentString();
		if (mNote.hasChanges(content, "")) {
			mNote.setContent(content);
			mNote.setModificationDate(Calendar.getInstance());
			mNote.save();
		}
	}

	private class loadNoteTask extends AsyncTask<String, Void, Void> {

		@Override
		protected void onPreExecute() {
			mContentEditText.removeTextChangedListener(NoteEditorFragment.this);
		}

		@Override
		protected Void doInBackground(String... args) {
			if (getActivity() == null) {
				return null;
			}

			String noteID = args[0];
			App application = (App) getActivity().getApplication();
			Bucket<Note> notesBucket = application.getNotesBucket();
			try {
				mNote = notesBucket.get(noteID);
				// Set the current note in NotesActivity when on a tablet
				if (getActivity() instanceof NotesActivity) {
					( (NotesActivity) getActivity() ).setCurrentNote(mNote);
				}
			}
			catch (BucketObjectMissingException e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void nada) {
			if (getActivity() == null || getActivity().isFinishing()) {
				return;
			}
			refreshContent(false);
			mContentEditText.addTextChangedListener(NoteEditorFragment.this);
			if (mNote != null && mNote.getContent().isEmpty()) {
				// Show soft keyboard
				mContentEditText.requestFocus();
				new Handler().postDelayed(new Runnable() {
					@Override
					public void run() {
						InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
						if (inputMethodManager != null) {
							inputMethodManager.showSoftInput(mContentEditText, 0);
						}
					}
				}, 100);

			}

			getActivity().invalidateOptionsMenu();
		}
	}

	private class saveNoteTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... args) {
			saveNote();
			return null;
		}
	}

	private void getImage() {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setMessage(R.string.dialog_select_prompt)
				.setPositiveButton(R.string.dialog_select_gallery, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						startGalleryChooser();
					}
				})
				.setNegativeButton(R.string.dialog_select_camera, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						startCamera();
					}
				});
		builder.create().show();
	}

	public void startGalleryChooser() {
		if (PermissionUtils.requestPermission(getActivity(), GALLERY_PERMISSIONS_REQUEST, Manifest.permission.READ_EXTERNAL_STORAGE)) {
			Intent intent = new Intent();
			intent.setType("image/*");
			intent.setAction(Intent.ACTION_GET_CONTENT);
			startActivityForResult(Intent.createChooser(intent, "Select a photo"),
					GALLERY_IMAGE_REQUEST);
		}
	}

	public void startCamera() {
		if (PermissionUtils.requestPermission(
				getActivity(),
				CAMERA_PERMISSIONS_REQUEST,
				Manifest.permission.READ_EXTERNAL_STORAGE,
				Manifest.permission.CAMERA)) {
			Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
			Uri photoUri = FileProvider.getUriForFile(getActivity(), getActivity().getPackageName() + ".provider", getCameraFile());
			intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
			intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			startActivityForResult(intent, CAMERA_IMAGE_REQUEST);
		}
	}

	public File getCameraFile() {
		File dir = getActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
		return new File(dir, FILE_NAME);
	}

	public void Vision(Uri uri) {
		if (dialog == null) {
			dialog = new ProgressDialog(getActivity());
		}
		dialog.setMessage("Please Wait");
		dialog.setCancelable(false);
		dialog.show();
		GVision vision = new GVision(new GVision.Callback() {
			@Override
			public void onResponse(String response) {
				dialog.dismiss();

				String prev = getNoteContentString();
				prev += "\n" + response;
				mNote.setContent(prev);
				refreshContent(true);
			}
		});
		vision.convert(getActivity(), uri);
	}

	public void translate() {
		String sourceText = getNoteContentString();
		GTranslate translate = new GTranslate(getActivity(), new GTranslate.Callback() {
			@Override
			public void onResponse(final String response) {
				getActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mNote.setContent(response);
						refreshContent(true);
					}
				});
			}
		});
		translate.convert(sourceText);
	}

	public void spell() {
		if (dialog == null) {
			dialog = new ProgressDialog(getActivity());
		}
		dialog.setMessage("Please Wait");
		dialog.setCancelable(false);
		dialog.show();
		String sourceText = getNoteContentString();
		Spell spell = new Spell(new Spell.Callback() {
			@Override
			public void onResponse(String response) {
				dialog.dismiss();
				if (response.isEmpty()) {
					return;
				}
				mNote.setContent(response);
				refreshContent(true);
			}
		});
		spell.convert(getActivity(), sourceText);
	}

	public void rephrase() {
		if (dialog == null) {
			dialog = new ProgressDialog(getActivity());
		}
		dialog.setMessage("Please Wait");
		dialog.setCancelable(false);
		dialog.show();
		String sourceText = getNoteContentString();
		Rephrase rephrase = new Rephrase(new Rephrase.Callback() {
			@Override
			public void onResponse(String response) {
				dialog.dismiss();
				if (response.isEmpty()) {
					return;
				}
				mNote.setContent(response);
				refreshContent(true);
			}
		});
		rephrase.convert(getActivity(), sourceText);
	}

	public void share() {
		ShareCompat.IntentBuilder
				.from(getActivity())
				.setText(getNoteContentString())
				.setType("text/plain")
				.setChooserTitle("Share Wittny Notes")
				.startChooser();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == GALLERY_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
			Vision(data.getData());
		}
		else if (requestCode == CAMERA_IMAGE_REQUEST && resultCode == RESULT_OK) {
			Uri photoUri = FileProvider.getUriForFile(getActivity(), getActivity().getPackageName() + ".provider", getCameraFile());
			Vision(photoUri);
		}
	}
}