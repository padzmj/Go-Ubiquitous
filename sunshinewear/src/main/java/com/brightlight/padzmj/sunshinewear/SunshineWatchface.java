/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.brightlight.padzmj.sunshinewear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchface extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);


    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);




    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    private static final String TAG = SunshineWatchface.class.getSimpleName();

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchface.Engine> mWeakReference;

        public EngineHandler(SunshineWatchface.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchface.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }


    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;

        private static final String WEATHER_PATH = "/weather";
        private static final String WEATHER_PATH_DATA = "/weather-data";

        private static final String KEY_UUID = "uuid";
        private static final String KEY_LOW = "low";
        private static final String KEY_HIGH = "high";
        private static final String KEY_WEATHER_ID = "weatherID";

        //Paint
        Paint mBackgroundPaint;
        Paint mTimeTextPaint;
        Paint mDateTextPaint;
        Paint mDateTextAmbientPaint;
        Paint mTempTextLowAmbientPaint;

        Paint mWeatherLowPaint;
        Paint mWeatherHighPaint;

        boolean mAmbient;

        Calendar mCalendar;

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchface.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                // Request access only to the Wearable API
                .addApi(Wearable.API)
                .build();

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        float mXOffset;
        float mYOffset;

        float mTempYOffset;
        float mDividerYOffset;
        float mDateYOffset;

        String mWeatherHigh;
        String mWeatherLow;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchface.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineWatchface.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTimeTextPaint = new Paint();
            mTimeTextPaint = createTextPaint(resources.getColor(R.color.digital_text), NORMAL_TYPEFACE);

            mDateTextPaint = createTextPaint(resources.getColor(R.color.primary_light), NORMAL_TYPEFACE);
            mDateTextAmbientPaint = createTextPaint(Color.WHITE, NORMAL_TYPEFACE);

            mWeatherHighPaint = new Paint();
            mWeatherHighPaint = createTextPaint(resources.getColor(R.color.digital_text), NORMAL_TYPEFACE);

            mWeatherLowPaint = new Paint();
            mWeatherLowPaint = createTextPaint(resources.getColor(R.color.digital_text), NORMAL_TYPEFACE);

            mTempTextLowAmbientPaint = createTextPaint(Color.WHITE, NORMAL_TYPEFACE);

            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor, Typeface typeFace) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(typeFace);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {

                mGoogleApiClient.connect();

                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchface.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchface.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchface.this.getResources();
            boolean isRound = insets.isRound();

            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);

            mDateYOffset = resources.getDimension(isRound
                    ? R.dimen.digital_date_y_offset_round : R.dimen.digital_date_y_offset);

            mDividerYOffset = resources.getDimension(isRound
                    ? R.dimen.digital_divider_y_offset_round : R.dimen.digital_divider_y_offset);

            mTempYOffset = resources.getDimension(isRound ?
                    R.dimen.digital_weather_y_offset_round : R.dimen.digital_weather_y_offset);


            float timeTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            float dateSize = resources.getDimension(R.dimen.digital_date_text_size);

            float tempHighTextSize = resources.getDimension(isRound ?
                    R.dimen.digital_temp_high_text_size_round : R.dimen.digital_temp_high_text_size);


            float tempLowTextSize = resources.getDimension(isRound ?
                    R.dimen.digital_temp_low_text_size_round : R.dimen.digital_temp_low_text_size);


            //Time
            mTimeTextPaint.setTextSize(timeTextSize);

            //Date
            mDateTextPaint.setTextSize(dateSize);

            //Temp
            mWeatherHighPaint.setTextSize(tempHighTextSize);
            mWeatherLowPaint.setTextSize(tempLowTextSize);
            mDateTextAmbientPaint.setTextSize(dateSize);
            mTempTextLowAmbientPaint.setTextSize(tempLowTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimeTextPaint.setAntiAlias(!inAmbientMode);
                    mDateTextPaint.setAntiAlias(!inAmbientMode);
                    mDateTextAmbientPaint.setAntiAlias(!inAmbientMode);
                    mTempTextLowAmbientPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            String text = mAmbient
                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format("%d:%02d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));


            float xOffsetText = mTimeTextPaint.measureText(text) / 2;

            canvas.drawText(text, bounds.centerX() - xOffsetText, mYOffset, mTimeTextPaint);


            //Date
            Paint datePaint = mAmbient ? mDateTextAmbientPaint : mDateTextPaint;

            Resources resources = getResources();

            int dayOfMonth = mCalendar.get(Calendar.DAY_OF_MONTH);
            int year = mCalendar.get(Calendar.YEAR);

            String dayOfWeekString   = Utility.getDayOfWeekString(resources, mCalendar.get(Calendar.DAY_OF_WEEK));
            String monthOfYearString = Utility.getMonthOfYearString(resources, mCalendar.get(Calendar.MONTH));

            String dateText = String.format("%s, %s %d %d", dayOfWeekString, monthOfYearString, dayOfMonth, year);

            float xOffsetDate = datePaint.measureText(dateText) / 2;

            canvas.drawText(dateText, bounds.centerX() - xOffsetDate, mDateYOffset, datePaint);

            float yLineOffset = mDateYOffset + 30;

            //Line
            canvas.drawLine(bounds.centerX() - 20, yLineOffset, bounds.centerX() + 20, yLineOffset, datePaint);

            //Temp
            String lowTemp, highTemp;

            highTemp = mWeatherHigh;
            lowTemp = mWeatherLow;

            if (mWeatherHigh != null && mWeatherLow != null) {
                // Draw a line to separate date and time from weather elements
                canvas.drawLine(bounds.centerX() - 20, mDividerYOffset, bounds.centerX() + 20, mDividerYOffset, datePaint);

                float highTextLen = mWeatherHighPaint.measureText(mWeatherHigh);

                if (mAmbient) {
                    float xOffset = bounds.centerX() - 20;
                    canvas.drawText(highTemp, xOffset, mTempYOffset, mWeatherHighPaint);
                    canvas.drawText(lowTemp, xOffset + highTextLen + 20, mTempYOffset, mWeatherLowPaint);
                } else {
                    float xOffset = bounds.centerX() + 40;
                    canvas.drawText(highTemp, xOffset, mTempYOffset, mWeatherHighPaint);
                    canvas.drawText(lowTemp, bounds.centerX() + (highTextLen / 2) + 20, mTempYOffset, mWeatherLowPaint);
                }
            }

//            canvas.drawText(highTemp, bounds.centerX() - 20, mTempYOffset, mWeatherHighPaint);
//            canvas.drawText(lowTemp, bounds.centerX() + 40, mTempYOffset, mWeatherLowPaint);


        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for (DataEvent dataEvent : dataEventBuffer) {
                Log.d(TAG, dataEventBuffer.getStatus().toString());

                if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                    DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                    String path = dataEvent.getDataItem().getUri().getPath();
                    Log.d(TAG, path);
                    if (path.equals(WEATHER_PATH)) {
                        if (dataMap.containsKey(KEY_HIGH)) {
                            mWeatherHigh = dataMap.getString(KEY_HIGH);
                            Log.d(TAG, "High = " + mWeatherHigh);
                        } else {
                            Log.d(TAG, "No high!!!");
                        }

                        if (dataMap.containsKey(KEY_LOW)) {
                            mWeatherLow = dataMap.getString(KEY_LOW);
                            Log.d(TAG, "Low = " + mWeatherLow);
                        } else {
                            Log.d(TAG, "No low!!");
                        }

                        invalidate();
                    }
                }
            }


//            for (DataEvent event : dataEventBuffer) {
//                if (event.getType() == DataEvent.TYPE_CHANGED) {
//
//                    Log.d(TAG, "DataItem changed: " + event.getDataItem().getUri());
//
//                    // DataItem changed
//                    DataItem item = event.getDataItem();
//                    if (item.getUri().getPath().equals(WEATHER_PATH)) {
//                        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
//
//                        Log.d(TAG, "DATA " + dataMap.size());
//                    }
//                } else if (event.getType() == DataEvent.TYPE_DELETED) {
//                    // DataItem deleted
//                }
//            }

//            for (DataEvent event : dataEventBuffer) {
//                if (event.getType() == DataEvent.TYPE_CHANGED) {
//                    // DataItem changed
////                    DataItem item = event.getDataItem();
//                    DataMap dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
//
//                    String path = event.getDataItem().getUri().getPath();
//                    Log.d(TAG, path);
//
//                    if (path.equals(WEATHER_PATH)) {
//
//                        Log.d(TAG, "Size " + dataMap.get(KEY_HIGH));
//
//                        if (dataMap.containsKey(KEY_HIGH)) {
//                            mWeatherHigh = dataMap.getString(KEY_HIGH);
//                            Log.d(TAG, "High = " + mWeatherHigh);
//                        } else {
//                            Log.d(TAG, "What? No high?");
//                        }
//
//                        if (dataMap.containsKey(KEY_LOW)) {
//                            mWeatherLow = dataMap.getString(KEY_LOW);
//                            Log.d(TAG, "Low = " + mWeatherLow);
//                        } else {
//                            Log.d(TAG, "What? No low?");
//                        }
//                    }
//                }
//            }
        }

        @Override
        public void onConnected(Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);

            requestWeatherData();
            Log.d(TAG, "onConnected");
        }


        private void requestWeatherData() {
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(WEATHER_PATH);
            putDataMapRequest.getDataMap().putString(KEY_UUID, UUID.randomUUID().toString());
            PutDataRequest request = putDataMapRequest.asPutDataRequest();

            Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                    .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                        @Override
                        public void onResult(DataApi.DataItemResult dataItemResult) {
                            if (!dataItemResult.getStatus().isSuccess()) {
                                Log.d(TAG, "Failed asking phone for weather data");
                            } else {
                                Log.d(TAG, "Successfully asked for weather data");
                            }
                        }
                    });
        }


        @Override
        public void onConnectionSuspended(int i) {
            Log.d(TAG, "onConnectionSuspended");

            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.d(TAG, "onConnectionFailed");

        }
    }
}
