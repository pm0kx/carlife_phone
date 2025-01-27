package com.didi365.carlife.video;

import android.content.Context;
import android.media.MediaPlayer;

/**
 * Create by zheng on 2019/1/17
 */

public abstract class AbstractMediaPlayEngine implements IBasePlayEngine, MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener
        , MediaPlayer.OnErrorListener {

    protected MediaPlayer mMediaPlayer;
    protected MediaModel mMediaInfo;
    protected Context mContext;
    protected int mPlayState;

    protected PlayerEngineListener mPlayerEngineListener;

    protected abstract boolean prepareSelf();

    protected abstract boolean prepareComplete(MediaPlayer mp);

    protected void defaultParam() {
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnCompletionListener(this);
        mMediaPlayer.setOnPreparedListener(this);
        mMediaInfo = null;
        mPlayState = PlayState.MPS_NOFILE;
    }

    public AbstractMediaPlayEngine(Context context) {
        mContext = context;
        defaultParam();
    }

    public void setPlayerListener(PlayerEngineListener listener) {
        mPlayerEngineListener = listener;
    }

    @Override
    public void play() {
        switch (mPlayState) {
            case PlayState.MPS_PAUSE:
                mMediaPlayer.start();
                mPlayState = PlayState.MPS_PLAYING;
                performPlayListener(mPlayState);
                break;
            case PlayState.MPS_STOP:
                prepareSelf();
                break;
            default:
                break;
        }
    }

    @Override
    public void pause() {
        switch (mPlayState) {
            case PlayState.MPS_PLAYING:
                mMediaPlayer.pause();
                mPlayState = PlayState.MPS_PAUSE;
                performPlayListener(mPlayState);
                break;
            default:
                break;
        }
    }

    @Override
    public void stop() {
        if (mPlayState != PlayState.MPS_NOFILE) {
            mMediaPlayer.reset();
            mPlayState = PlayState.MPS_STOP;
            performPlayListener(mPlayState);
        }
    }

    @Override
    public void skipTo(int time) {
        switch (mPlayState) {
            case PlayState.MPS_PLAYING:
            case PlayState.MPS_PAUSE:
                int time2 = reviceSeekValue(time);
                mMediaPlayer.seekTo(time2);
                break;
            default:
                break;
        }
    }

    public void exit() {
        stop();
        mMediaPlayer.release();
        mMediaInfo = null;
        mPlayState = PlayState.MPS_NOFILE;
    }

    public boolean isPlaying() {
        return mPlayState == PlayState.MPS_PLAYING;
    }

    public boolean isPause() {
        return mPlayState == PlayState.MPS_PAUSE;
    }

    public void playMedia(MediaModel mediaInfo) {
        if (mediaInfo != null) {
            mMediaInfo = mediaInfo;
            prepareSelf();
        }
    }

    public int getCurPosition() {
        if (mPlayState == PlayState.MPS_PLAYING || mPlayState == PlayState.MPS_PAUSE) {
            return mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    public int getDuration() {
        switch (mPlayState) {
            case PlayState.MPS_PLAYING:
            case PlayState.MPS_PAUSE:
            case PlayState.MPS_PARECOMPLETE:
                return mMediaPlayer.getDuration();
        }
        return 0;
    }

    public int getPlayState() {
        return mPlayState;
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        prepareComplete(mediaPlayer);
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
        return false;
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        if (mPlayerEngineListener != null) {
            mPlayerEngineListener.onTrackPlayComplete(mMediaInfo);
        }
    }

    private int reviceSeekValue(int value) {
        if (value < 0) {
            value = 0;
        }
        if (value > mMediaPlayer.getDuration()) {
            value = mMediaPlayer.getDuration();
        }
        return value;
    }

    protected void performPlayListener(int playState) {
        if (mPlayerEngineListener != null) {
            switch (playState) {
                case PlayState.MPS_INVALID:
                    mPlayerEngineListener.onTrackStreamError(mMediaInfo);
                    break;
                case PlayState.MPS_STOP:
                    mPlayerEngineListener.onTrackStop(mMediaInfo);
                    break;
                case PlayState.MPS_PLAYING:
                    mPlayerEngineListener.onTrackPlay(mMediaInfo);
                    break;
                case PlayState.MPS_PAUSE:
                    mPlayerEngineListener.onTrackPause(mMediaInfo);
                    break;
                case PlayState.MPS_PARESYNC:
                    mPlayerEngineListener.onTrackPrepareSync(mMediaInfo);
                    break;
            }
        }
    }
}
