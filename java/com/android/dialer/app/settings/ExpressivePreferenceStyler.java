/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.dialer.app.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroupAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.android.dialer.R;

/**
 * Applies Material 3 Expressive segmented card styling (grouped top/middle/bottom/single rounded
 * tonal cards) to any {@link PreferenceFragmentCompat} screen without mutating view layout passes
 * during draw.
 */
public final class ExpressivePreferenceStyler {

  private ExpressivePreferenceStyler() {}

  public static void apply(@NonNull PreferenceFragmentCompat fragment) {
    fragment.setDivider(null);
    fragment.setDividerHeight(0);

    RecyclerView recyclerView = fragment.getListView();
    if (recyclerView == null) {
      return;
    }

    Context context = recyclerView.getContext();
    float density = context.getResources().getDisplayMetrics().density;
    int horizontalPadding = Math.round(16f * density);
    int topPadding = Math.round(8f * density);
    int bottomPadding = Math.round(24f * density);

    recyclerView.setPadding(horizontalPadding, topPadding, horizontalPadding, bottomPadding);
    recyclerView.setClipToPadding(false);
    recyclerView.setBackgroundColor(context.getColor(R.color.dialer_surface));

    for (int i = recyclerView.getItemDecorationCount() - 1; i >= 0; i--) {
      if (recyclerView.getItemDecorationAt(i) instanceof ExpressiveCardDecoration) {
        recyclerView.removeItemDecorationAt(i);
      }
    }
    recyclerView.addItemDecoration(new ExpressiveCardDecoration(context));

    final int categoryTextColor = context.getColor(R.color.dialer_primary);
    final int iconTintColor = context.getColor(R.color.dialer_on_surface_variant);
    recyclerView.addOnChildAttachStateChangeListener(
        new RecyclerView.OnChildAttachStateChangeListener() {
          @Override
          public void onChildViewAttachedToWindow(@NonNull View view) {
            RecyclerView.Adapter<?> rawAdapter = recyclerView.getAdapter();
            if (!(rawAdapter instanceof PreferenceGroupAdapter)) {
              return;
            }
            int position = recyclerView.getChildAdapterPosition(view);
            if (position == RecyclerView.NO_POSITION || position >= rawAdapter.getItemCount()) {
              return;
            }
            Preference item = ((PreferenceGroupAdapter) rawAdapter).getItem(position);
            if (item instanceof PreferenceCategory) {
              TextView titleView = view.findViewById(android.R.id.title);
              if (titleView != null) {
                titleView.setTextColor(categoryTextColor);
              }
            } else {
              ImageView iconView = view.findViewById(android.R.id.icon);
              if (iconView != null) {
                iconView.setImageTintList(ColorStateList.valueOf(iconTintColor));
              }
            }
          }

          @Override
          public void onChildViewDetachedFromWindow(@NonNull View view) {}
        });
  }

  private static final class ExpressiveCardDecoration extends RecyclerView.ItemDecoration {

    private final float outerRadiusPx;
    private final float innerRadiusPx;
    private final int intraGroupGapPx;
    private final int interGroupGapPx;
    private final int categoryTopGapPx;
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();
    private final Path path = new Path();

    ExpressiveCardDecoration(Context context) {
      float density = context.getResources().getDisplayMetrics().density;
      outerRadiusPx = 24f * density;
      innerRadiusPx = 6f * density;
      intraGroupGapPx = Math.round(2f * density);
      interGroupGapPx = Math.round(14f * density);
      categoryTopGapPx = Math.round(8f * density);

      cardPaint.setStyle(Paint.Style.FILL);
      cardPaint.setColor(context.getColor(R.color.dialer_surface_container));
    }

    @Override
    public void getItemOffsets(
        @NonNull Rect outRect,
        @NonNull View view,
        @NonNull RecyclerView parent,
        @NonNull RecyclerView.State state) {
      RecyclerView.Adapter<?> rawAdapter = parent.getAdapter();
      if (!(rawAdapter instanceof PreferenceGroupAdapter)) {
        return;
      }
      PreferenceGroupAdapter adapter = (PreferenceGroupAdapter) rawAdapter;
      int position = parent.getChildAdapterPosition(view);
      if (position == RecyclerView.NO_POSITION || position >= adapter.getItemCount()) {
        return;
      }

      Preference current = adapter.getItem(position);
      if (current instanceof PreferenceCategory) {
        outRect.set(0, position == 0 ? 0 : categoryTopGapPx, 0, 0);
        return;
      }

      Preference next = position + 1 < adapter.getItemCount() ? adapter.getItem(position + 1) : null;
      boolean hasNextInGroup = next != null && !(next instanceof PreferenceCategory);
      outRect.set(0, 0, 0, hasNextInGroup ? intraGroupGapPx : interGroupGapPx);
    }

    @Override
    public void onDraw(
        @NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
      RecyclerView.Adapter<?> rawAdapter = parent.getAdapter();
      if (!(rawAdapter instanceof PreferenceGroupAdapter)) {
        return;
      }
      PreferenceGroupAdapter adapter = (PreferenceGroupAdapter) rawAdapter;
      int childCount = parent.getChildCount();
      for (int i = 0; i < childCount; i++) {
        View child = parent.getChildAt(i);
        if (child.getVisibility() != View.VISIBLE) {
          continue;
        }
        int position = parent.getChildAdapterPosition(child);
        if (position == RecyclerView.NO_POSITION || position >= adapter.getItemCount()) {
          continue;
        }
        Preference current = adapter.getItem(position);
        if (current instanceof PreferenceCategory) {
          continue;
        }

        Preference prev = position > 0 ? adapter.getItem(position - 1) : null;
        Preference next =
            position + 1 < adapter.getItemCount() ? adapter.getItem(position + 1) : null;
        boolean hasPrevInGroup = prev != null && !(prev instanceof PreferenceCategory);
        boolean hasNextInGroup = next != null && !(next instanceof PreferenceCategory);

        float topRadius = hasPrevInGroup ? innerRadiusPx : outerRadiusPx;
        float bottomRadius = hasNextInGroup ? innerRadiusPx : outerRadiusPx;
        float[] radii =
            new float[] {
              topRadius, topRadius,
              topRadius, topRadius,
              bottomRadius, bottomRadius,
              bottomRadius, bottomRadius
            };

        float ty = child.getTranslationY();
        rectF.set(child.getLeft(), child.getTop() + ty, child.getRight(), child.getBottom() + ty);
        path.reset();
        path.addRoundRect(rectF, radii, Path.Direction.CW);
        c.drawPath(path, cardPaint);
      }
    }
  }
}
