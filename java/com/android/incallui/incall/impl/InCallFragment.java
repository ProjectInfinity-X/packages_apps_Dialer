/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2023-2024 The LineageOS Project
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

package com.android.incallui.incall.impl;

import android.Manifest;
import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import java.util.Timer;
import java.util.TimerTask;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.Insets;
import android.view.WindowManager;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.drawable.GradientDrawable;
import android.animation.ValueAnimator;
import com.android.incallui.call.state.DialerCallState;
import android.util.Log;
import android.view.animation.DecelerateInterpolator;
import android.telecom.CallAudioState;
import android.telephony.TelephonyManager;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.android.dialer.R;
import com.android.dialer.common.Assert;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.multimedia.MultimediaData;
import com.android.dialer.widget.LockableViewPager;
import com.android.incallui.audioroute.AudioRouteSelectorDialogFragment;
import com.android.incallui.audioroute.AudioRouteSelectorDialogFragment.AudioRouteSelectorPresenter;
import com.android.incallui.contactgrid.ContactGridManager;
import com.android.incallui.hold.OnHoldFragment;
import com.android.incallui.incall.impl.ButtonController.CallRecordButtonController;
import com.android.incallui.incall.impl.ButtonController.SpeakerButtonController;
import com.android.incallui.incall.impl.ButtonController.UpgradeToRttButtonController;
import com.android.incallui.incall.impl.InCallButtonGridFragment.OnButtonGridCreatedListener;
import com.android.incallui.incall.protocol.InCallButtonIds;
import com.android.incallui.incall.protocol.InCallButtonIdsExtension;
import com.android.incallui.incall.protocol.InCallButtonUi;
import com.android.incallui.incall.protocol.InCallButtonUiDelegate;
import com.android.incallui.incall.protocol.InCallButtonUiDelegateFactory;
import com.android.incallui.incall.protocol.InCallScreen;
import com.android.incallui.incall.protocol.InCallScreenDelegate;
import com.android.incallui.incall.protocol.InCallScreenDelegateFactory;
import com.android.incallui.incall.protocol.PrimaryCallState;
import com.android.incallui.incall.protocol.PrimaryCallState.ButtonState;
import com.android.incallui.incall.protocol.PrimaryInfo;
import com.android.incallui.incall.protocol.SecondaryInfo;

import java.util.ArrayList;
import java.util.List;
import android.view.WindowManager;
import com.android.incallui.incall.protocol.ContactPhotoType;

/** Fragment that shows UI for an ongoing voice call. */
public class InCallFragment extends Fragment
    implements InCallScreen,
        InCallButtonUi,
        OnClickListener,
        AudioRouteSelectorPresenter,
        OnButtonGridCreatedListener {

  private final List<ButtonController> buttonControllers = new ArrayList<>();
  private View endCallButton;
  private InCallPaginator paginator;
  private LockableViewPager pager;
  private InCallPagerAdapter adapter;
  private ContactGridManager contactGridManager;
  private InCallScreenDelegate inCallScreenDelegate;
  private InCallButtonUiDelegate inCallButtonUiDelegate;
  private InCallButtonGridFragment inCallButtonGridFragment;
  @Nullable
  private ButtonChooser buttonChooser;
  private SecondaryInfo savedSecondaryInfo;
  private int voiceNetworkType;
  private int phoneType;
  private boolean stateRestored;
  private boolean userDeniedBluetooth;

  private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
          new ActivityResultContracts.RequestMultiplePermissions(),
          grantResults -> {
            boolean allGranted = grantResults.values().stream().allMatch(x -> x);
            if (allGranted) {
              inCallButtonUiDelegate.callRecordClicked(true);
            }
          });

  private final ActivityResultLauncher<String[]> bluetoothPermissionLauncher =
          registerForActivityResult(
                  new ActivityResultContracts.RequestMultiplePermissions(),
                  grantResults -> {
                    boolean allGranted = grantResults.values().stream().allMatch(x -> x);
                    inCallButtonUiDelegate.showAudioRouteSelector();
                    if (!allGranted) {
                      userDeniedBluetooth = true;
                    }
                  });

  // Add animation to educate users. If a call has enriched calling attachments then we'll
  // initially show the attachment page. After a delay seconds we'll animate to the button grid.
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable pagerRunnable =
      new Runnable() {
        @Override
        public void run() {
          pager.setCurrentItem(adapter.getButtonGridPosition());
        }
      };

  private static boolean isSupportedButton(@InCallButtonIds int id) {
    return id == InCallButtonIds.BUTTON_AUDIO
        || id == InCallButtonIds.BUTTON_MUTE
        || id == InCallButtonIds.BUTTON_DIALPAD
        || id == InCallButtonIds.BUTTON_HOLD
        || id == InCallButtonIds.BUTTON_SWAP
        || id == InCallButtonIds.BUTTON_UPGRADE_TO_VIDEO
        || id == InCallButtonIds.BUTTON_ADD_CALL
        || id == InCallButtonIds.BUTTON_MERGE
        || id == InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE
        || id == InCallButtonIds.BUTTON_SWAP_SIM
        || id == InCallButtonIds.BUTTON_UPGRADE_TO_RTT
        || id == InCallButtonIds.BUTTON_RECORD_CALL;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    if (savedSecondaryInfo != null) {
      setSecondary(savedSecondaryInfo);
    }
  }

    Timer timer = new Timer();
    TimerTask timerTask;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    inCallButtonUiDelegate =
        FragmentUtils.getParent(this, InCallButtonUiDelegateFactory.class)
            .newInCallButtonUiDelegate();
    if (savedInstanceState != null) {
      stateRestored = true;
    }
  }

  private ImageView avatarImageView;

  @Nullable
  @Override
  @SuppressLint("MissingPermission")
  public View onCreateView(
      @NonNull LayoutInflater layoutInflater,
      @Nullable ViewGroup viewGroup,
      @Nullable Bundle bundle) {
    LogUtil.i("InCallFragment.onCreateView", null);
    getActivity().setTheme(R.style.Theme_InCallScreen);
        Window window = getActivity().getWindow();
	window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.setNavigationBarContrastEnforced(false);
        window.setDecorFitsSystemWindows(false);
        window.getDecorView().setSystemUiVisibility(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

    // Bypass to avoid StrictModeResourceMismatchViolation
    final View view = layoutInflater.inflate(R.layout.frag_incall_voice, viewGroup, false);
    avatarImageView = (ImageView) view.findViewById(R.id.contactgrid_avatar);
    contactGridManager =
        new ContactGridManager(
            view,
            avatarImageView,
            getResources().getDimensionPixelSize(R.dimen.incall_avatar_size),
            true /* showAnonymousAvatar */);
    contactGridManager.onMultiWindowModeChanged(getActivity().isInMultiWindowMode());

    paginator = (InCallPaginator) view.findViewById(R.id.incall_paginator);
    pager = (LockableViewPager) view.findViewById(R.id.incall_pager);
    pager.setOnTouchListener(
        (v, event) -> {
          handler.removeCallbacks(pagerRunnable);
          return false;
        });

    endCallButton = view.findViewById(R.id.incall_end_call);
    endCallButton.setOnClickListener(this);

    if (ContextCompat.checkSelfPermission(getContext(), permission.READ_PHONE_STATE)
        != PackageManager.PERMISSION_GRANTED) {
      voiceNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
    } else {
      voiceNetworkType =
          getContext().getSystemService(TelephonyManager.class).getVoiceNetworkType();
    }
    // TODO(a bug): Change to use corresponding phone type used for current call.
    phoneType = getContext().getSystemService(TelephonyManager.class).getPhoneType();

    // Workaround to adjust padding for status bar and navigation bar since fitsSystemWindows
    // doesn't work well when switching with other fragments.
    view.addOnAttachStateChangeListener(
        new OnAttachStateChangeListener() {
          @Override
          public void onViewAttachedToWindow(View v) {
            View container = v.findViewById(R.id.incall_ui_container);
            Insets insets = v.getRootWindowInsets().getInsets(WindowInsets.Type.systemBars());
            int topInset = insets.top;
            int bottomInset = insets.bottom;
            if (topInset != container.getPaddingTop()) {
              TransitionManager.beginDelayedTransition(((ViewGroup) container.getParent()));
              container.setPadding(0, topInset, 0, bottomInset);
            }
          }

          @Override
          public void onViewDetachedFromWindow(View v) {}
        });
    view.setFitsSystemWindows(false);
    return view;
  }

  @Override
  public void onResume() {
    super.onResume();
    inCallScreenDelegate.onInCallScreenResumed();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle bundle) {
    LogUtil.i("InCallFragment.onViewCreated", null);
    super.onViewCreated(view, bundle);
    inCallScreenDelegate =
        FragmentUtils.getParent(this, InCallScreenDelegateFactory.class).newInCallScreenDelegate();
    Assert.isNotNull(inCallScreenDelegate);

    buttonControllers.add(new ButtonController.MuteButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.SpeakerButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.DialpadButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.HoldButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.AddCallButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.SwapButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.MergeButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.SwapSimButtonController(inCallButtonUiDelegate));
    buttonControllers.add(
        new ButtonController.UpgradeToVideoButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new UpgradeToRttButtonController(inCallButtonUiDelegate));
    buttonControllers.add(
        new ButtonController.ManageConferenceButtonController(inCallScreenDelegate));
    buttonControllers.add(
        new ButtonController.SwitchToSecondaryButtonController(inCallScreenDelegate));
    buttonControllers.add(new ButtonController.CallRecordButtonController(inCallButtonUiDelegate));

    inCallScreenDelegate.onInCallScreenDelegateInit(this);
    inCallScreenDelegate.onInCallScreenReady();
  }

  @Override
  public void onPause() {
    super.onPause();
    inCallScreenDelegate.onInCallScreenPaused();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    inCallScreenDelegate.onInCallScreenUnready();
  }

  @Override
  public void onClick(View view) {
    if (view == endCallButton) {
      LogUtil.i("InCallFragment.onClick", "end call button clicked");
      inCallScreenDelegate.onEndCallClicked();
    } else {
      LogUtil.e("InCallFragment.onClick", "unknown view: " + view);
      Assert.createAssertionFailException("");
    }
  }

  @Override
  public void setPrimary(@NonNull PrimaryInfo primaryInfo) {
    LogUtil.i("InCallFragment.setPrimary", primaryInfo.toString());
    setAdapterMedia(primaryInfo.multimediaData(), primaryInfo.showInCallButtonGrid());
    contactGridManager.setPrimary(primaryInfo);
  }

  private void setAdapterMedia(MultimediaData multimediaData, boolean showInCallButtonGrid) {
    if (adapter == null) {
      adapter =
          new InCallPagerAdapter(getChildFragmentManager(), multimediaData, showInCallButtonGrid);
      pager.setAdapter(adapter);
    } else {
      adapter.setAttachments(multimediaData);
    }

    if (adapter.getCount() > 1 && getResources().getInteger(R.integer.incall_num_rows) > 1) {
      paginator.setVisibility(View.VISIBLE);
      paginator.setupWithViewPager(pager);
      pager.setSwipingLocked(false);
      if (!stateRestored) {
        handler.postDelayed(pagerRunnable, 4_000);
      } else {
        pager.setCurrentItem(adapter.getButtonGridPosition(), false /* animateScroll */);
      }
    } else {
      paginator.setVisibility(View.GONE);
    }
  }

  @Override
  public void setSecondary(@NonNull SecondaryInfo secondaryInfo) {
    LogUtil.i("InCallFragment.setSecondary", secondaryInfo.toString());
    updateButtonStates();

    if (!isAdded()) {
      savedSecondaryInfo = secondaryInfo;
      return;
    }
    savedSecondaryInfo = null;
    FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
    Fragment oldBanner = getChildFragmentManager().findFragmentById(R.id.incall_on_hold_banner);
    if (secondaryInfo.shouldShow()) {
      transaction.replace(R.id.incall_on_hold_banner, OnHoldFragment.newInstance(secondaryInfo));
    } else {
      if (oldBanner != null) {
        transaction.remove(oldBanner);
      }
    }
    transaction.setCustomAnimations(R.anim.abc_slide_in_top, R.anim.abc_slide_out_top);
    transaction.commitNowAllowingStateLoss();
  }

  @Override
  public void setCallState(@NonNull PrimaryCallState primaryCallState) {
    LogUtil.i("InCallFragment.setCallState", primaryCallState.toString());
    contactGridManager.setCallState(primaryCallState);
    getButtonController(InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY)
        .setAllowed(primaryCallState.swapToSecondaryButtonState() != ButtonState.NOT_SUPPORT);
    getButtonController(InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY)
        .setEnabled(primaryCallState.swapToSecondaryButtonState() == ButtonState.ENABLED);
    buttonChooser =
        ButtonChooserFactory.newButtonChooser(
            voiceNetworkType, primaryCallState.isWifi(), phoneType);
    updateButtonStates();
  }

  @Override
  public void setEndCallButtonEnabled(boolean enabled, boolean animate) {
    if (endCallButton != null) {
      endCallButton.setEnabled(enabled);
    }
  }

  @Override
  public void showManageConferenceCallButton(boolean visible) {
    getButtonController(InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE).setAllowed(visible);
    getButtonController(InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE).setEnabled(visible);
    updateButtonStates();
  }

  @Override
  public boolean isManageConferenceVisible() {
    return getButtonController(InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE).isAllowed();
  }

  @Override
  public void dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
    contactGridManager.dispatchPopulateAccessibilityEvent(event);
  }

  @Override
  public void showNoteSentToast() {
    LogUtil.i("InCallFragment.showNoteSentToast", null);
    Toast.makeText(getContext(), R.string.incall_note_sent, Toast.LENGTH_LONG).show();
  }

  @Override
  public void updateInCallScreenColors() {}

  @Override
  public void onInCallScreenDialpadVisibilityChange(boolean isShowing) {
    LogUtil.i("InCallFragment.onInCallScreenDialpadVisibilityChange", "isShowing: " + isShowing);
    // Take note that the dialpad button isShowing
    getButtonController(InCallButtonIds.BUTTON_DIALPAD).setChecked(isShowing);

    // This check is needed because there is a race condition where we attempt to update
    // ButtonGridFragment before it is ready, so we check whether it is ready first and once it is
    // ready, #onButtonGridCreated will mark the dialpad button as isShowing.
    if (inCallButtonGridFragment != null) {
      // Update the Android Button's state to isShowing.
      inCallButtonGridFragment.onInCallScreenDialpadVisibilityChange(isShowing);
    }
    Activity activity = getActivity();
    Window window = activity.getWindow();
    window.setNavigationBarColor(
        activity.getColor(
            isShowing ? android.R.color.background_dark : android.R.color.transparent));
  }

  @Override
  public int getAnswerAndDialpadContainerResourceId() {
    return R.id.incall_dialpad_container;
  }

  @Override
  public Fragment getInCallScreenFragment() {
    return this;
  }

  @Override
  public void showButton(@InCallButtonIds int buttonId, boolean show) {
    LogUtil.v(
        "InCallFragment.showButton",
        "buttionId: %s, show: %b",
        InCallButtonIdsExtension.toString(buttonId),
        show);
    if (isSupportedButton(buttonId)) {
      getButtonController(buttonId).setAllowed(show);
    }
  }

  @Override
  public void enableButton(@InCallButtonIds int buttonId, boolean enable) {
    LogUtil.v(
        "InCallFragment.enableButton",
        "buttonId: %s, enable: %b",
        InCallButtonIdsExtension.toString(buttonId),
        enable);
    if (isSupportedButton(buttonId)) {
      getButtonController(buttonId).setEnabled(enable);
    }
  }

  @Override
  public void setEnabled(boolean enabled) {
    LogUtil.v("InCallFragment.setEnabled", "enabled: " + enabled);
    for (ButtonController buttonController : buttonControllers) {
      buttonController.setEnabled(enabled);
    }
  }

  @Override
  public void setHold(boolean value) {
    getButtonController(InCallButtonIds.BUTTON_HOLD).setChecked(value);
  }

  @Override
  public void setCameraSwitched(boolean isBackFacingCamera) {}

  @Override
  public void setVideoPaused(boolean isPaused) {}

  @Override
  public void setAudioState(CallAudioState audioState) {
    LogUtil.i("InCallFragment.setAudioState", "audioState: " + audioState);
    ((SpeakerButtonController) getButtonController(InCallButtonIds.BUTTON_AUDIO))
        .setAudioState(audioState);
    getButtonController(InCallButtonIds.BUTTON_MUTE).setChecked(audioState.isMuted());
  }

  @Override
  public void setCallRecordingState(boolean isRecording) {
    ((CallRecordButtonController) getButtonController(InCallButtonIds.BUTTON_RECORD_CALL))
        .setRecordingState(isRecording);
  }

  @Override
  public void setCallRecordingDuration(long durationMs) {
    ((CallRecordButtonController) getButtonController(InCallButtonIds.BUTTON_RECORD_CALL))
        .setRecordingDuration(durationMs);
  }

  @Override
  public void requestCallRecordingPermissions(String[] permissions) {
    permissionLauncher.launch(permissions);
  }

  @Override
  public void updateButtonStates() {
    // When the incall screen is ready, this method is called from #setSecondary, even though the
    // incall button ui is not ready yet. This method is called again once the incall button ui is
    // ready though, so this operation is safe and will be executed asap.
    if (inCallButtonGridFragment == null) {
      return;
    }
    int numVisibleButtons =
        inCallButtonGridFragment.updateButtonStates(
            buttonControllers, buttonChooser, voiceNetworkType, phoneType);

    int visibility = numVisibleButtons == 0 ? View.GONE : View.VISIBLE;
    pager.setVisibility(visibility);
    if (adapter != null
        && adapter.getCount() > 1
        && getResources().getInteger(R.integer.incall_num_rows) > 1) {
      paginator.setVisibility(View.VISIBLE);
      pager.setSwipingLocked(false);
    } else {
      paginator.setVisibility(View.GONE);
      if (adapter != null) {
        pager.setSwipingLocked(true);
        pager.setCurrentItem(adapter.getButtonGridPosition());
      }
    }
  }

  @Override
  public Fragment getInCallButtonUiFragment() {
    return this;
  }

  @Override
  public void showAudioRouteSelector() {
    String[] permissions = new String[]{Manifest.permission.BLUETOOTH_CONNECT};
    if (hasAllPermissions(permissions) || userDeniedBluetooth) {
      AudioRouteSelectorDialogFragment.newInstance(inCallButtonUiDelegate.getCurrentAudioState())
              .show(getChildFragmentManager(), null);
    } else {
      bluetoothPermissionLauncher.launch(permissions);
    }
  }

  private boolean hasAllPermissions(String[] permissions) {
    for (String p : permissions) {
      if (requireContext().checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void onAudioRouteSelected(int audioRoute) {
    inCallButtonUiDelegate.setAudioRoute(audioRoute);
  }

  @Override
  public void onAudioRouteSelectorDismiss() {}

  @NonNull
  @Override
  public ButtonController getButtonController(@InCallButtonIds int id) {
    for (ButtonController buttonController : buttonControllers) {
      if (buttonController.getInCallButtonId() == id) {
        return buttonController;
      }
    }
    Assert.createAssertionFailException("");
    return null;
  }

  @Override
  public void onButtonGridCreated(InCallButtonGridFragment inCallButtonGridFragment) {
    LogUtil.i("InCallFragment.onButtonGridCreated", "InCallUiReady");
    this.inCallButtonGridFragment = inCallButtonGridFragment;
    inCallButtonUiDelegate.onInCallButtonUiReady(this);
    updateButtonStates();
  }

  @Override
  public void onButtonGridDestroyed() {
    LogUtil.i("InCallFragment.onButtonGridCreated", "InCallUiUnready");
    inCallButtonUiDelegate.onInCallButtonUiUnready();
    this.inCallButtonGridFragment = null;
  }

  @Override
  public boolean isShowingLocationUi() {
    Fragment fragment = getLocationFragment();
    return fragment != null && fragment.isVisible();
  }

  @Override
  public void showLocationUi(@Nullable Fragment locationUi) {
    boolean isVisible = isShowingLocationUi();
    if (locationUi != null && !isVisible) {
      // Show the location fragment.
      getChildFragmentManager()
          .beginTransaction()
          .replace(R.id.incall_location_holder, locationUi)
          .commitAllowingStateLoss();
    } else if (locationUi == null && isVisible) {
      // Hide the location fragment
      getChildFragmentManager()
          .beginTransaction()
          .remove(getLocationFragment())
          .commitAllowingStateLoss();
    }
  }

  @Override
  public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
    super.onMultiWindowModeChanged(isInMultiWindowMode);
    if (isInMultiWindowMode == isShowingLocationUi()) {
      LogUtil.i("InCallFragment.onMultiWindowModeChanged", "hide = " + isInMultiWindowMode);
      // Need to show or hide location
      showLocationUi(isInMultiWindowMode ? null : getLocationFragment());
    }
    contactGridManager.onMultiWindowModeChanged(isInMultiWindowMode);
  }

  private Fragment getLocationFragment() {
    return getChildFragmentManager().findFragmentById(R.id.incall_location_holder);
  }
}
