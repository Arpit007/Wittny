package com.starkx.arpit.wittny.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.NavigationView;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.simperium.client.Bucket;
import com.simperium.client.BucketObjectMissingException;
import com.simperium.client.Query;
import com.starkx.arpit.wittny.App;
import com.starkx.arpit.wittny.R;
import com.starkx.arpit.wittny.models.Note;
import com.starkx.arpit.wittny.utils.DisplayUtils;
import com.starkx.arpit.wittny.utils.DrawableUtils;
import com.starkx.arpit.wittny.utils.HtmlCompat;
import com.starkx.arpit.wittny.utils.TagsAdapter;
import com.starkx.arpit.wittny.utils.UndoBarController;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class NotesActivity extends AppCompatActivity implements
		NoteListFragment.Callbacks, UndoBarController.UndoListener,
		Bucket.Listener<Note> {

	public boolean Exit = false;
	public static String TAG_NOTE_LIST = "noteList";
	public static String TAG_NOTE_EDITOR = "noteEditor";
	protected Bucket<Note> mNotesBucket;
	private int TRASH_SELECTED_ID = 1;
	private boolean mShouldSelectNewNote;
	private UndoBarController mUndoBarController;
	private View mFragmentsContainer;
	private SearchView mSearchView;
	private MenuItem mSearchMenuItem;
	private NoteListFragment mNoteListFragment;
	private NoteEditorFragment mNoteEditorFragment;
	private Note mCurrentNote;
	private MenuItem mEmptyTrashMenuItem;

	// Menu drawer
	private DrawerLayout mDrawerLayout;
	private ListView mDrawerList;
	private NavigationView mNavigationView;
	private ActionBarDrawerToggle mDrawerToggle;
	private TagsAdapter mTagsAdapter;
	private TagsAdapter.TagMenuItem mSelectedTag;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
			getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.transparent));
		}

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_notes);

		mFragmentsContainer = findViewById(R.id.note_fragment_container);

		App currentApp = (App) getApplication();
		if (mNotesBucket == null) {
			mNotesBucket = currentApp.getNotesBucket();
		}

		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		configureNavigationDrawer(toolbar);

		if (savedInstanceState == null) {
			mNoteListFragment = new NoteListFragment();
			FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
			fragmentTransaction.add(R.id.note_fragment_container, mNoteListFragment, TAG_NOTE_LIST);
			fragmentTransaction.commit();
		}
		else {
			mNoteListFragment = (NoteListFragment) getSupportFragmentManager().findFragmentByTag(TAG_NOTE_LIST);
		}

		if (DisplayUtils.isLargeScreen(this)) {
			if (getSupportFragmentManager().findFragmentByTag(TAG_NOTE_EDITOR) != null) {
				mNoteEditorFragment = (NoteEditorFragment) getSupportFragmentManager().findFragmentByTag(TAG_NOTE_EDITOR);
			}
			else if (DisplayUtils.isLandscape(this)) {
				addEditorFragment();
			}
		}

		// enable ActionBar app icon to behave as action to toggle nav drawer
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setHomeButtonEnabled(true);

			// Add loading indicator to show when indexing
			ProgressBar progressBar = (ProgressBar) getLayoutInflater().inflate(R.layout.progressbar_toolbar, null);
			actionBar.setDisplayShowCustomEnabled(true);
			actionBar.setCustomView(progressBar);
			setToolbarProgressVisibility(false);
		}

		mUndoBarController = new UndoBarController(this);
	}

	@Override
	protected void onResume() {
		super.onResume();

		mNotesBucket.start();

		mNotesBucket.addOnSaveObjectListener(this);
		mNotesBucket.addOnDeleteObjectListener(this);

		setSelectedTagActive();

		if (mCurrentNote != null && mShouldSelectNewNote) {
			onNoteSelected(mCurrentNote.getSimperiumKey(), 0, true, null, false);
			mShouldSelectNewNote = false;
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		mNotesBucket.removeOnSaveObjectListener(this);
		mNotesBucket.removeOnDeleteObjectListener(this);
	}

	@Override
	public void setTitle(CharSequence title) {
		if (title == null) {
			title = "";
		}
		if (getSupportActionBar() != null) {
			getSupportActionBar().setTitle(title);
		}
	}

	@Override
	public void onBackPressed() {
		if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
			mDrawerLayout.closeDrawer(GravityCompat.START);
		}
		else {
			if (Exit) {
				super.onBackPressed();
			}
			else {
				Toast.makeText(this, "Press back again to Exit", Toast.LENGTH_SHORT).show();
				new Handler().postDelayed(new Runnable() {
					@Override
					public void run() {
						Exit = false;
					}
				}, 1000);
			}
		}
	}

	private void configureNavigationDrawer(Toolbar toolbar) {
		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
		mNavigationView = (NavigationView) findViewById(R.id.navigation_view);
		mDrawerList = (ListView) findViewById(R.id.drawer_list);

		mNavigationView.getLayoutParams().width = getOptimalDrawerWidth(this);
		mTagsAdapter = new TagsAdapter(this, mNotesBucket, mDrawerList.getHeaderViewsCount());
		mDrawerList.setAdapter(mTagsAdapter);
		mDrawerList.setOnItemClickListener(new DrawerItemClickListener());

		if (mSelectedTag == null) {
			mSelectedTag = mTagsAdapter.getDefaultItem();
		}

		mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar, R.string.open_drawer,
				R.string.close_drawer) {
			public void onDrawerClosed(View view) {
				supportInvalidateOptionsMenu();
			}

			public void onDrawerOpened(View drawerView) {
			}

			@Override
			public void onDrawerSlide(View drawerView, float slideOffset) {
				super.onDrawerSlide(drawerView, 0f);
			}
		};

		mDrawerLayout.addDrawerListener(mDrawerToggle);
	}

	private void setSelectedTagActive() {
		if (mSelectedTag == null) {
			mSelectedTag = mTagsAdapter.getDefaultItem();
		}

		setTitle(mSelectedTag.name);
		mDrawerList.setItemChecked(mTagsAdapter.getPosition(mSelectedTag) + mDrawerList.getHeaderViewsCount(), true);
	}

	public TagsAdapter.TagMenuItem getSelectedTag() {
		if (mSelectedTag == null) {
			mSelectedTag = mTagsAdapter.getDefaultItem();
		}

		return mSelectedTag;
	}

	// Enable or disable the trash action bar button depending on if there are deleted notes or not
	public void updateTrashMenuItem() {
		if (mEmptyTrashMenuItem == null || mNotesBucket == null) {
			return;
		}

		Query<Note> query = Note.allDeleted(mNotesBucket);
		if (query.count() == 0) {
			mEmptyTrashMenuItem.setEnabled(false);
		}
		else {
			mEmptyTrashMenuItem.setEnabled(true);
		}
	}

	private void addEditorFragment() {
		FragmentManager fm = getSupportFragmentManager();
		FragmentTransaction ft = fm.beginTransaction();
		mNoteEditorFragment = new NoteEditorFragment();
		ft.add(R.id.note_fragment_container, mNoteEditorFragment, TAG_NOTE_EDITOR);
		ft.commitAllowingStateLoss();
		fm.executePendingTransactions();
	}

	public void setCurrentNote(Note note) {
		mCurrentNote = note;
	}

	public NoteListFragment getNoteListFragment() {
		return mNoteListFragment;
	}

	@SuppressWarnings("ResourceType")
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.notes_list, menu);

		mSearchMenuItem = menu.findItem(R.id.menu_search);
		mSearchView = (SearchView) mSearchMenuItem.getActionView();

		String hintHexColor = getString(R.color.light_grey).replace("ff", "");
		mSearchView.setQueryHint(HtmlCompat.fromHtml(String.format("<font color=\"%s\">%s</font>",
				hintHexColor,
				getString(R.string.search))));

		mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextChange(String newText) {
				if (mSearchMenuItem.isActionViewExpanded()) {
					getNoteListFragment().searchNotes(newText);
				}
				return true;
			}

			@Override
			public boolean onQueryTextSubmit(String queryText) {
				getNoteListFragment().searchNotes(queryText);
				return true;
			}

		});

		MenuItemCompat.setOnActionExpandListener(mSearchMenuItem, new MenuItemCompat.OnActionExpandListener() {
			@Override
			public boolean onMenuItemActionExpand(MenuItem menuItem) {
				checkEmptyListText(true);
				if (mNoteListFragment != null) {
					mNoteListFragment.setFloatingActionButtonVisible(false);
				}
				return true;
			}

			@Override
			public boolean onMenuItemActionCollapse(MenuItem menuItem) {
				// Show all notes again
				if (mNoteListFragment != null) {
					mNoteListFragment.setFloatingActionButtonVisible(true);
				}
				mSearchView.setQuery("", false);
				checkEmptyListText(false);
				getNoteListFragment().clearSearch();
				return true;
			}
		});

		mSearchMenuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				return false;
			}
		});

		MenuItem trashItem = menu.findItem(R.id.menu_delete).setTitle(R.string.restore);
		if (mCurrentNote != null && mCurrentNote.isDeleted()) {
			trashItem.setTitle(R.string.restore);
		}
		else {
			trashItem.setTitle(R.string.delete);
		}

		menu.findItem(R.id.menu_search).setVisible(true);
		trashItem.setVisible(false);
		menu.findItem(R.id.menu_empty_trash).setVisible(false);

		// Are we looking at the trash? Adjust menu accordingly.
		if (mDrawerList.getCheckedItemPosition() == TRASH_SELECTED_ID) {
			mEmptyTrashMenuItem = menu.findItem(R.id.menu_empty_trash);
			mEmptyTrashMenuItem.setVisible(true);

			updateTrashMenuItem();

			menu.findItem(R.id.menu_search).setVisible(false);
		}

		DrawableUtils.tintMenuWithAttribute(this, menu, R.attr.actionBarTextColor);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (mDrawerToggle.onOptionsItemSelected(item)) {
			return true;
		}
		switch (item.getItemId()) {
			case R.id.menu_delete:
				if (mNoteEditorFragment != null) {
					if (mCurrentNote != null) {
						mCurrentNote.setDeleted(!mCurrentNote.isDeleted());
						mCurrentNote.setModificationDate(Calendar.getInstance());
						mCurrentNote.save();

						if (mCurrentNote.isDeleted()) {
							List<String> deletedNoteIds = new ArrayList<>();
							deletedNoteIds.add(mCurrentNote.getSimperiumKey());
							mUndoBarController.setDeletedNoteIds(deletedNoteIds);
							mUndoBarController.showUndoBar(getUndoView(), getString(R.string.note_deleted));
						}
					}
					NoteListFragment fragment = getNoteListFragment();
					if (fragment != null) {
						fragment.getPrefs();
						fragment.refreshList();
					}
				}
				return true;
			case R.id.menu_empty_trash:
				AlertDialog.Builder alert = new AlertDialog.Builder(this);

				alert.setTitle(R.string.empty_trash);
				alert.setMessage(R.string.confirm_empty_trash);
				alert.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						new emptyTrashTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
					}
				});
				alert.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						// Do nothing, just closing the dialog
					}
				});
				alert.show();
				return true;
			case android.R.id.home:
				invalidateOptionsMenu();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * Callback method from {@link NoteListFragment.Callbacks} indicating that
	 * the item with the given ID was selected. Used for tablets only.
	 */
	@Override
	public void onNoteSelected(String noteID, int position, boolean isNew, String matchOffsets, boolean isMarkdownEnabled) {
		// Launch the editor activity
		Bundle arguments = new Bundle();
		arguments.putString(NoteEditorFragment.ARG_ITEM_ID, noteID);
		arguments.putBoolean(NoteEditorFragment.ARG_NEW_NOTE, isNew);
		arguments.putBoolean(NoteEditorFragment.ARG_MARKDOWN_ENABLED, isMarkdownEnabled);

		if (matchOffsets != null) {
			arguments.putString(NoteEditorFragment.ARG_MATCH_OFFSETS, matchOffsets);
		}

		Intent editNoteIntent = new Intent(this, NoteEditorActivity.class);
		editNoteIntent.putExtras(arguments);
		startActivityForResult(editNoteIntent, App.INTENT_EDIT_NOTE);
	}

	private void setToolbarProgressVisibility(boolean isVisible) {
		if (getSupportActionBar() != null) {
			getSupportActionBar().setDisplayShowCustomEnabled(isVisible);
		}
	}

	@Override
	public void recreate() {
		Handler handler = new Handler();
		handler.post(new Runnable() {
			@Override
			public void run() {
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
					NotesActivity.this.finish();
					NotesActivity.this.startActivity(NotesActivity.this.getIntent());
				}
				else {
					NotesActivity.super.recreate();
				}
			}
		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
			case App.INTENT_PREFERENCES:
				// nbradbury - refresh note list when user returns from preferences (in case they changed anything)
				invalidateOptionsMenu();
				NoteListFragment fragment = getNoteListFragment();
				if (fragment != null) {
					fragment.getPrefs();
					fragment.refreshList();
				}

				break;
			case App.INTENT_EDIT_NOTE:
				if (resultCode == RESULT_OK && data != null && data.hasExtra(App.DELETED_NOTE_ID)) {
					String noteId = data.getStringExtra(App.DELETED_NOTE_ID);
					if (noteId != null) {
						List<String> deletedNoteIds = new ArrayList<>();
						deletedNoteIds.add(noteId);
						mUndoBarController.setDeletedNoteIds(deletedNoteIds);
						mUndoBarController.showUndoBar(getUndoView(), getString(R.string.note_deleted));
					}
				}
				break;
		}
	}

	@Override
	public void onUndo() {
		if (mUndoBarController == null) {
			return;
		}

		List<String> deletedNoteIds = mUndoBarController.getDeletedNoteIds();
		if (deletedNoteIds != null) {
			for (int i = 0; i < deletedNoteIds.size(); i++) {
				Note deletedNote;
				try {
					deletedNote = mNotesBucket.get(deletedNoteIds.get(i));
				}
				catch (BucketObjectMissingException e) {
					return;
				}
				if (deletedNote != null) {
					deletedNote.setDeleted(false);
					deletedNote.setModificationDate(Calendar.getInstance());
					deletedNote.save();
					NoteListFragment fragment = getNoteListFragment();
					if (fragment != null) {
						fragment.getPrefs();
						fragment.refreshList();
					}
				}
			}
		}
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		// Sync the toggle state after onRestoreInstanceState has occurred.
		mDrawerToggle.syncState();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		mDrawerToggle.onConfigurationChanged(newConfig);
	}

	public void checkEmptyListText(boolean isSearch) {
		if (isSearch) {
			getNoteListFragment().setEmptyListMessage("<strong>" + getString(R.string.no_notes_found) + "</strong>");
			getNoteListFragment().setEmptyListViewClickable(false);
		}
		else if (mDrawerList.getCheckedItemPosition() == TRASH_SELECTED_ID) {
			getNoteListFragment().setEmptyListMessage("<strong>" + getString(R.string.trash_is_empty) + "</strong>");
			getNoteListFragment().setEmptyListViewClickable(false);
		}
		else {
			getNoteListFragment().setEmptyListMessage("<strong>" + getString(R.string.no_notes_here) + "</strong><br />" + String.format(getString(R.string.why_not_create_one), "<u>", "</u>"));
			getNoteListFragment().setEmptyListViewClickable(true);
		}
	}

	public void stopListeningToNotesBucket() {
		mNotesBucket.removeOnSaveObjectListener(this);
		mNotesBucket.removeOnDeleteObjectListener(this);
	}

	private View getUndoView() {
		View undoView = mFragmentsContainer;
		if (!DisplayUtils.isLargeScreenLandscape(this) &&
				getNoteListFragment() != null &&
				getNoteListFragment().getRootView() != null) {
			undoView = getNoteListFragment().getRootView();
		}

		return undoView;
	}

	public void showUndoBarWithNoteIds(List<String> noteIds) {
		if (mUndoBarController != null) {
			mUndoBarController.setDeletedNoteIds(noteIds);
			mUndoBarController.showUndoBar(
					getUndoView(),
					getResources().getQuantityString(R.plurals.trashed_notes, noteIds.size(), noteIds.size())
			);
		}
	}

	@Override
	public void onNetworkChange(Bucket<Note> bucket, final Bucket.ChangeType type, String key) {
	}

	@Override
	public void onSaveObject(Bucket<Note> bucket, Note object) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mNoteListFragment.refreshList();
			}
		});
	}

	@Override
	public void onDeleteObject(Bucket<Note> bucket, Note object) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mNoteListFragment.refreshList();
			}
		});
	}

	@Override
	public void onBeforeUpdateObject(Bucket<Note> bucket, Note note) {
	}

	private class DrawerItemClickListener implements ListView.OnItemClickListener {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			position -= mDrawerList.getHeaderViewsCount();
			mSelectedTag = mTagsAdapter.getItem(position);
			checkEmptyListText(false);
			setSelectedTagActive();
			mDrawerLayout.closeDrawer(mNavigationView);
			if (mDrawerList.getCheckedItemPosition() == TRASH_SELECTED_ID) {
				getNoteListFragment().getListView().setLongClickable(false);
			}
			else {
				getNoteListFragment().getListView().setLongClickable(true);
			}

			getNoteListFragment().refreshListFromNavSelect();
		}
	}

	private class emptyTrashTask extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... voids) {
			if (mNotesBucket == null) {
				return null;
			}

			Query<Note> query = Note.allDeleted(mNotesBucket);
			Bucket.ObjectCursor c = query.execute();
			while (c.moveToNext()) {
				c.getObject().delete();
			}

			return null;
		}
	}

	public static int getOptimalDrawerWidth(Context context) {
		Point displaySize = DisplayUtils.getDisplayPixelSize(context);
		int appBarHeight = DisplayUtils.getActionBarHeight(context);
		int drawerWidth = Math.min(displaySize.x, displaySize.y) - appBarHeight;
		int maxDp = ( DisplayUtils.isXLarge(context) ? 400 : 320 );
		int maxPx = DisplayUtils.dpToPx(context, maxDp);
		return Math.min(drawerWidth, maxPx);
	}
}