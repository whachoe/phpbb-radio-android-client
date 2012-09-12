package org.copywaste.breakzradio;

import java.util.Formatter;

import org.copywaste.breakzradio.PlayerService.LocalBinder;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;

public class Radio extends SherlockActivity implements PlayerService.PlayerServiceListener, SeekBar.OnSeekBarChangeListener {
	// Constants
	private final static int playdrawable  = R.drawable.play;
	private final static int pausedrawable = R.drawable.pause;
	// private final static int nextdrawable  = android.R.drawable.ic_media_next;
	
	// Properties
	private ProgressDialog loader;
	private PlayerService mService;
	private ImageButton stopbutton, playbutton, nextbutton;
	private TextView songTotalDurationLabel, songCurrentDurationLabel;
	private SeekBar songProgressBar;
	private Handler progressHandler = new Handler();
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        songTotalDurationLabel   = (TextView) findViewById(R.id.songTotalDurationLabel);
        songCurrentDurationLabel = (TextView) findViewById(R.id.songCurrentDurationLabel);
        songProgressBar = (SeekBar) findViewById(R.id.songProgressBar);
        
        stopbutton = (ImageButton) findViewById(R.id.stopbutton);
        playbutton = (ImageButton) findViewById(R.id.playbutton);
        nextbutton = (ImageButton) findViewById(R.id.nextbutton);
        stopbutton.setEnabled(false);
        playbutton.setEnabled(false);
        nextbutton.setEnabled(false);
        
        stopbutton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mService != null)
					mService.stopTrack();
			}
		});
        
        playbutton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mService == null) {
					loader.show();
					startService(new Intent(Radio.this, PlayerService.class));
					bindService(new Intent(Radio.this, PlayerService.class), mConnection, Context.BIND_AUTO_CREATE);
				} else {
					if (mService.status == PlayerService.TRACK_PLAYING) {
						mService.pauseTrack();
					} else {
						mService.playTrack();
					}
				}
			}
		});
        
        nextbutton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mService.nextTrack();
			}
		});
        
        startService(new Intent(this, PlayerService.class));
    }

    @SuppressLint("HandlerLeak")   
	@Override
    protected void onResume() {
    	super.onResume();
    	loader = new ProgressDialog(this);
    	loader.setProgressStyle(ProgressDialog.STYLE_SPINNER);
    	loader.setCancelable(false);
    	loader.setIndeterminate(true);
    	loader.setMessage("Loading..");
    	loader.show();
    	
    	// Checking for network connection in background
    	final Handler h = new Handler() {
    	    @Override
    	    public void handleMessage(Message msg) {
    	        if (msg.what != 1) { // code if not connected
    	        	loader.dismiss();
    	        	
    	    		AlertDialog.Builder db = new AlertDialog.Builder(Radio.this);
    	    		db.setTitle(R.string.no_connection_title);
    	    		db.setMessage(R.string.no_connection_content);
    	    		db.setPositiveButton(android.R.string.ok, new AlertDialog.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					});
    	    		
    	    		db.show();
    	        } else { // We are connected: Showtime!
    	        	bindService(new Intent(Radio.this, PlayerService.class), mConnection, Context.BIND_AUTO_CREATE);
    	        }
    	    }
    	};
    	
    	new Thread() {
            @Override
            public void run() {
            	h.sendEmptyMessage(WebStuff.webIsReachable() ? 1 : 0);
            }
    	}.start();
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    	if (mService != null) {
    		progressHandler.removeCallbacksAndMessages(null);
    		mService.setListener(null);
    		unbindService(mConnection);
    	}
    }
    
    private ServiceConnection mConnection = new ServiceConnection() {
        // Called when the connection with the service is established
        public void onServiceConnected(ComponentName className, IBinder service) {
        	Logger.log("Service is bound");
        	
        	loader.dismiss();
        	
            // Because we have bound to an explicit service that is running in our own process, 
        	// we can cast its IBinder to a concrete class and directly access it.
            LocalBinder binder = (LocalBinder) service;
            mService = binder.getService();
            
            mService.setListener(Radio.this);
            stopbutton.setEnabled(true);
            playbutton.setEnabled(true);
            nextbutton.setEnabled(true);
            
            // Only start playing if we don't have a tune yet
            if (mService.status == PlayerService.TRACK_STOP)
            	mService.nextTrack();
            else { // Let's check if a track is playing and if so: update our display
            	if (mService.status == PlayerService.TRACK_PAUSED) {
            		playbutton.setImageDrawable(getResources().getDrawable(playdrawable));
            	} 
            	if (mService.status == PlayerService.TRACK_PLAYING) {
            		playbutton.setImageDrawable(getResources().getDrawable(pausedrawable));
            	}
            	
            	// Show the current track info
            	//TODO: maybe later rewrite this since 'onTrackChange' is semantically not really what we want
            	onTrackChange(mService.getInfo());
            }
        }

        // Called when the connection with the service disconnects unexpectedly
        public void onServiceDisconnected(ComponentName className) {
        	 mService = null;
        }
    };

    /**
	 * Update timer on seekbar
	 * */
	private void updateProgressBar() {
        progressHandler.postDelayed(mUpdateTimeTask, 200);        
    }
	
	/**
	 * Background Runnable thread
	 * */
	private Runnable mUpdateTimeTask = new Runnable() {
		   public void run() {
			   if (mService == null) return;
			   
			   // This is dodgy: We should not interface with the mediaplayer directly but for stubbing this is ok
			   long totalDuration = mService.mp.getDuration();
			   long currentDuration = mService.mp.getCurrentPosition();
			  
			   // Displaying Total Duration time
			   songTotalDurationLabel.setText(""+Utilities.milliSecondsToTimer(totalDuration));
			   // Displaying time completed playing
			   songCurrentDurationLabel.setText(""+Utilities.milliSecondsToTimer(currentDuration));
			   
			   // Updating progress bar
			   int progress = (int)(Utilities.getProgressPercentage(currentDuration, totalDuration));
			   songProgressBar.setProgress(progress);
			   
		       progressHandler.postDelayed(this, 200);
		   }
		};
	
		/**
	     * When progress of SeekBar changes 
	     * */
	    @Override
	    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {}
	 
	    /**
	     * When user starts moving the progress handler
	     * */
	    @Override
	    public void onStartTrackingTouch(SeekBar seekBar) {
	        // remove message Handler from updating progress bar
	        progressHandler.removeCallbacksAndMessages(null);
	    }
	 
	    /**
	     * When user stops moving the progress handler
	     * */
	    @Override
	    public void onStopTrackingTouch(SeekBar seekBar) {
	    	if (mService == null) return;
	    	
	        int totalDuration = mService.mp.getDuration();
	        int currentPosition = Utilities.progressToTimer(seekBar.getProgress(), totalDuration);
	 
	        // forward or backward to certain seconds
	        mService.mp.seekTo(currentPosition);
	 
	        // update timer progress again
	        updateProgressBar();
	    }
	    
    /**
     * Implementing the PlayerService Interface
     */
	@Override
	public void onTrackChange(JSONObject newtrack) {
		try {
			((TextView)findViewById(R.id.artist)).setText(newtrack.getString("artist"));
			((TextView)findViewById(R.id.title)).setText(newtrack.getString("songtitle"));
			
			StringBuilder sb = new StringBuilder();
			Formatter formatter = new Formatter(sb);
			formatter.format("<a href=\"%s\">%s</a>", newtrack.getString("post_url"), newtrack.getString("poster_name"));
			TextView posted_by = (TextView)findViewById(R.id.posted_by);
			posted_by.setText(Html.fromHtml(sb.toString()));
			posted_by.setMovementMethod(LinkMovementMethod.getInstance());
			formatter.close();
			
			sb = new StringBuilder();
			formatter = new Formatter(sb);
			formatter.format("<a href=\"%s\">%s</a>", newtrack.getString("forum_url"), newtrack.getString("forum_name"));
			TextView posted_in = (TextView)findViewById(R.id.posted_in);
			posted_in.setText(Html.fromHtml(sb.toString()));
			posted_in.setMovementMethod(LinkMovementMethod.getInstance());
			formatter.close();
			
			((TextView)findViewById(R.id.posted_on)).setText(newtrack.getString("post_time"));
			
			if (mService.status == PlayerService.TRACK_PAUSED)
				playbutton.setImageDrawable(getResources().getDrawable(playdrawable));
			else
				playbutton.setImageDrawable(getResources().getDrawable(pausedrawable));
			
			updateProgressBar();
			
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onTrackPause() {
		playbutton.setImageDrawable(getResources().getDrawable(playdrawable));
	}

	@Override
	public void onTrackStop() {
		((TextView)findViewById(R.id.artist)).setText("");
		((TextView)findViewById(R.id.title)).setText("");
		((TextView)findViewById(R.id.posted_by)).setText("");
		((TextView)findViewById(R.id.posted_in)).setText("");
		((TextView)findViewById(R.id.posted_on)).setText("");
		playbutton.setImageDrawable(getResources().getDrawable(playdrawable));
		songProgressBar.setProgress(0);
		songCurrentDurationLabel.setText("0:00");
		songTotalDurationLabel.setText("0:00");
		
		// Since we stopped: Release the service
		unbindService(mConnection);
		stopService(new Intent(Radio.this, PlayerService.class));
		mService = null;
	}

	@Override
	public void onTrackPlay() {
		playbutton.setImageDrawable(getResources().getDrawable(pausedrawable));
	}
	
}