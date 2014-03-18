package org.maneulyori.seoip;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.maneulyori.seoip.ConnectionService.ConnectionBinder;

import android.os.Bundle;
import android.os.IBinder;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends Activity {

	private ConnectionService mService;
	private boolean mBound = false;

	private SSLContext sslcontext;
	private Intent connectionIntent;
	private Intent seoveripIntent;

	private String address = "elrond.maneulyori.org";
	private int port = 1337;
	private String key = "changethis";

	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			// We've bound to LocalService, cast the IBinder and get
			// LocalService instance
			ConnectionBinder binder = (ConnectionBinder) service;
			mService = binder.getService();

			if (!mService.isConnected()) {
				mService.setupConnection(sslcontext.getSocketFactory(),
						address, port, key);
			}

			mBound = true;
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			mBound = false;
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		try {
			KeyStore trusted = KeyStore.getInstance("BKS");
			InputStream in = this.getResources().openRawResource(
					R.raw.sslkeystore);

			try {
				trusted.load(in, "111111".toCharArray());
			} finally {
				in.close();
			}

			TrustManagerFactory tmf = TrustManagerFactory
					.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			tmf.init(trusted);

			sslcontext = SSLContext.getInstance("TLS");
			sslcontext.init(null, tmf.getTrustManagers(), new SecureRandom());

			connectionIntent = new Intent(this, ConnectionService.class);
			seoveripIntent = new Intent(this, SEOverIPService.class);
			
			startService(connectionIntent);
		} catch (Exception e) {
			e.printStackTrace();
		}

		setContentView(R.layout.activity_main);
	}

	@Override
	protected void onStart() {
		super.onStart();

		if (!mBound) {
			bindService(connectionIntent, mConnection, Context.BIND_AUTO_CREATE);
		}
		
		startService(seoveripIntent);

		Button send = (Button) findViewById(R.id.send);
		Button start = (Button) findViewById(R.id.start);
		Button stop = (Button) findViewById(R.id.stop);
		Button reconnect = (Button)findViewById(R.id.reconnect);
		
		final EditText edittext = (EditText) findViewById(R.id.APDU);
		final TextView textview = (TextView) findViewById(R.id.response);

		send.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				String[] splittedAPDU = edittext.getText().toString()
						.split(" ");

				byte[] apduByte = new byte[splittedAPDU.length];

				for (int i = 0; i < splittedAPDU.length; i++) {
					apduByte[i] = (byte) Integer.parseInt(splittedAPDU[i], 16);
				}

				try {
					mService.sendCommand("LOCK 0");
					byte[] response = mService.sendAPDU(apduByte);

					if (response == null) {
						textview.setText("FAILED");
						return;
					}
					StringBuilder retval = new StringBuilder();

					retval.append("Response: ");

					for (int i = 0; i < response.length; i++) {
						retval.append(" "
								+ String.format("%02X", response[i] & 0xFF));
					}

					textview.setText(retval.toString());
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try {
						mService.sendCommand("UNLOCK");
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}

		});
		
		start.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				startService(connectionIntent);
				if (!mBound) {
					bindService(connectionIntent, mConnection, Context.BIND_AUTO_CREATE);
				}
			}
		});
		
		stop.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				stopService(connectionIntent);
			}
		});
		
		reconnect.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mService.reconnect();
			}
		});
	}

	@Override
	protected void onStop() {
		super.onStop();
		unbindService(mConnection);
		System.out.println("HELLO");
		stopService(seoveripIntent);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}
