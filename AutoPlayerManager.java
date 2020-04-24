/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.tnott4.tv.player.AutoFocusPlayer;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.C.ContentType;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.tnott4.tv.R;
import com.tnott4.tv.analytics.GoogleAnalyticImpl;
import com.tnott4.tv.data.Model.MyAppStaticData;
import com.tnott4.tv.data.Model.MyMedia;
import com.tnott4.tv.player.lib.MyPlayerEvent;
import com.tnott4.tv.root.AppConst;
import com.tnott4.tv.utils.LogUtil;
import com.tnott4.tv.utils.UtilityClass;
import com.tnott4.tv.view.MainActivity;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Manages the {@link ExoPlayer}, the IMA plugin and all video playback.
 */
/* package */
public final class AutoPlayerManager implements AdsMediaSource.MediaSourceFactory,
        Player.EventListener, MyPlayerEvent {
    private static final String TAG = "Player Manager";
    private boolean isAdPlaying = false;
    private final DataSource.Factory dataSourceFactory;

    private SimpleExoPlayer player;
    private long contentPosition;
    private MyPlayerEvent myPlayerEvent;

    private boolean startAutoPlay = true;
    private int startWindow = 0;
    private long startPosition = 0;
    private long playedVideoDuration = 0, totalVideoDuration = 0;
    private long videoCurrentDurationToStore = 0;
    private Activity activity;
    private MyMedia currentMedia;
    private boolean flag25 = false, flag50 = false, flag75 = false;
    private PlayerView playerView;
    private MyAppStaticData myAppStaticData;

    private TimerTask timerTask;
    private GoogleAnalyticImpl googleAnalytic;
    private UtilityClass utilityClass;
    private String screenName, appMenuName;
    private boolean isVideoCompleteCalled = false;
    private Timer timer;
    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            if (player != null && msg.what == AppConst.MSG_PLAYTIME) {
                myPlayerEvent.onEverySecond(false);
                try {
                    LogUtil.getInstance().i(TAG, "running: ");
                    /*For VOD Media Play*/
                    if (player != null && !player.isCurrentWindowDynamic()) {

                        if (myPlayerEvent != null)
                            myPlayerEvent.playerEverySecondVOD((int) (player.getCurrentPosition() / 1000));
                            videoCurrentDurationToStore = player.getCurrentPosition();
                            playerEverySecondVOD((int) (player.getCurrentPosition() / 1000));
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
                //}
            }
            return true;
        }
    });

    public AutoPlayerManager(Activity activity, MyPlayerEvent myPlayerEvent,
                             PlayerView playerView, GoogleAnalyticImpl googleAnalytics, UtilityClass utilityClass) {
        this.myPlayerEvent = myPlayerEvent;
        this.utilityClass = utilityClass;
        this.googleAnalytic = googleAnalytics;
        this.activity = activity;
        this.playerView = playerView;
        this.dataSourceFactory = new DefaultHttpDataSourceFactory(
                activity.getString(R.string.app_name),
                DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
                /* allowCrossProtocolRedirects= */ true);

    }

    public String getFeedUrlHolder() {
        return feedUrlHolder;
    }

    private String feedUrlHolder = "";

    public void init(MyMedia media, String flagMenu) {
        this.appMenuName = flagMenu;
        this.currentMedia = media;
        feedUrlHolder = media.getVideo().getUrl();
        try {
            DefaultTrackSelector trackSelector =
                    new DefaultTrackSelector();
            trackSelector.setParameters(new DefaultTrackSelector.ParametersBuilder()
                    .setRendererDisabled(C.TRACK_TYPE_VIDEO, false)
                    .build());
            player = ExoPlayerFactory.newSimpleInstance(activity, trackSelector);
            playerView.setUseController(false);
            playerView.setPlayer(player);

            // This is the MediaSource representing the content media (i.e. not the ad).
            MediaSource contentMediaSource = buildMediaSource(Uri.parse(feedUrlHolder));

            player.seekTo(contentPosition);
            player.prepare(contentMediaSource);
            player.setPlayWhenReady(startAutoPlay);
            player.addListener(this);
            /*Handle Player Events*/
            player.addListener(new PlayerEventListener(feedUrlHolder));
            player.seekTo(startWindow, startPosition);
            player.seekTo(contentPosition);
        } catch (Exception e) {
            e.getMessage();
        }
        if (currentMedia.isWatchLiveAndWeatherStatus()) {
            screenName = utilityClass.navigationName(appMenuName, currentMedia.getWatchWeatherString());
            Log.e("PLAYER ICI", appMenuName + " = " + currentMedia.getWatchWeatherString());
        } else {
            screenName = utilityClass.navigationName(appMenuName, currentMedia.getWatchWeatherString());
            Log.e("PLAYER ICI", appMenuName + " = " + currentMedia.getWatchWeatherString());
        }
        timer = new Timer();
        timerTask = new TimerTask() {
            @Override
            public void run() {
                /*Use to run code in UI Thread*/
                playerView.post(() -> handler.sendEmptyMessage(AppConst.MSG_PLAYTIME));
            }
        };
        timer.schedule(timerTask, 0, 999);
    }


    public void reset() {
        if (player != null) {
            if (!isAdPlaying)
                contentPosition = player.getContentPosition();
            player.release();
            player = null;
            cancelTask();
            LogUtil.getInstance().i(TAG, "reset: contentPosition==>" + contentPosition);

        }

    }

    private void cancelTask() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    public void play() {
        if (player != null) {
            player.setPlayWhenReady(true);
        }
    }

    public void release() {
        try {
            if (player != null) {
                player.release();
                player = null;
            }
            cancelTask();
            if (!isVideoCompleteCalled && isVideoPlayCalled) {
                trackEventWithDimensionAndMetrics(AppConst.STOP_M, AppConst.VIDEO_INCOMPLETE_GA);
            }
            flag25 = flag50 = flag75 = false;
            videoCurrentDurationToStore =0;
            isVideoPlayCalled = false;
            isBufferingCalled = false;
            isVideoCompleteCalled = false;
        } catch (Exception e) {
            e.getMessage();
        }
    }

    @Override
    public MediaSource createMediaSource(Uri uri) {
        return buildMediaSource(uri);
    }

    @Override
    public int[] getSupportedTypes() {
        // IMA does not support Smooth Streaming ads.
        return new int[]{C.TYPE_DASH, C.TYPE_HLS, C.TYPE_OTHER};
    }

    private MediaSource buildMediaSource(Uri uri) {
        @ContentType int type = Util.inferContentType(uri);
        switch (type) {
            case C.TYPE_DASH:
                return new DashMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
            case C.TYPE_SS:
                return new SsMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
            case C.TYPE_HLS:
                return new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
            case C.TYPE_OTHER:
                return new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
            default:
                throw new IllegalStateException("Unsupported type: " + type);
        }
    }

    String contentType;

    @Override
    public void onAdEvent(AdEvent adEvent) {

    }

    private boolean isBufferingCalled = false, isVideoPlayCalled = false;

    @Override
    public void playerEverySecondVOD(int playerDurationInSecond) {
        if (player != null) {
            totalVideoDuration = player.getDuration();
            if (!isVideoCompleteCalled && totalVideoDuration > 0) {
//                try {
//                    long percent = (playerDurationInSecond * 100000) / (totalVideoDuration);
//                    // Calling GA when after 25% of total video duration played
//                    if (percent >= 25 && !flag25) {
//                        flag25 = true;
//                        trackEventWithDimension("25%", playerDurationInSecond);
//                    } else if (percent >= 50 && !flag50) {
//                        flag50 = true;
//                        trackEventWithDimension("50%", playerDurationInSecond);
//                    } else if (percent >= 75 && !flag75) {
//                        flag75 = true;
//                        trackEventWithDimension("75%", playerDurationInSecond);
//                    }
//                    LogUtil.getInstance().i(TAG + playerDurationInSecond, totalVideoDuration + "==GAv4 playerEverySecondVOD: percent==>" + percent);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }

            }
        }
    }

    @Override
    public void onBuffering() {
        if (!isBufferingCalled) {
            trackEventWithDimension(AppConst.BUFFER_M);
            isBufferingCalled = true;
        }
    }

    @Override
    public void onReady(boolean isPlaying, long playerTotalDurationInSecond) {
        if (isPlaying) {
            isBufferingCalled = false;
            if (player.isCurrentWindowDynamic())
                contentType = AppConst.STR_LINEAR_GA;
            else contentType = AppConst.STR_VOD_GA;
            if (isVideoPlayCalled) {
                trackEventWithDimension(AppConst.RESUME_M);
            } else {
                trackEventWithDimension(AppConst.PLAY_M);
                isVideoPlayCalled = true;
            }
        }

    }

    @Override
    public void onCompleted() {
        isVideoPlayCalled = false;
        isBufferingCalled = false;
        isVideoCompleteCalled = true;
        flag25 = flag50 = flag75 = false;
        trackEventWithDimensionAndMetrics(AppConst.COMPLETE_M, AppConst.VIDEO_COMPLETE_GA);
    }

    @Override
    public void onError() {
        isVideoPlayCalled = false;
    }

    @Override
    public void onEverySecond(boolean b) {

    }

    /**
     * Handle Player Events
     */
    private class PlayerEventListener implements Player.EventListener {
        private String contentUrl;

        PlayerEventListener(String contentUrl) {
            this.contentUrl = contentUrl;
        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            switch (playbackState) {
                case Player.STATE_BUFFERING:
                    if (myPlayerEvent != null)
                        myPlayerEvent.onBuffering();
                    onBuffering();
                    break;
                case Player.STATE_ENDED:
                    if (myPlayerEvent != null)
                        myPlayerEvent.onCompleted();
                    onCompleted();
                    break;
                case Player.STATE_READY:
                    if (myPlayerEvent != null)
                        myPlayerEvent.onReady(playWhenReady, player.getDuration() / 1000);
                    onReady(playWhenReady, player.getDuration() / 1000);
                    break;
            }

        }

        @Override
        public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
            if (player != null && player.getPlaybackError() != null) {
                // The user has performed a seek whilst in the error state. Update the resume position so
                // that if the user then retries, playback resumes from the position to which they seeked.

            }
        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {

            if (myPlayerEvent != null)
                myPlayerEvent.onError();
            cancelTask();
            switch (error.type) {
                case ExoPlaybackException.TYPE_RENDERER:
                    LogUtil.getInstance().w(TAG, "TYPE_RENDERER==>" + error.type);
                    break;
                case ExoPlaybackException.TYPE_SOURCE:
                    LogUtil.getInstance().w(TAG, "TYPE_SOURCE==>" + error.type);
                    break;
                case ExoPlaybackException.TYPE_UNEXPECTED:
                    LogUtil.getInstance().w(TAG, "TYPE_UNEXPECTED==>" + error.type);
                    break;
                case ExoPlaybackException.TYPE_OUT_OF_MEMORY:
                    LogUtil.getInstance().w(TAG, "TYPE_OUT_OF_MEMORY==>" + error.type);
                    break;
                default:
                    break;
                case ExoPlaybackException.TYPE_REMOTE:
                    LogUtil.getInstance().w(TAG, "TYPE_REMOTE==>" + error.type);
                    break;
            }

        }

        @Override
        @SuppressWarnings("ReferenceEquality")
        public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {


        }
    }

    /**
     * Google analytics parameter without event value(ev)
     * <p>
     * Play, Resume, Pause and Buffer
     * </p>
     */
    private void trackEventWithDimension(String action) {
        Log.e("EVENT_NEW", action + " == " + currentMedia.getTitle() + "===" + screenName + " ==" + contentType + "===");
        if (AppConst.isGoogleAnalyticsEnable) {
            try {
                googleAnalytic.trackEventWithDimension(AppConst.EVENT_CATEGORY_VIDEO, action,
                        currentMedia.getTitle(), screenName, currentMedia.getId(),
                        contentType, "" + currentMedia.getVideo().getBitrate());

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void trackEventWithDimensionAndMetrics(String action, int isComplete) {
        float currentDuration;

        if (isComplete == AppConst.VIDEO_COMPLETE_GA)
            currentDuration = totalVideoDuration;
        else
            currentDuration = videoCurrentDurationToStore;

        if (AppConst.isGoogleAnalyticsEnable) {
            try {
                googleAnalytic.trackEventWithDimensionAndMatrices(AppConst.EVENT_CATEGORY_VIDEO, action,
                        currentMedia.getTitle(), screenName, currentMedia.getId(),
                        contentType, "" + totalVideoDuration / 1000,
                        currentMedia.getVideo().getBitrate(), currentDuration / 1000, isComplete);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Log.e("EVENT_NEW", action + " == " + currentMedia.getTitle() + "===" + screenName + " ==" + contentType + "===" + isComplete +"==="+currentDuration / 1000);
    }

    private void trackEventWithDimension(String action, long value) {

        if (AppConst.isGoogleAnalyticsEnable) {
            try {
                googleAnalytic.trackEventWithDimension(AppConst.EVENT_CATEGORY_VIDEO, action, currentMedia.getTitle(),
                        value / 1000, screenName, currentMedia.getId(),
                        contentType, "" + currentMedia.getVideo().getBitrate());

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Log.e("EVENT_NEW", action + " == " + currentMedia.getTitle() + "===" + screenName + " ==" + contentType + "===" + value / 1000);
    }
}

