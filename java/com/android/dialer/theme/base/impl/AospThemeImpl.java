/*
 * Copyright (C) 2018 The Android Open Source Project
 * Copyright (C) 2023-2025 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dialer.theme.base.impl;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;

import androidx.annotation.ColorInt;
import androidx.annotation.StyleRes;

import com.android.dialer.R;
import com.android.dialer.common.Assert;
import com.android.dialer.theme.base.Theme;

import javax.inject.Singleton;

/** Utility for fetching theme attributes dynamically across light and dark modes. */
@SuppressWarnings("unused")
@Singleton
public class AospThemeImpl implements Theme {

  private static final int UNINITIALIZED = -2;

  private final Context appContext;
  private int cachedNightMode = -1;

  private int colorIcon = UNINITIALIZED;
  private int colorIconSecondary = UNINITIALIZED;
  private int colorPrimary = UNINITIALIZED;
  private int colorPrimaryDark = UNINITIALIZED;
  private int colorAccent = UNINITIALIZED;
  private int textColorPrimary = UNINITIALIZED;
  private int textColorSecondary = UNINITIALIZED;
  private int textColorPrimaryInverse = UNINITIALIZED;
  private int textColorHint = UNINITIALIZED;
  private int colorBackground = UNINITIALIZED;
  private int colorBackgroundFloating = UNINITIALIZED;
  private int colorTextOnUnthemedDarkBackground = UNINITIALIZED;
  private int colorIconOnUnthemedDarkBackground = UNINITIALIZED;
  private int colorCallNotificationBackground = UNINITIALIZED;

  public AospThemeImpl(Context context) {
    this.appContext = context.getApplicationContext();
    ensureColorsLoaded();
  }

  private synchronized void ensureColorsLoaded() {
    int currentNightMode =
        appContext.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    if (currentNightMode == cachedNightMode && colorPrimary != UNINITIALIZED) {
      return;
    }
    cachedNightMode = currentNightMode;
    @StyleRes int themeRes = getApplicationThemeRes();
    Context themedContext = new ContextThemeWrapper(appContext, themeRes);
    TypedArray array =
        themedContext
            .getTheme()
            .obtainStyledAttributes(
                themeRes,
                new int[] {
                  android.R.attr.colorPrimary,
                  android.R.attr.colorPrimaryDark,
                  android.R.attr.colorAccent,
                  android.R.attr.textColorPrimary,
                  android.R.attr.textColorSecondary,
                  android.R.attr.textColorPrimaryInverse,
                  android.R.attr.textColorHint,
                  android.R.attr.colorBackground,
                  android.R.attr.colorBackgroundFloating,
                  R.attr.colorIcon,
                  R.attr.colorIconSecondary,
                  R.attr.colorTextOnUnthemedDarkBackground,
                  R.attr.colorIconOnUnthemedDarkBackground,
                  R.attr.colorCallNotificationBackground
                });
    colorPrimary = array.getColor(/* index= */ 0, /* defValue= */ UNINITIALIZED);
    colorPrimaryDark = array.getColor(/* index= */ 1, /* defValue= */ UNINITIALIZED);
    colorAccent = array.getColor(/* index= */ 2, /* defValue= */ UNINITIALIZED);
    textColorPrimary = array.getColor(/* index= */ 3, /* defValue= */ UNINITIALIZED);
    textColorSecondary = array.getColor(/* index= */ 4, /* defValue= */ UNINITIALIZED);
    textColorPrimaryInverse = array.getColor(/* index= */ 5, /* defValue= */ UNINITIALIZED);
    textColorHint = array.getColor(/* index= */ 6, /* defValue= */ UNINITIALIZED);
    colorBackground = array.getColor(/* index= */ 7, /* defValue= */ UNINITIALIZED);
    colorBackgroundFloating = array.getColor(/* index= */ 8, /* defValue= */ UNINITIALIZED);
    colorIcon = array.getColor(/* index= */ 9, /* defValue= */ UNINITIALIZED);
    colorIconSecondary = array.getColor(/* index= */ 10, /* defValue= */ UNINITIALIZED);
    colorTextOnUnthemedDarkBackground =
        array.getColor(/* index= */ 11, /* defValue= */ UNINITIALIZED);
    colorIconOnUnthemedDarkBackground =
        array.getColor(/* index= */ 12, /* defValue= */ UNINITIALIZED);
    colorCallNotificationBackground =
        array.getColor(/* index= */ 13, /* defValue= */ UNINITIALIZED);
    array.recycle();
  }

  /**
   * Returns the {@link Theme} that the application is using. Activities should check this value if
   * their custom style needs to customize further based on the application theme.
   */
  @Override
  public @Type int getTheme() {
    int nightMode =
        appContext.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    return nightMode == Configuration.UI_MODE_NIGHT_YES ? DARK : LIGHT;
  }

  @Override
  public @StyleRes int getApplicationThemeRes() {
    switch (getTheme()) {
      case DARK:
        return R.style.Dialer_Dark_ThemeBase_NoActionBar;
      case LIGHT:
        return R.style.Dialer_ThemeBase_NoActionBar;
      case UNKNOWN:
      default:
        throw Assert.createIllegalStateFailException("Theme hasn't been set yet.");
    }
  }

  @Override
  public Context getThemedContext(Context context) {
    return new ContextThemeWrapper(context, getApplicationThemeRes());
  }

  @Override
  public LayoutInflater getThemedLayoutInflator(LayoutInflater inflater) {
    return inflater.cloneInContext(getThemedContext(inflater.getContext()));
  }

  @Override
  public @ColorInt int getColorIcon() {
    ensureColorsLoaded();
    Assert.checkArgument(colorIcon != UNINITIALIZED);
    return colorIcon;
  }

  @Override
  public @ColorInt int getColorIconSecondary() {
    ensureColorsLoaded();
    Assert.checkArgument(colorIconSecondary != UNINITIALIZED);
    return colorIconSecondary;
  }

  @Override
  public @ColorInt int getColorPrimary() {
    ensureColorsLoaded();
    Assert.checkArgument(colorPrimary != UNINITIALIZED);
    return colorPrimary;
  }

  @Override
  public int getColorPrimaryDark() {
    ensureColorsLoaded();
    Assert.checkArgument(colorPrimaryDark != UNINITIALIZED);
    return colorPrimaryDark;
  }

  @Override
  public @ColorInt int getColorAccent() {
    ensureColorsLoaded();
    Assert.checkArgument(colorAccent != UNINITIALIZED);
    return colorAccent;
  }

  @Override
  public @ColorInt int getTextColorSecondary() {
    ensureColorsLoaded();
    Assert.checkArgument(textColorSecondary != UNINITIALIZED);
    return textColorSecondary;
  }

  @Override
  public @ColorInt int getTextColorPrimary() {
    ensureColorsLoaded();
    Assert.checkArgument(textColorPrimary != UNINITIALIZED);
    return textColorPrimary;
  }

  @Override
  public @ColorInt int getColorTextOnUnthemedDarkBackground() {
    ensureColorsLoaded();
    Assert.checkArgument(colorTextOnUnthemedDarkBackground != UNINITIALIZED);
    return colorTextOnUnthemedDarkBackground;
  }

  @Override
  public @ColorInt int getColorIconOnUnthemedDarkBackground() {
    ensureColorsLoaded();
    Assert.checkArgument(colorIconOnUnthemedDarkBackground != UNINITIALIZED);
    return colorIconOnUnthemedDarkBackground;
  }

  @Override
  public @ColorInt int getColorCallNotificationBackground() {
    ensureColorsLoaded();
    Assert.checkArgument(colorCallNotificationBackground != UNINITIALIZED);
    return colorCallNotificationBackground;
  }
}
