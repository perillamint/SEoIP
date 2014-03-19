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
import android.os.Handler;
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
import android.widget.Toast;

public class MainActivity extends Activity {

	private ConnectionService mService;
	private boolean mBound = false;
	private Handler handler = new Handler();

	private SSLContext sslcontext;
	private Intent connectionIntent;
	private Intent seoveripIntent;

	private String address;
	private int port;
	private String key;

	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			// We've bound to LocalService, cast the IBinder and get
			// LocalService instance
			ConnectionBinder binder = (ConnectionBinder) service;
			mService = binder.getService();

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
			startService(seoveripIntent);
		} catch (Exception e) {
			e.printStackTrace();
		}

		setContentView(R.layout.activity_main);
	}

	@Override
	protected void onStart() {
		super.onStart();

		Button send = (Button) findViewById(R.id.send);
		Button connect = (Button) findViewById(R.id.connect);
		Button disconnect = (Button) findViewById(R.id.disconnect);
		Button reconnect = (Button) findViewById(R.id.reconnect);

		final EditText edittext = (EditText) findViewById(R.id.APDU);
		final TextView textview = (TextView) findViewById(R.id.response);
		final TextView serverAddr = (TextView) findViewById(R.id.serverAddr);
		final TextView serverPass = (TextView) findViewById(R.id.serverPass);
		final TextView serverPort = (TextView) findViewById(R.id.serverPort);
		final TextView connStat = (TextView) findViewById(R.id.connectionStat);

		send.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				if (!mBound) {
					Toast.makeText(MainActivity.this, "Not bounded",
							Toast.LENGTH_SHORT).show();
					return;
				}

				if (!mService.isConnected()) {
					Toast.makeText(MainActivity.this, "Not connected",
							Toast.LENGTH_SHORT).show();
					return;
				}

				String[] splittedAPDU = edittext.getText().toString()
						.split(" ");

				byte[] apduByte = new byte[splittedAPDU.length];

				for (int i = 0; i < splittedAPDU.length; i++) {
					apduByte[i] = (byte) Integer.parseInt(splittedAPDU[i], 16);
				}

				try {
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
				}
			}

		});

		connect.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				address = serverAddr.getText().toString();
				key = serverPass.getText().toString();
				port = Integer.parseInt(serverPort.getText().toString());

				if (!mBound) {
					bindService(connectionIntent, mConnection,
							Context.BIND_AUTO_CREATE);
				}

				new Thread(new Runnable() {

					@Override
					public void run() {

						Runnable toastRunnable = new Runnable() {
							public void run() {
								Toast.makeText(MainActivity.this, "Connected",
										Toast.LENGTH_SHORT).show();
								connStat.setText("Connected");
							}
						};

						while (!mBound)
							try {
								Thread.sleep(100);
							} catch (InterruptedException e1) {
								e1.printStackTrace();
							}

						if ((!mService.isConnected()) && address != null
								&& key != null && port != 0) {
							mService.setupConnection(
									sslcontext.getSocketFactory(), address,
									port, key);
							mService.sendCommand("LOCK 0");
						}

						handler.post(toastRunnable);
					}

				}).start();
			}
		});

		disconnect.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (!mBound) {
					bindService(connectionIntent, mConnection,
							Context.BIND_AUTO_CREATE);
				}

				new Thread(new Runnable() {

					@Override
					public void run() {

						Runnable toastRunnable = new Runnable() {
							public void run() {
								Toast.makeText(MainActivity.this,
										"Disconnected", Toast.LENGTH_SHORT)
										.show();
								connStat.setText("Disconnected.");
								
							}
						};

						while (!mBound)
							try {
								Thread.sleep(100);
							} catch (InterruptedException e1) {
								e1.printStackTrace();
							}

						if (mService.isConnected()) {
							mService.sendCommand("UNLOCK");
							mService.disconnect();
						}

						handler.post(toastRunnable);
					}

				}).start();
			}
		});

		reconnect.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				address = serverAddr.getText().toString();
				key = serverPass.getText().toString();
				port = Integer.parseInt(serverPort.getText().toString());

				mService.reconnect();
			}
		});
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (mBound)
			unbindService(mConnection);

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		stopService(connectionIntent);
		stopService(seoveripIntent);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}
