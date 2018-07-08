package com.starkx.arpit.wittny;

import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.starkx.arpit.wittny.ui.NotesActivity;

public class Splash extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_splash);

		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				Intent intent = new Intent(Splash.this, NotesActivity.class);
				startActivity(intent);
				finish();
			}
		}, 1500);
	}
}
