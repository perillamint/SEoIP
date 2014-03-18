package org.maneulyori.seoip;

import java.io.IOException;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

public class SEOverIPService extends HostApduService {
    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
    	
    	Log.d("SEoIP", commandApdu.toString());
    	
    	if(!MainActivity.connectionThread.isConnected())
    	{
    		Log.d("SEoIP", "ERR No connection");
    		return new byte[] {(byte)0x6A, (byte)0x82};
    	}

    	try {
			return MainActivity.connectionThread.sendAPDU(commandApdu);
		} catch (IOException e) {
			e.printStackTrace();
		}
    	
    	return new byte[] {(byte)0x6A, (byte)0x82};
    }
    @Override
    public void onDeactivated(int reason) {
       Log.d("SEoIP", "DEACTIVATED");
    }
}