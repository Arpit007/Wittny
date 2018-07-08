package com.starkx.arpit.wittny.utils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.annotation.AttrRes;
import android.support.annotation.ColorInt;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;

public class DrawableUtils {
    public static Drawable tintDrawable(Drawable drawable, @ColorInt int color) {
        if (drawable != null) {
            drawable = DrawableCompat.wrap(drawable).mutate();
            DrawableCompat.setTint(drawable, color);
        }
        return drawable;
    }

    private static int getColor(Context context, @AttrRes int tintColorAttribute) {
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(tintColorAttribute, typedValue, true);
        return typedValue.data;
    }

    public static void tintMenuWithAttribute(Context context, Menu menu, @AttrRes int tintColorAttribute) {
        @ColorInt int color = getColor(context, tintColorAttribute);
        DrawableUtils.tintMenu(menu, color);
    }

    public static void tintMenu(Menu menu, @ColorInt int color) {
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            DrawableUtils.tintMenuItem(item, color);
        }
    }

    public static void tintMenuItem(MenuItem menuItem, @ColorInt int color) {
        Drawable tinted = DrawableUtils.tintDrawable(menuItem.getIcon(), color);
        menuItem.setIcon(tinted);
    }
}
