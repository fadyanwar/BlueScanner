package com.example.bluescanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS = 2;

    private BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<String> deviceArrayAdapter;
    private final ArrayList<String> deviceList = new ArrayList<>();
    private TextView statusText;
    private Button scanButton;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);

                String deviceName = device.getName();
                String deviceAddress = device.getAddress();
                String deviceType = parseDeviceType(device.getBluetoothClass().getDeviceClass());

                String deviceInfo = String.format("%s\n%s\n%s\n%d dBm",
                        (deviceName != null && !deviceName.isEmpty()) ? deviceName : "Unknown Device",
                        deviceAddress,
                        deviceType,
                        rssi);

                if (!deviceList.contains(deviceInfo)) {
                    deviceList.add(deviceInfo);
                    deviceArrayAdapter.notifyDataSetChanged();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                statusText.setText("Scan finished. Found " + deviceList.size() + " devices.");
                scanButton.setEnabled(true);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        scanButton = findViewById(R.id.scanButton);
        ListView deviceListView = findViewById(R.id.deviceList);

        // Custom list adapter setup
        deviceArrayAdapter = new ArrayAdapter<String>(
                this,
                R.layout.device_list_item,
                R.id.deviceName,
                deviceList
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                String[] parts = getItem(position).split("\n");

                TextView name = view.findViewById(R.id.deviceName);
                TextView address = view.findViewById(R.id.deviceAddress);
                TextView details = view.findViewById(R.id.deviceDetails);

                name.setText(parts[0]);
                address.setText(parts[1]);
                details.setText(parts[2] + " | " + parts[3]);

                // Signal strength color coding
                int rssi = Integer.parseInt(parts[3].replaceAll("[^\\d-]", ""));
                if (rssi > -50) {
                    details.setTextColor(Color.GREEN);
                } else if (rssi > -70) {
                    details.setTextColor(Color.YELLOW);
                } else {
                    details.setTextColor(Color.RED);
                }

                return view;
            }
        };

        deviceListView.setAdapter(deviceArrayAdapter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            statusText.setText("Bluetooth not supported");
            scanButton.setEnabled(false);
            return;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(receiver, filter);

        scanButton.setOnClickListener(v -> {
            if (checkPermissions()) startBluetoothScan();
        });
    }

    private String parseDeviceType(int deviceClass) {
        switch (deviceClass) {
            case BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES: return "🎧 Headphones";
            case BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE: return "📱 Handsfree";
            case BluetoothClass.Device.PHONE_SMART: return "📱 Smartphone";
            case BluetoothClass.Device.WEARABLE_WRIST_WATCH: return "⌚ Smartwatch";
            case BluetoothClass.Device.COMPUTER_LAPTOP: return "💻 Laptop";
            case BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO: return "🚗 Car Audio";
            case BluetoothClass.Device.HEALTH_BLOOD_PRESSURE: return "❤️ Health Device";
            case BluetoothClass.Device.TOY_CONTROLLER: return "🎮 Toy";
            default: return "❓ Unknown Device";
        }
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.ACCESS_FINE_LOCATION
                        }, REQUEST_PERMISSIONS);
                return false;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_PERMISSIONS);
                return false;
            }
        }
        return true;
    }

    @SuppressLint("MissingPermission")
    private void startBluetoothScan() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }

        deviceList.clear();
        deviceArrayAdapter.notifyDataSetChanged();

        if (bluetoothAdapter.startDiscovery()) {
            statusText.setText("Scanning for devices...");
            scanButton.setEnabled(false);
        } else {
            statusText.setText("Scan initialization failed");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT && resultCode != RESULT_OK) {
            Toast.makeText(this, "Bluetooth required for scanning", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) startBluetoothScan();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
        if (bluetoothAdapter != null) bluetoothAdapter.cancelDiscovery();
    }
}