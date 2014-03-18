package org.maneulyori.seoip;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends Activity {

	static ConnectionThread connectionThread;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_main);
	}

	@Override
	protected void onStart() {
		super.onStart();

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

			SSLContext sslcontext = SSLContext.getInstance("TLS");
			sslcontext.init(null, tmf.getTrustManagers(), new SecureRandom());

			connectionThread = new ConnectionThread(
					sslcontext.getSocketFactory(), "elrond.maneulyori.org", 1337,
					"changethis", true);
			Thread thread = new Thread(connectionThread);
			thread.start();
			
			connectionThread.sendCommand("LOCK 0");
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		Button send = (Button) findViewById(R.id.send);
		final EditText edittext = (EditText) findViewById(R.id.APDU);
		final TextView textview = (TextView) findViewById(R.id.response);
		
		send.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				String[] splittedAPDU = edittext.getText().toString().split(" ");
				
				byte[] apduByte = new byte[splittedAPDU.length];
				
				for (int i = 0; i < splittedAPDU.length; i++) {
					apduByte[i] = (byte) Integer.parseInt(splittedAPDU[i],
							16);
				}
				
				try {
					byte[] response = MainActivity.connectionThread.sendAPDU(apduByte);
					
					if(response == null) {
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
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		});
	}

	@Override
	protected void onStop() {
		super.onStop();
		connectionThread.terminate();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}
