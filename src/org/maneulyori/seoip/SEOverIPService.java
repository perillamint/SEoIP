package org.maneulyori.seoip;

import java.io.IOException;

import org.maneulyori.seoip.ConnectionService.ConnectionBinder;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class SEOverIPService extends HostApduService {

	private ConnectionService mService;
	private boolean mBound = false;

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
	public int onStartCommand(Intent intent, int flags, int startId) {
		Intent connectionIntent = new Intent(this, ConnectionService.class);
		bindService(connectionIntent, mConnection, Context.BIND_AUTO_CREATE);

		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onDestroy() {
		if (mBound) {
			unbindService(mConnection);
			mBound = false;
		}
	}

	@Override
	public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {

		try {
			mService.sendCommand("LOCK 0");

			Log.d("SEoIP", commandApdu.toString());

			if (!mService.isConnected()) {
				Log.d("SEoIP", "ERR No connection");
				return new byte[] { (byte) 0x6A, (byte) 0x82 }; //File not found response.
			}

			try {
				return mService.sendAPDU(commandApdu);
			} catch (IOException e) {
				e.printStackTrace();
			}

			mService.sendCommand("UNLOCK");

		} catch (IOException e1) {
			e1.printStackTrace();
		}

		return new byte[] { (byte) 0x6A, (byte) 0x82 };
	}

	@Override
	public void onDeactivated(int reason) {
		Log.d("SEoIP", "DEACTIVATED");
	}
}