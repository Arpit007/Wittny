package com.starkx.arpit.wittny.ui;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ListFragment;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.util.SparseBooleanArray;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.simperium.client.Bucket;
import com.simperium.client.Query;
import com.simperium.client.Query.SortType;
import com.starkx.arpit.wittny.App;
import com.starkx.arpit.wittny.R;
import com.starkx.arpit.wittny.models.Note;
import com.starkx.arpit.wittny.utils.DrawableUtils;
import com.starkx.arpit.wittny.utils.HtmlCompat;
import com.starkx.arpit.wittny.utils.SearchSnippetFormatter;
import com.starkx.arpit.wittny.utils.SearchTokenizer;
import com.starkx.arpit.wittny.utils.TextHighlighter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.starkx.arpit.wittny.models.Note.TAGS_PROPERTY;

public class NoteListFragment extends ListFragment implements AdapterView.OnItemLongClickListener, AbsListView.MultiChoiceModeListener {

	private static final String STATE_ACTIVATED_POSITION = "activated_position";
	private static Callbacks sCallbacks = new Callbacks() {
		@Override
		public void onNoteSelected(String noteID, int position, boolean isNew, String matchOffsets, boolean isMarkdownEnabled) {
		}
	};
	protected NotesCursorAdapter mNotesAdapter;
	protected String mSearchString;
	private ActionMode mActionMode;
	private View mRootView;
	private TextView mEmptyListTextView;
	private FloatingActionButton mFloatingActionButton;
	private int mNumPreviewLines;
	private String mSelectedNoteId;
	private refreshListTask mRefreshListTask;
	private int mTitleFontSize;
	private int mPreviewFontSize;
	/**
	 * The fragment's current callback object, which is notified of list item
	 * clicks.
	 */
	private Callbacks mCallbacks = sCallbacks;
	private int mActivatedPosition = ListView.INVALID_POSITION;

	public NoteListFragment() {
	}

	public void setEmptyListViewClickable(boolean isClickable) {
		if (mEmptyListTextView != null) {
			mEmptyListTextView.setClickable(isClickable);
		}
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long l) {
		getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
		getListView().setItemChecked(position, true);
		if (mActionMode == null) {
			getActivity().startActionMode(this);
		}
		return true;
	}

	@Override
	public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
		MenuInflater inflater = actionMode.getMenuInflater();
		inflater.inflate(R.menu.bulk_edit, menu);
		DrawableUtils.tintMenuWithAttribute(getActivity(), menu, R.attr.actionModeTextColor);
		mActionMode = actionMode;
		return true;
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		return false;
	}

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		if (getListView().getCheckedItemIds().length > 0) {

			switch (item.getItemId()) {
				case R.id.menu_delete:
					new trashNotesTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
					break;
			}
		}
		return false;
	}

	@Override
	public void onDestroyActionMode(ActionMode mode) {
		mActionMode = null;
		new Handler().post(new Runnable() {
			@Override
			public void run() {
				if (getActivity() != null) {
					setActivateOnItemClick(false);
				}
			}
		});
	}

	@Override
	public void onItemCheckedStateChanged(ActionMode actionMode, int position, long id, boolean checked) {
		int checkedCount = getListView().getCheckedItemCount();
		if (checkedCount == 0) {
			actionMode.setTitle("");
		}
		else {
			actionMode.setTitle(getResources().getQuantityString(R.plurals.selected_notes, checkedCount, checkedCount));
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mNotesAdapter = new NotesCursorAdapter(getActivity().getBaseContext(), null, 0);
		setListAdapter(mNotesAdapter);
	}

	// nbradbury - load values from preferences
	protected void getPrefs() {
		mNumPreviewLines = 2;
		mPreviewFontSize = 14;
		mTitleFontSize = mPreviewFontSize + 2;
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_notes_list, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		mRootView = view.findViewById(R.id.list_root);

		LinearLayout emptyView = view.findViewById(android.R.id.empty);
		emptyView.setVisibility(View.GONE);
		mEmptyListTextView = view.findViewById(R.id.empty_message);
		mEmptyListTextView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				addNote();
			}
		});
		setEmptyListMessage("<strong>" + getString(R.string.no_notes_here) + "</strong><br />" + String.format(getString(R.string.why_not_create_one), "<u>", "</u>"));

		mFloatingActionButton = view.findViewById(R.id.fab_button);
		mFloatingActionButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				createNewNote();
			}
		});

		getListView().setOnItemLongClickListener(this);
		getListView().setMultiChoiceModeListener(this);
	}

	private void createNewNote() {
		if (!isAdded()) {
			return;
		}

		addNote();
	}

	@Override
	public void onAttach(Context activity) {
		super.onAttach(activity);

		// Activities containing this fragment must implement its callbacks.
		if (!( activity instanceof Callbacks )) {
			throw new IllegalStateException("Activity must implement fragment's callbacks.");
		}

		mCallbacks = (Callbacks) activity;
	}

	@Override
	public void onResume() {
		super.onResume();
		getPrefs();

		refreshList();
	}

	@Override
	public void onDetach() {
		super.onDetach();

		// Reset the active callbacks interface to the dummy implementation.
		mCallbacks = sCallbacks;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	public void setEmptyListMessage(String message) {
		if (mEmptyListTextView != null && message != null) {
			mEmptyListTextView.setText(HtmlCompat.fromHtml(message));
		}
	}

	@Override
	public void onListItemClick(ListView listView, View view, int position, long id) {
		if (!isAdded()) {
			return;
		}
		super.onListItemClick(listView, view, position, id);

		NoteViewHolder holder = (NoteViewHolder) view.getTag();
		String noteID = holder.getNoteId();
		if (noteID != null) {
			mCallbacks.onNoteSelected(noteID, position, false, holder.matchOffsets, false);
		}

		mActivatedPosition = position;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (mActivatedPosition != ListView.INVALID_POSITION) {
			// Serialize and persist the activated item position.
			outState.putInt(STATE_ACTIVATED_POSITION, mActivatedPosition);
		}
	}

	public View getRootView() {
		return mRootView;
	}

	/**
	 * Turns on activate-on-click mode. When this mode is on, list items will be
	 * given the 'activated' state when touched.
	 */
	public void setActivateOnItemClick(boolean activateOnItemClick) {
		// When setting CHOICE_MODE_SINGLE, ListView will automatically
		// give items the 'activated' state when touched.
		getListView().setChoiceMode(activateOnItemClick ? ListView.CHOICE_MODE_SINGLE : ListView.CHOICE_MODE_NONE);
	}

	public void setActivatedPosition(int position) {
		if (getListView() != null) {
			if (position == ListView.INVALID_POSITION) {
				getListView().setItemChecked(mActivatedPosition, false);
			}
			else {
				getListView().setItemChecked(position, true);
			}

			mActivatedPosition = position;
		}
	}

	public void setFloatingActionButtonVisible(boolean visible) {
		if (mFloatingActionButton == null) {
			return;
		}

		if (visible) {
			mFloatingActionButton.show();
		}
		else {
			mFloatingActionButton.hide();
		}
	}

	public void refreshList() {
		refreshList(false);
	}

	public void refreshList(boolean fromNav) {
		if (mRefreshListTask != null && mRefreshListTask.getStatus() != AsyncTask.Status.FINISHED) {
			mRefreshListTask.cancel(true);
		}

		mRefreshListTask = new refreshListTask();
		mRefreshListTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, fromNav);
	}

	public void refreshListFromNavSelect() {
		refreshList(true);
	}

	public Bucket.ObjectCursor<Note> queryNotes() {
		if (!isAdded()) {
			return null;
		}

		NotesActivity notesActivity = (NotesActivity) getActivity();
		Query<Note> query = notesActivity.getSelectedTag().query();

		String searchString = mSearchString;
		if (hasSearchQuery()) {
			searchString = queryTags(query, mSearchString);
		}
		if (!TextUtils.isEmpty(searchString)) {
			query.where(new Query.FullTextMatch(new SearchTokenizer(searchString)));
			query.include(new Query.FullTextOffsets("match_offsets"));
			query.include(new Query.FullTextSnippet(Note.MATCHED_TITLE_INDEX_NAME, Note.TITLE_INDEX_NAME));
			query.include(new Query.FullTextSnippet(Note.MATCHED_CONTENT_INDEX_NAME, Note.CONTENT_PROPERTY));
			query.include(Note.TITLE_INDEX_NAME, Note.CONTENT_PREVIEW_INDEX_NAME);
		}
		else {
			query.include(Note.TITLE_INDEX_NAME, Note.CONTENT_PREVIEW_INDEX_NAME);
		}

		query.include(Note.PINNED_INDEX_NAME);

		sortNoteQuery(query);

		return query.execute();
	}

	private String queryTags(Query<Note> query, String searchString) {
		Pattern pattern = Pattern.compile("tag:(.*?)( |$)");
		Matcher matcher = pattern.matcher(searchString);
		while (matcher.find()) {
			query.where(TAGS_PROPERTY, Query.ComparisonType.LIKE, matcher.group(1));
		}
		return matcher.replaceAll("");
	}

	public void addNote() {

		// Prevents jarring 'New note...' from showing in the list view when creating a new note
		NotesActivity notesActivity = (NotesActivity) getActivity();
		notesActivity.stopListeningToNotesBucket();

		// Create & save new note
		App app = (App) getActivity().getApplication();
		Bucket<Note> notesBucket = app.getNotesBucket();
		Note note = notesBucket.newObject();
		note.setCreationDate(Calendar.getInstance());
		note.setModificationDate(note.getCreationDate());

		if (notesActivity.getSelectedTag() != null && notesActivity.getSelectedTag().name != null) {
			String tagName = notesActivity.getSelectedTag().name;
			if (!tagName.equals(getString(R.string.notes)) && !tagName.equals(getString(R.string.trash))) {
				note.setTagString(tagName);
			}
		}

		note.save();

		Bundle arguments = new Bundle();
		arguments.putString(NoteEditorFragment.ARG_ITEM_ID, note.getSimperiumKey());
		arguments.putBoolean(NoteEditorFragment.ARG_NEW_NOTE, true);
		Intent editNoteIntent = new Intent(getActivity(), NoteEditorActivity.class);
		editNoteIntent.putExtras(arguments);

		getActivity().startActivityForResult(editNoteIntent, App.INTENT_EDIT_NOTE);
	}

	public void setNoteSelected(String selectedNoteID) {
		// Loop through notes and set note selected if found
		//noinspection unchecked
		Bucket.ObjectCursor<Note> cursor = (Bucket.ObjectCursor<Note>) mNotesAdapter.getCursor();
		if (cursor != null) {
			for (int i = 0; i < cursor.getCount(); i++) {
				cursor.moveToPosition(i);
				String noteKey = cursor.getSimperiumKey();
				if (noteKey != null && noteKey.equals(selectedNoteID)) {
					setActivatedPosition(i);
					return;
				}
			}
		}

		// Didn't find the note, let's try again after the cursor updates (see refreshListTask)
		mSelectedNoteId = selectedNoteID;
	}

	public void searchNotes(String searchString) {
		if (!searchString.equals(mSearchString)) {
			mSearchString = searchString;
			refreshList();
		}
	}

	/**
	 * Clear search and load all notes
	 */
	public void clearSearch() {
		if (mSearchString != null && !mSearchString.equals("")) {
			mSearchString = null;
			refreshList();
		}
	}

	public boolean hasSearchQuery() {
		return mSearchString != null && !mSearchString.equals("");
	}

	public void sortNoteQuery(Query<Note> noteQuery) {
		noteQuery.order("pinned", SortType.DESCENDING);
		noteQuery.order(Note.MODIFIED_INDEX_NAME, SortType.DESCENDING);
	}

	/**
	 * A callback interface that all activities containing this fragment must
	 * implement. This mechanism allows activities to be notified of item
	 * selections.
	 */
	public interface Callbacks {
		/**
		 * Callback for when a note has been selected.
		 */
		void onNoteSelected(String noteID, int position, boolean isNew, String matchOffsets, boolean isMarkdownEnabled);
	}

	// view holder for NotesCursorAdapter
	private static class NoteViewHolder {
		public String matchOffsets;
		TextView titleTextView;
		TextView contentTextView;
		private String mNoteId;

		public String getNoteId() {
			return mNoteId;
		}

		public void setNoteId(String noteId) {
			mNoteId = noteId;
		}
	}

	public class NotesCursorAdapter extends CursorAdapter {
		private Bucket.ObjectCursor<Note> mCursor;

		private SearchSnippetFormatter.SpanFactory mSnippetHighlighter = new TextHighlighter(getActivity(),
				R.attr.listSearchHighlightForegroundColor, R.attr.listSearchHighlightBackgroundColor);

		public NotesCursorAdapter(Context context, Bucket.ObjectCursor<Note> c, int flags) {
			super(context, c, flags);
			mCursor = c;
		}

		public void changeCursor(Bucket.ObjectCursor<Note> cursor) {
			mCursor = cursor;
			super.changeCursor(cursor);
		}

		@Override
		public Note getItem(int position) {
			mCursor.moveToPosition(position);
			return mCursor.getObject();
		}

		@Override
		public View getView(final int position, View view, ViewGroup parent) {

			final NoteViewHolder holder;
			if (view == null) {
				view = View.inflate(getActivity().getBaseContext(), R.layout.note_list_row, null);
				holder = new NoteViewHolder();
				holder.titleTextView = view.findViewById(R.id.note_title);
				holder.contentTextView = view.findViewById(R.id.note_content);
				view.setTag(holder);
			}
			else {
				holder = (NoteViewHolder) view.getTag();
			}

			if (holder.titleTextView.getTextSize() != mTitleFontSize) {
				holder.titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, mTitleFontSize);
				holder.contentTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, mPreviewFontSize);
			}

			if (position == getListView().getCheckedItemPosition()) {
				view.setActivated(true);
			}
			else {
				view.setActivated(false);
			}

			// for performance reasons we are going to get indexed values
			// from the cursor instead of instantiating the entire bucket object
			holder.contentTextView.setVisibility(View.VISIBLE);
			holder.contentTextView.setMaxLines(mNumPreviewLines);
			mCursor.moveToPosition(position);
			holder.setNoteId(mCursor.getSimperiumKey());

			String title = mCursor.getString(mCursor.getColumnIndex(Note.TITLE_INDEX_NAME));

			if (title == null || title.equals("")) {
				SpannableString untitled = new SpannableString(getString(R.string.new_note_list));
				untitled.setSpan(new TextAppearanceSpan(getActivity(), R.style.UntitledNoteAppearance), 0, untitled.length(), 0x0);
				holder.titleTextView.setText(untitled);
			}
			else {
				holder.titleTextView.setText(title);
			}

			holder.matchOffsets = null;

			int matchOffsetsIndex = mCursor.getColumnIndex("match_offsets");
			if (hasSearchQuery() && matchOffsetsIndex != -1) {
				title = mCursor.getString(mCursor.getColumnIndex(Note.MATCHED_TITLE_INDEX_NAME));
				String snippet = mCursor.getString(mCursor.getColumnIndex(Note.MATCHED_CONTENT_INDEX_NAME));

				holder.matchOffsets = mCursor.getString(matchOffsetsIndex);

				try {
					holder.contentTextView.setText(SearchSnippetFormatter.formatString(snippet, mSnippetHighlighter));
					holder.titleTextView.setText(SearchSnippetFormatter.formatString(title, mSnippetHighlighter));
				}
				catch (NullPointerException e) {
					title = mCursor.getString(mCursor.getColumnIndex(Note.TITLE_INDEX_NAME));
					title = ( title != null ) ? title : "";
					holder.titleTextView.setText(title);
					String matchedContentPreview = mCursor.getString(mCursor.getColumnIndex(Note.CONTENT_PREVIEW_INDEX_NAME));
					holder.contentTextView.setText(matchedContentPreview);
				}
			}
			else if (mNumPreviewLines > 0) {

				String contentPreview = mCursor.getString(mCursor.getColumnIndex(Note.CONTENT_PREVIEW_INDEX_NAME));
				if (title == null || title.equals(contentPreview) || title.equals(getString(R.string.new_note_list))) {
					holder.contentTextView.setVisibility(View.GONE);
				}
				else {
					holder.contentTextView.setText(contentPreview);
				}
			}

			return view;
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
			return null;
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {

		}
	}

	private class refreshListTask extends AsyncTask<Boolean, Void, Bucket.ObjectCursor<Note>> {
		boolean mIsFromNavSelect;

		@Override
		protected Bucket.ObjectCursor<Note> doInBackground(Boolean... args) {
			mIsFromNavSelect = args[0];
			return queryNotes();
		}

		@Override
		protected void onPostExecute(Bucket.ObjectCursor<Note> cursor) {
			if (cursor == null || getActivity() == null || getActivity().isFinishing()) {
				return;
			}
			try {
				mNotesAdapter.changeCursor(cursor);
			}
			catch (SQLiteException e) {
				android.util.Log.e(App.TAG, "Invalid SQL statement", e);
				mNotesAdapter.changeCursor(null);
			}

			if (mSelectedNoteId != null) {
				setNoteSelected(mSelectedNoteId);
				mSelectedNoteId = null;
			}
		}
	}

	private class trashNotesTask extends AsyncTask<Void, Void, Void> {

		private List<String> mDeletedNoteIds = new ArrayList<>();
		private SparseBooleanArray mSelectedRows = new SparseBooleanArray();

		@Override
		protected void onPreExecute() {
			if (getListView() != null) {
				mSelectedRows = getListView().getCheckedItemPositions();
			}
		}

		@Override
		protected Void doInBackground(Void... args) {

			// Get the checked notes and add them to the deletedNotesList
			// We can't modify the note in this loop because the adapter could change
			List<Note> deletedNotesList = new ArrayList<>();
			for (int i = 0; i < mSelectedRows.size(); i++) {
				if (mSelectedRows.valueAt(i)) {
					deletedNotesList.add(mNotesAdapter.getItem(mSelectedRows.keyAt(i)));
				}
			}

			// Now loop through the notes list and mark them as deleted
			for (Note deletedNote : deletedNotesList) {
				mDeletedNoteIds.add(deletedNote.getSimperiumKey());
				deletedNote.setDeleted(!deletedNote.isDeleted());
				deletedNote.setModificationDate(Calendar.getInstance());
				deletedNote.save();
			}

			return null;
		}

		@Override
		protected void onPostExecute(Void aVoid) {
			NotesActivity notesActivity = ( (NotesActivity) getActivity() );
			if (notesActivity != null) {
				notesActivity.showUndoBarWithNoteIds(mDeletedNoteIds);
			}

			refreshList();
		}
	}
}
