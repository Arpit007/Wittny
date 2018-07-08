package com.starkx.arpit.wittny.utils;

import android.support.design.widget.Snackbar;
import android.view.View;

import com.starkx.arpit.wittny.R;

import java.util.List;

public class UndoBarController {
	private UndoListener mUndoListener;

	private List<String> mDeletedNoteIds;
	private View.OnClickListener mOnUndoClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			if (mUndoListener != null) {
				mUndoListener.onUndo();
			}
		}
	};

	public UndoBarController(UndoListener undoListener) {
		mUndoListener = undoListener;
	}

	public void showUndoBar(View view, CharSequence message) {
		if (view == null) {
			return;
		}

		Snackbar.make(view, message, Snackbar.LENGTH_LONG)
				.setAction(R.string.undo, mOnUndoClickListener)
				.show();
	}

	public List<String> getDeletedNoteIds() {
		return mDeletedNoteIds;
	}

	public void setDeletedNoteIds(List<String> noteIds) {
		mDeletedNoteIds = noteIds;
	}

	public interface UndoListener {
		void onUndo();
	}
}
