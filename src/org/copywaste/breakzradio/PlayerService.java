package org.copywaste.breakzradio;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.copywaste.breakzradio.WebStuff.ApiException;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

public class PlayerService extends Service {
	public static final int TRACK_PAUSED  = 1;
	public static final int TRACK_PLAYING = 2;
	public static final int TRACK_STOP    = 3;
	
	public MediaPlayer mp;
    private IBinder mBinder;
    NotificationCompat.Builder b;
    private PlayerServiceListener listener = null;
    JSONObject track = null;
    public int status; 
    NotificationManager nm; 
    
    @Override
    public void onCreate() {
    	super.onCreate();
    	
    	mp = new MediaPlayer();
    	mBinder = new LocalBinder();
    	status = TRACK_STOP;
    	nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    	return super.onStartCommand(intent, START_STICKY, startId);
    }
    
	/**
	 * @see android.app.Service#onBind(Intent)
	 */
	@Override
	public IBinder onBind(Intent intent) {
		// Make a notification so we can get back to the player
		b=new NotificationCompat.Builder(this);
		Intent outbound=new Intent(this, Radio.class);
		
		b.setAutoCancel(false)
		 .setOngoing(true)
		 .setDefaults(Notification.DEFAULT_ALL)
		 .setWhen(System.currentTimeMillis())
		 .setSmallIcon(android.R.drawable.ic_media_play)
		 .setVibrate(null)
		 .setSound(null)
		 .setContentTitle(getString(R.string.app_name))
		 .setContentText("Loading...").setTicker("Loading...")
		 .setContentIntent(PendingIntent.getActivity(this, 0, outbound, 0));
		
		Notification noti = b.build();
		startForeground(5432,noti);
		
		// Ask for next song when a song is completed
		mp.setOnCompletionListener(new OnCompletionListener() {
			@Override
			public void onCompletion(MediaPlayer mp) {
				nextTrack();
			}
		});
		return mBinder;
	}
	
	public class LocalBinder extends Binder {
        PlayerService getService() {
            // Return this instance of PlayerService so clients can call public methods
            return PlayerService.this;
        }
    }
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		mp.release();
	}
	
	public interface PlayerServiceListener {
		public void onTrackChange(JSONObject newtrack);
		public void onTrackStop();
		public void onTrackPause();
		public void onTrackPlay();
	}
	
	class nextTrackCallable implements Callable<JSONObject> {
	    @Override
	    public JSONObject call() {
	    	String data;
			try {
				data = WebStuff.getPage(RadioApplication.baseurl+"/api/next");
				if (data.length() > 0) {
					track = new JSONObject(data);
					return track;
				}

			} catch (ApiException e) {
				e.printStackTrace();
			} catch (JSONException e) {
				e.printStackTrace();
			}
			
			return null;
	    }
	}
	
	private JSONObject getTrack() {
		ExecutorService executor = Executors.newFixedThreadPool(300);
		Future<JSONObject> task = executor.submit(new nextTrackCallable());
		JSONObject track;
		try {
			// this blocks until result is ready
			track = task.get();
			
			return track;
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		} 
		
		return null;
	}
	
	public void nextTrack() {
		track = getTrack();
		if (track != null) {
			try {
				mp.reset();
				
				if (track.getString("type").equals("mp3"))
					mp.setDataSource(track.getString("url"));
				else
					mp.setDataSource(track.getString("stream_url")+"?client_id="+track.getString("soundcloud_api_key"));
				mp.prepare();
				playTrack();
				
				// Message the GUI
				if (listener != null) {
					listener.onTrackChange(track);
				}
				
				// Set notification
				b.setContentText(track.getString("artist") + " - " + track.getString("songtitle"));
				nm.notify(5432, b.build());
				
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (JSONException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			// If the previous track was null, try again
			nextTrack();
		}
	}
	
	public void pauseTrack() {
		status = TRACK_PAUSED;
		mp.pause();
		
		// Change the notification a bit
		b.setSmallIcon(android.R.drawable.ic_media_pause);
		nm.notify(5432, b.build());
		
		if (listener != null) {
			listener.onTrackPause();
		}
	}
	
	public void playTrack() {
		status = TRACK_PLAYING;
		mp.start();
		
		// Change the notification a bit
		b.setSmallIcon(android.R.drawable.ic_media_play);
		nm.notify(5432, b.build());

		if (listener != null) {
			listener.onTrackPlay();
		}
	}
	
	public void stopTrack() {
		status = TRACK_STOP;
		mp.stop();
		
		// Get rid of notification
		nm.cancelAll();
		
		if (listener != null) {
			listener.onTrackStop();
		}
	}
	
	public JSONObject getInfo() {
		return track;
	}
	
	public void setListener(PlayerServiceListener listener) {
		this.listener = listener;
	}
}
