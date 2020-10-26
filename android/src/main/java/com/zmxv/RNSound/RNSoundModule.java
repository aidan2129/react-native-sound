package com.zmxv.RNSound;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.content.res.AssetFileDescriptor;
import android.util.Log;
import android.media.AudioManager;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;

import com.android.vending.expansion.zipfile.APKExpansionSupport;
import com.android.vending.expansion.zipfile.ZipResourceFile;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import java.util.Arrays;

import android.util.Log;

public class RNSoundModule extends ReactContextBaseJavaModule implements LifecycleEventListener {
  Map<Integer, MediaPlayer> playerPool = new HashMap<>();
  ReactApplicationContext context;
  final static Object NULL = null;
  private static final String TAG = "RNSoundModule";

  public RNSoundModule(ReactApplicationContext context) {
    super(context);
    context.addLifecycleEventListener(this);
    this.context = context;
  }

  @Override
  public String getName() {
    return "RNSound";
  }

  @ReactMethod
  public void prepare(final String fileName, final Integer key, final Callback callback) {
    MediaPlayer player = createMediaPlayer(fileName);
    if (player == null) {
      WritableMap e = Arguments.createMap();
      e.putInt("code", -1);
      e.putString("message", "resource not found");
      callback.invoke(e);
      return;
    }
    this.playerPool.put(key, player);
    WritableMap props = Arguments.createMap();
    props.putDouble("duration", player.getDuration() * .001);
    callback.invoke(NULL, props);
  }

  protected MediaPlayer createMediaPlayer(final String fileName) {
    if(fileName.startsWith("exp://")) {
      String[] path = fileName.split("//");
      int expVer = Integer.parseInt(path[1]);
      int expPatchVer = Integer.parseInt(path[2]);
      String uri = path[3];
      ZipResourceFile expansionFile = null;
      AssetFileDescriptor fd = null;
      MediaPlayer mPlayer = new MediaPlayer();
      if(expVer>0) {
          try {
              expansionFile = APKExpansionSupport.getAPKExpansionZipFile(this.context, expVer, expPatchVer);
              fd = expansionFile.getAssetFileDescriptor(uri);
          } catch (IOException e) {
              Log.e(TAG, Arrays.toString(e.getStackTrace()));
              e.getStackTrace();
          } catch (NullPointerException e) {
              Log.e(TAG, Arrays.toString(e.getStackTrace()));
              e.getStackTrace();
          }
      }
      if(fd!=null) {
        try {
          mPlayer.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(),fd.getLength());
          mPlayer.prepare();
        } catch (IOException e) {
            Log.e(TAG, Arrays.toString(e.getStackTrace()));
            e.getStackTrace();
        } catch (NullPointerException e) {
            Log.e(TAG, Arrays.toString(e.getStackTrace()));
            e.getStackTrace();
        }
        try {
          fd.close();
        } catch (IOException e) {
          Log.e(TAG, Arrays.toString(e.getStackTrace()));
          e.getStackTrace();
        }
        return mPlayer;
      }
    } else {
      int res = this.context.getResources().getIdentifier(fileName, "raw", this.context.getPackageName());
      if (res != 0) {
        return MediaPlayer.create(this.context, res);
      }
      File file = new File(fileName);
      if (file.exists()) {
        Uri uri = Uri.fromFile(file);
        return MediaPlayer.create(this.context, uri);
      }
    int res = this.context.getResources().getIdentifier(fileName, "raw", this.context.getPackageName());
    if (res != 0) {
      return MediaPlayer.create(this.context, res);
    }
    if(fileName.startsWith("http://") || fileName.startsWith("https://")) {
      MediaPlayer mediaPlayer = new MediaPlayer();
      mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
      Log.i("RNSoundModule", fileName);
      try {
        mediaPlayer.setDataSource(fileName);
      } catch(IOException e) {
        Log.e("RNSoundModule", "Exception", e);
        return null;
      }
      return mediaPlayer;
    }

    if (fileName.startsWith("asset:/")){
        try {
            AssetFileDescriptor descriptor = this.context.getAssets().openFd(fileName.replace("asset:/", ""));
            mediaPlayer.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
            descriptor.close();
            return mediaPlayer;
        } catch(IOException e) {
            Log.e("RNSoundModule", "Exception", e);
            return null;
        }
    }

    File file = new File(fileName);
    if (file.exists()) {
      Uri uri = Uri.fromFile(file);
      return MediaPlayer.create(this.context, uri);
    }
    return null;
  }

  @ReactMethod
  public void play(final Integer key, final Callback callback) {
    MediaPlayer player = this.playerPool.get(key);
    if (player == null) {
      callback.invoke(false);
      return;
    }
    if (player.isPlaying()) {
      return;
    }
    player.setOnCompletionListener(new OnCompletionListener() {
      boolean callbackWasCalled = false;

      @Override
      public synchronized void onCompletion(MediaPlayer mp) {
        if (!mp.isLooping()) {
          if (callbackWasCalled) return;
          callbackWasCalled = true;
          callback.invoke(true);
        }
      }
    });
    player.setOnErrorListener(new OnErrorListener() {
      boolean callbackWasCalled = false;

      @Override
      public synchronized boolean onError(MediaPlayer mp, int what, int extra) {
        if (callbackWasCalled) return true;
        callbackWasCalled = true;
        callback.invoke(false);
        return true;
      }
    });
    player.start();
  }

  @ReactMethod
  public void pause(final Integer key) {
    MediaPlayer player = this.playerPool.get(key);
    if (player != null && player.isPlaying()) {
      player.pause();
    }
  }

  @ReactMethod
  public void stop(final Integer key) {
    MediaPlayer player = this.playerPool.get(key);
    if (player != null && player.isPlaying()) {
      player.pause();
      player.seekTo(0);
    }
  }

  @ReactMethod
  public void release(final Integer key) {
    MediaPlayer player = this.playerPool.get(key);
    if (player != null) {
      player.release();
      this.playerPool.remove(key);
    }
  }

  @ReactMethod
  public void setVolume(final Integer key, final Float left, final Float right) {
    MediaPlayer player = this.playerPool.get(key);
    if (player != null) {
      player.setVolume(left, right);
    }
  }

  @ReactMethod
  public void setSystemVolume(final Integer key, final Integer value) {
    AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
  }

  @ReactMethod
  public void setLooping(final Integer key, final Boolean looping) {
    MediaPlayer player = this.playerPool.get(key);
    if (player != null) {
      player.setLooping(looping);
    }
  }

  @ReactMethod
  public void setSpeed(final Integer key, final Float speed) {
    MediaPlayer player = this.playerPool.get(key);
    if (player != null) {
      player.setPlaybackParams(player.getPlaybackParams().setSpeed(speed));
    }
  }

  @ReactMethod
  public void setCurrentTime(final Integer key, final Float sec) {
    MediaPlayer player = this.playerPool.get(key);
    if (player != null) {
      player.seekTo((int)Math.round(sec * 1000));
    }
  }

  @ReactMethod
  public void getCurrentTime(final Integer key, final Callback callback) {
    MediaPlayer player = this.playerPool.get(key);
    if (player == null) {
      callback.invoke(-1, false);
      return;
    }
    callback.invoke(player.getCurrentPosition() * .001, player.isPlaying());
  }

  @ReactMethod
  public void enable(final Boolean enabled) {
    // no op
  }

  @Override
  public Map<String, Object> getConstants() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put("IsAndroid", true);
    return constants;
  }

  public void onHostDestroy() {
      for (Map.Entry<Integer, MediaPlayer> entry : this.playerPool.entrySet()) {
        release(entry.getKey());
      }
  }

  public void onHostPause() {}

  public void onHostResume() {}
}

