package com.starkx.arpit.wittny.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.starkx.arpit.wittny.R;
import com.starkx.arpit.wittny.src.NoteEditorViewPager;
import com.starkx.arpit.wittny.utils.PermissionUtils;

import java.util.ArrayList;

import static com.starkx.arpit.wittny.ui.NoteEditorFragment.CAMERA_PERMISSIONS_REQUEST;
import static com.starkx.arpit.wittny.ui.NoteEditorFragment.GALLERY_PERMISSIONS_REQUEST;

public class NoteEditorActivity extends AppCompatActivity {
	private TabLayout mTabLayout;
	private NoteEditorFragmentPagerAdapter mNoteEditorFragmentPagerAdapter;
	private NoteEditorViewPager mViewPager;
	private boolean isMarkdownEnabled;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_note_editor);

		setTitle("");

		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		if (getSupportActionBar() != null) {
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		}

		NoteEditorFragment noteEditorFragment;

		mNoteEditorFragmentPagerAdapter =
				new NoteEditorFragmentPagerAdapter(getSupportFragmentManager());
		mViewPager = findViewById(R.id.pager);
		mTabLayout = findViewById(R.id.tabs);

		if (savedInstanceState == null) {
			Intent intent = getIntent();
			// Create the note editor fragment
			Bundle arguments = new Bundle();
			arguments.putString(NoteEditorFragment.ARG_ITEM_ID,
					intent.getStringExtra(NoteEditorFragment.ARG_ITEM_ID));

			boolean isNewNote = intent.getBooleanExtra(NoteEditorFragment.ARG_NEW_NOTE, false);
			arguments.putBoolean(NoteEditorFragment.ARG_NEW_NOTE, isNewNote);
			if (intent.hasExtra(NoteEditorFragment.ARG_MATCH_OFFSETS)) {
				arguments.putString(NoteEditorFragment.ARG_MATCH_OFFSETS,
						intent.getStringExtra(NoteEditorFragment.ARG_MATCH_OFFSETS));
			}

			noteEditorFragment = new NoteEditorFragment();
			noteEditorFragment.setArguments(arguments);

			mNoteEditorFragmentPagerAdapter.addFragment(
					noteEditorFragment,
					getString(R.string.tab_edit)
			);
			mViewPager.setPagingEnabled(false);
			mViewPager.addOnPageChangeListener(
					new NoteEditorViewPager.OnPageChangeListener() {
						@Override
						public void onPageSelected(int position) {
							if (position == 1) {
								final InputMethodManager imm = (InputMethodManager) getSystemService(
										Context.INPUT_METHOD_SERVICE);
								imm.hideSoftInputFromWindow(mViewPager.getWindowToken(), 0);
							}
						}

						@Override
						public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
						}

						@Override
						public void onPageScrollStateChanged(int state) {
						}
					}
			);

			isMarkdownEnabled = intent.getBooleanExtra(NoteEditorFragment.ARG_MARKDOWN_ENABLED, false);
		}
		else {
			mNoteEditorFragmentPagerAdapter.addFragment(
					getSupportFragmentManager().getFragment(savedInstanceState, getString(R.string.tab_edit)),
					getString(R.string.tab_edit)
			);

			isMarkdownEnabled = savedInstanceState.getBoolean(NoteEditorFragment.ARG_MARKDOWN_ENABLED);
		}

		mViewPager.setAdapter(mNoteEditorFragmentPagerAdapter);
		mTabLayout.setupWithViewPager(mViewPager);

		// Show tabs if markdown is enabled for the current note.
		if (isMarkdownEnabled) {
			showTabs();
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		getSupportFragmentManager()
				.putFragment(outState, getString(R.string.tab_edit), mNoteEditorFragmentPagerAdapter.getItem(0));
		outState.putBoolean(NoteEditorFragment.ARG_MARKDOWN_ENABLED, isMarkdownEnabled);
		super.onSaveInstanceState(outState);
	}

	public void showTabs() {
		mTabLayout.setVisibility(View.VISIBLE);
		mViewPager.setPagingEnabled(true);
	}

	private static class NoteEditorFragmentPagerAdapter extends FragmentPagerAdapter {
		private final ArrayList<Fragment> mFragments = new ArrayList<>();
		private final ArrayList<String> mTitles = new ArrayList<>();

		public NoteEditorFragmentPagerAdapter(FragmentManager manager) {
			super(manager);
		}

		@Override
		public int getCount() {
			return mFragments.size();
		}

		@Override
		public Fragment getItem(int position) {
			return mFragments.get(position);
		}

		@Override
		public int getItemPosition(Object object) {
			return PagerAdapter.POSITION_NONE;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			return mTitles.get(position);
		}

		public void addFragment(Fragment fragment, String title) {
			mFragments.add(fragment);
			mTitles.add(title);
			notifyDataSetChanged();
		}
	}

	@Override
	public void onRequestPermissionsResult(
			int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		switch (requestCode) {
			case CAMERA_PERMISSIONS_REQUEST:
				if (PermissionUtils.permissionGranted(requestCode, CAMERA_PERMISSIONS_REQUEST, grantResults)) {
					( (NoteEditorFragment) mNoteEditorFragmentPagerAdapter.getItem(0) ).startCamera();
				}
				break;
			case GALLERY_PERMISSIONS_REQUEST:
				if (PermissionUtils.permissionGranted(requestCode, GALLERY_PERMISSIONS_REQUEST, grantResults)) {
					( (NoteEditorFragment) mNoteEditorFragmentPagerAdapter.getItem(0) ).startGalleryChooser();
				}
				break;
		}
	}
}
