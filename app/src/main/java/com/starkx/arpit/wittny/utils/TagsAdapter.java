package com.starkx.arpit.wittny.utils;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;

import com.simperium.client.Bucket;
import com.simperium.client.Query;
import com.starkx.arpit.wittny.R;
import com.starkx.arpit.wittny.models.Note;
import com.starkx.arpit.wittny.src.TintedTextView;

public class TagsAdapter extends BaseAdapter {
	public static final long ALL_NOTES_ID = -1L;
	public static final long TRASH_ID = -2L;

	public static final int DEFAULT_ITEM_POSITION = 0;
	protected Context mContext;
	protected LayoutInflater mInflater;
	protected Bucket<Note> mNotesBucket;
	protected TagMenuItem mAllNotesItem;
	protected TagMenuItem mTrashItem;
	private int mTextColorId;
	private int mHeaderCount;

	public TagsAdapter(Context context, Bucket<Note> notesBucket, int headerCount) {
		mHeaderCount = headerCount;
		mContext = context;
		mNotesBucket = notesBucket;
		mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mAllNotesItem = new TagMenuItem(ALL_NOTES_ID, R.string.notes) {

			@Override
			public Query<Note> query() {
				return Note.all(mNotesBucket);
			}

		};
		mTrashItem = new TagMenuItem(TRASH_ID, R.string.trash) {

			@Override
			public Query<Note> query() {
				return Note.allDeleted(mNotesBucket);
			}

		};

		TypedArray a = mContext.obtainStyledAttributes(new int[]{ R.attr.noteTitleColor });
		mTextColorId = a.getResourceId(0, 0);
		a.recycle();
	}

	@Override
	public int getCount() {
		return 2;
	}

	public TagMenuItem getDefaultItem() {
		return getItem(DEFAULT_ITEM_POSITION);
	}

	@Override
	public TagMenuItem getItem(int i) {
		switch (i) {
			case 0:
				return mAllNotesItem;
			case 1:
				return mTrashItem;
			default:
				return null;
		}
	}

	@Override
	public long getItemId(int position) {
		return getItem(position).id;
	}

	@Override
	public View getView(int position, View view, ViewGroup viewGroup) {

		if (view == null) {
			view = mInflater.inflate(R.layout.nav_drawer_row, null);
		}
		TagMenuItem tagMenuItem = getItem(position);

		TintedTextView drawerItemText = view.findViewById(R.id.drawer_item_name);
		drawerItemText.setText(tagMenuItem.name);

		int selectedPosition = ( (ListView) viewGroup ).getCheckedItemPosition() - mHeaderCount;

		int color = ContextCompat.getColor(mContext, mTextColorId);
		if (position == selectedPosition) {
			color = ContextCompat.getColor(mContext, R.color.blue);
		}

		View dividerView = view.findViewById(R.id.section_divider);

		Drawable icon = null;
		if (position == 0) {
			icon = ContextCompat.getDrawable(mContext, R.drawable.ic_notes_24dp);
			dividerView.setVisibility(View.GONE);
		}
		else if (position == 1) {
			icon = ContextCompat.getDrawable(mContext, R.drawable.ic_trash_24dp);
			dividerView.setVisibility(View.VISIBLE);
		}

		drawerItemText.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null, color);
		drawerItemText.setTextColor(color);

		return view;
	}

	public int getPosition(TagMenuItem mSelectedTag) {
		if (mSelectedTag.id == ALL_NOTES_ID) {
			return 0;
		}
		if (mSelectedTag.id == TRASH_ID) {
			return 1;
		}
		return -1;
	}

	public class TagMenuItem {
		public String name;
		public long id;

		private TagMenuItem(long id, int resourceId) {
			this(id, mContext.getResources().getString(resourceId));
		}

		private TagMenuItem(long id, String name) {
			this.id = id;
			this.name = name;
		}

		public Query<Note> query() {
			return Note.allInTag(mNotesBucket, this.name);
		}
	}
}
