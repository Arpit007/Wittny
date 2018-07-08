package com.starkx.arpit.wittny.src;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.ArrayAdapter;

import com.google.cloud.translate.Detection;
import com.google.cloud.translate.Language;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.Translate.TranslateOption;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import com.google.common.collect.ImmutableList;

import java.util.List;

import javax.annotation.Nullable;

import static com.starkx.arpit.wittny.src.Api.API_KEY;

/**
 * Created by Home Laptop on 20-Jan-18.
 */

public class GTranslate {
	private static final String TAG = GTranslate.class.getSimpleName();

	Callback callback;
	Context context;
	List<Language> languages = null;

	public GTranslate(Context context, @NonNull Callback callback) {
		this.callback = callback;
		this.context = context;
	}

	private Translate createTranslateService() {
		return TranslateOptions.newBuilder().setApiKey(API_KEY).build().getService();
	}

	private Detection detectLanguage(String sourceText) {
		Translate translate = createTranslateService();
		List<Detection> detections = translate.detect(ImmutableList.of(sourceText));
		if (detections.size() > 0) {
			return detections.get(0);
		}
		return null;
	}

	private void getSupportedLanguages(@Nullable String sourceText) {
		Translate translate = createTranslateService();
		Detection language = detectLanguage(sourceText);
		String detected = ( language != null ) ? language.getLanguage() : "en";
		Translate.LanguageListOption target = Translate.LanguageListOption.targetLanguage(detected);
		languages = translate.listSupportedLanguages(target);
	}

	private String translateText(String sourceText, String targetLang) {
		Translate translate = createTranslateService();
		Translation translation = translate.translate(sourceText, TranslateOption.targetLanguage(targetLang));
		return translation.getTranslatedText();
	}

	public void convert(final String Text) {
		final ProgressDialog progressDialog = new ProgressDialog(context);
		progressDialog.setMessage("Please Wait");
		progressDialog.setCancelable(false);
		progressDialog.show();

		new Thread(new Runnable() {
			@Override
			public void run() {
				final String sourceText = Text.replaceAll("\n", "<br/>");
				getSupportedLanguages(sourceText);
				progressDialog.dismiss();

				new android.os.Handler(Looper.getMainLooper()).post(new Runnable() {
					@Override
					public void run() {
						AlertDialog.Builder builderSingle = new AlertDialog.Builder(context);
						builderSingle.setTitle("Select Target Language");

						final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(context, android.R.layout.select_dialog_singlechoice);
						for (Language language : languages) {
							arrayAdapter.add(language.getName());
						}

						builderSingle.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								dialog.dismiss();
							}
						});

						builderSingle.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, final int which) {
								progressDialog.show();
								new Thread(new Runnable() {
									@Override
									public void run() {
										String translation = translateText(sourceText, languages.get(which).getCode());
										progressDialog.dismiss();
										if (translation != null && !translation.isEmpty()) {
											Log.d(TAG, translation);
											translation = translation.replaceAll("<br/>", "\n");
											callback.onResponse(translation);
										}
									}
								}).start();
							}
						});
						builderSingle.show();
					}
				});
			}
		}).start();
	}

	public interface Callback {
		void onResponse(String response);
	}
}
