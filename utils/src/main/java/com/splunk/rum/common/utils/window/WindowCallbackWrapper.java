/*
Copyright 2026 Splunk Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.splunk.rum.common.utils.window;

import android.os.Build;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.List;

/*
 This class must be written in Java because of method onMenuOpened. It can not be overridden in Kotlin with nullable type but null is received in LeakCanary and it cause a crash.
 */
public class WindowCallbackWrapper implements Window.Callback {

	@Nullable
	private Window.Callback callback;

	public WindowCallbackWrapper(@Nullable Window.Callback callback) {
		this.callback = callback;
	}

	@Nullable
	public Window.Callback getCallback() {
		return callback;
	}

	public void setCallback(@Nullable Window.Callback callback) {
		this.callback = callback;
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		return callback != null && callback.dispatchKeyEvent(event);
	}

	@Override
	public boolean dispatchKeyShortcutEvent(KeyEvent event) {
		return callback != null && callback.dispatchKeyShortcutEvent(event);
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		return callback != null && callback.dispatchTouchEvent(event);
	}

	@Override
	public boolean dispatchTrackballEvent(MotionEvent event) {
		return callback != null && callback.dispatchTrackballEvent(event);
	}

	@Override
	public boolean dispatchGenericMotionEvent(MotionEvent event) {
		return callback != null && callback.dispatchGenericMotionEvent(event);
	}

	@Override
	public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
		return callback != null && callback.dispatchPopulateAccessibilityEvent(event);
	}

	@Nullable
	@Override
	public View onCreatePanelView(int featureId) {
		return callback != null ? callback.onCreatePanelView(featureId) : null;
	}

	@Override
	public boolean onCreatePanelMenu(int featureId, @NonNull Menu menu) {
		return callback != null && callback.onCreatePanelMenu(featureId, menu);
	}

	@Override
	public boolean onPreparePanel(int featureId, @Nullable View view, @NonNull Menu menu) {
		return callback != null && callback.onPreparePanel(featureId, view, menu);
	}

	@Override
	public boolean onMenuOpened(int featureId, @NonNull Menu menu) {
		return callback != null && callback.onMenuOpened(featureId, menu);
	}

	@Override
	public boolean onMenuItemSelected(int featureId, @NonNull MenuItem item) {
		return callback != null && callback.onMenuItemSelected(featureId, item);
	}

	@Override
	public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) {
		if (callback != null)
			callback.onWindowAttributesChanged(attrs);
	}

	@Override
	public void onContentChanged() {
		if (callback != null)
			callback.onContentChanged();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		if (callback != null)
			callback.onWindowFocusChanged(hasFocus);
	}

	@Override
	public void onAttachedToWindow() {
		if (callback != null)
			callback.onAttachedToWindow();
	}

	@Override
	public void onDetachedFromWindow() {
		if (callback != null)
			callback.onDetachedFromWindow();
	}

	@Override
	public void onPanelClosed(int featureId, @NonNull Menu menu) {
		if (callback != null)
			callback.onPanelClosed(featureId, menu);
	}

	@Override
	public boolean onSearchRequested() {
		return callback != null && callback.onSearchRequested();
	}

	@Override
	@RequiresApi(api = Build.VERSION_CODES.M)
	public boolean onSearchRequested(SearchEvent searchEvent) {
		return callback != null && callback.onSearchRequested(searchEvent);
	}

	@Nullable
	@Override
	public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
		return this.callback != null ? this.callback.onWindowStartingActionMode(callback) : null;
	}

	@Nullable
	@Override
	@RequiresApi(api = Build.VERSION_CODES.M)
	public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type) {
		return this.callback != null ? this.callback.onWindowStartingActionMode(callback, type) : null;
	}

	@Override
	public void onActionModeStarted(ActionMode mode) {
		if (callback != null)
			callback.onActionModeStarted(mode);
	}

	@Override
	public void onActionModeFinished(ActionMode mode) {
		if (callback != null)
			callback.onActionModeFinished(mode);
	}


	@Override
	@RequiresApi(api = Build.VERSION_CODES.N)
	public void onProvideKeyboardShortcuts(List<KeyboardShortcutGroup> data, @Nullable Menu menu, int deviceId) {
		if (callback != null)
			callback.onProvideKeyboardShortcuts(data, menu, deviceId);
	}

	@Override
	@RequiresApi(api = Build.VERSION_CODES.O)
	public void onPointerCaptureChanged(boolean hasCapture) {
		if (callback != null)
			callback.onPointerCaptureChanged(hasCapture);
	}
}
