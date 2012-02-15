package no.hioa.recruiting.activities;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import no.hioa.recruiting.R;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.ToggleButton;
import at.abraxas.amarino.Amarino;
import at.abraxas.amarino.AmarinoIntent;

public class ConnectionActivity extends ListActivity {
	private static final String TAG = "ConnectionActivity";
	private static final Boolean D = true;
	private static final int MESSAGE_RECEIVED = 0; 
	
	public static final UUID SUUID = UUID.randomUUID();

	private static ArrayAdapter<String> mDeviceList;

	private BluetoothAdapter mBluetoothAdapter;
	private ToggleButton mBluetoothOnButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (D) Log.d(TAG, "--- onCreate ---");
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.connection);
		mDeviceList = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
		setListAdapter(mDeviceList);

		mBluetoothOnButton = (ToggleButton) findViewById(R.id.connection_toggle_bluetooth_button);
		mBluetoothOnButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// TODO: implement progressbar & button lock
				if (mBluetoothAdapter.isEnabled()) {
					mBluetoothAdapter.disable();
					((ToggleButton) v).setChecked(false);
					setListAdapter(null);
				}
				else {
					mBluetoothAdapter.enable();
					((ToggleButton) v).setChecked(true);
					refreshList();
				}
			}
		});

		Button b = (Button) findViewById(R.id.connection_scan_for_devices_button);
		b.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				refreshList();
			}
		});

		b = (Button) findViewById(R.id.connection_console_button);
		b.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent i = new Intent(ConnectionActivity.this, ConsoleActivity.class);
				startActivity(i);
			}
		});

		ListView lv = (ListView) findViewById(android.R.id.list);
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
				String deviceMacAddress = (mDeviceList.getItem(position).split("\n"))[1];
				
				Amarino.connect(getApplicationContext(), deviceMacAddress);
			}
		});

		initializeBluetooth();
	}

	private void initializeBluetooth() {
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		mHandler = new CustomHandler(); 

		// Checks if the handset supports bluetooth
		if (mBluetoothAdapter == null) {
			Toast.makeText(ConnectionActivity.this,
					"This device does not support bluetooth", Toast.LENGTH_SHORT);
		}

		// Checks if Bluetooth is turned on
		if (mBluetoothAdapter.isEnabled()) {
			mBluetoothOnButton.setChecked(true);
			// Checks for already paired devices
			Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
			if (pairedDevices.size() > 0) {
				for (BluetoothDevice device : pairedDevices) {
					mDeviceList.add(device.getName() + "\n" + device.getAddress());
				}
			}
		}
		else {
			mBluetoothOnButton.setChecked(false);
		}
	}

	@Override
	protected void onDestroy() {
		try {
			unregisterReceiver(mBcastReceiver);
		}
		catch (IllegalArgumentException e) {
			Log.e(TAG, "--- No Receiver registered ---");
		}
		super.onDestroy();
	}

	@Override
	public void onPause() {
		try {
			unregisterReceiver(mBcastReceiver);
		}
		catch (IllegalArgumentException e) {
			Log.e(TAG, "--- No Receiver registered ---");
		}
		super.onPause();
	}

	private void refreshList() {
		// TODO: implement progressbar
		if (mBluetoothAdapter.isDiscovering()) mBluetoothAdapter.cancelDiscovery();

		// Removes all listed devices
		mDeviceList.clear();

		// Register the BroadcastReceiver
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		registerReceiver(mBcastReceiver, filter);

		mBluetoothAdapter.startDiscovery();
	}
	
	private class CustomHandler extends Handler { 
		@Override
		public void handleMessage(Message msg) {
			Toast.makeText(ConnectionActivity.this, "Message recieved", Toast.LENGTH_LONG).show(); 
		}
	}
	
	private class ConnectedThread extends Thread {
		private static final String TAG = "ConnectedThread"; 
		
	    private final BluetoothSocket mSocket;
	    private final InputStream mInStream;
	    private final OutputStream mOutStream;
	 
	    public ConnectedThread(BluetoothSocket socket) {
	    	if(D) Log.i(TAG, "--- Constructor ---");
	        mSocket = socket;
	        InputStream tmpIn = null;
	        OutputStream tmpOut = null;
	 
	        // Get the input and output streams, using temp objects because
	        // member streams are final
	        try {
	            tmpIn = socket.getInputStream();
	            tmpOut = socket.getOutputStream();
	        } catch (IOException e) { }
	 
	        mInStream = tmpIn;
	        mOutStream = tmpOut;
	    }
	 
	    public void run() {
	    	if(D) Log.i(TAG, "--- run() ---");
	        byte[] buffer = new byte[1024];  // buffer store for the stream
	        int bytes; // bytes returned from read()
	 
	        // Keep listening to the InputStream until an exception occurs
	        while (true) {
	            try {
	                // Read from the InputStream
	                bytes = mInStream.read(buffer);
	                // Send the obtained bytes to the UI Activity
	                mHandler.obtainMessage(MESSAGE_RECEIVED, bytes, -1, buffer)
	                        .sendToTarget();
	            } catch (IOException e) {
	                break;
	            }
	        }
	    }
	 
	    /* Call this from the main Activity to send data to the remote device */
	    public void write(byte[] bytes) {
	        try {
	            mOutStream.write(bytes);
	        } catch (IOException e) { }
	    }
	 
	    /* Call this from the main Activity to shutdown the connection */
	    public void cancel() {
	        try {
	            mSocket.close();
	        } catch (IOException e) { }
	    }
	}
	
	private final BroadcastReceiver mAmarinoReceiver = new BroadcastReceiver() {
		private static final String TAG = "AmarinoReceiver";
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.i(TAG, "--- onReceive"); 
			String action = intent.getAction();
			
			if(action.equals(AmarinoIntent.ACTION_CONNECTED)) {
				
			}
			if(action.equals(AmarinoIntent.ACTION_CONNECTED_DEVICES)) {
				
			}
			if(action.equals(AmarinoIntent.ACTION_CONNECTION_FAILED)) {
				
			}
			if(action.equals(AmarinoIntent.ACTION_DISCONNECTED)) {
				
			}
			
		}
	};

	private final BroadcastReceiver mBcastReceiver = new BroadcastReceiver() {
		private static final String TAG = "BroadcastReceiver";

		@Override
		public void onReceive(Context context, Intent intent) {
			// Gets the intents action identifier
			String action = intent.getAction();
			// If the action is equal to "device found"
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				Log.i(TAG, "--- Bluetooth device found ---");
				BluetoothDevice device = (BluetoothDevice) intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				Log.i(TAG, "--- " + device.getName() + " ---");
				mDeviceList.add(device.getName() + "\n" + device.getAddress());
			}

			if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
				Log.i(TAG, "--- Bluetooth Scan finished ----");
				ConnectionActivity.this.unregisterReceiver(mBcastReceiver);
			}
		}
	};
}
