package li.gunt.sonosremotethings;

import android.app.Activity;
import android.graphics.Bitmap;
import android.util.Log;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.View;
import android.widget.AbsoluteLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.List;

import com.google.android.things.contrib.driver.ssd1306.BitmapHelper;
import com.google.android.things.contrib.driver.ssd1306.Ssd1306;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManagerService;
import com.vmichalak.sonoscontroller.PlayState;
import com.vmichalak.sonoscontroller.SonosDevice;
import com.vmichalak.sonoscontroller.SonosDiscovery;
import com.vmichalak.sonoscontroller.exception.SonosControllerException;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    // PICO-PI-IMX6UL hardware mapping
    private static final String I2C_BUS = "I2C2";
    private static final String BUTTON_PLAY_PAUSE = "GPIO5_IO02";
    private static final String BUTTON_VOLUME_UP = "GPIO4_IO19";
    private static final String BUTTON_VOLUME_DOWN = "GPIO4_IO21";
    private static final String BUTTON_PRESET1 = "GPIO4_IO22";
    private static final String BUTTON_PRESET2 = "GPIO4_IO23";

    // Sonos speaker zone to control
    private static final String SONOS_ZONE = "Kitchen";

    private TextView textLine1;
    private TextView textLine2;

    private Ssd1306 screen;
    private SonosDevice sonosDevice;
    private Gpio buttonPlayPause;
    private Gpio buttonVolumeUp;
    private Gpio buttonVolumeDown;
    private Gpio buttonPreset1;
    private Gpio buttonPreset2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // OLED display is our primary view
        setupOledDisplay();
        bindView();
        updateView("Android-Remote-Things", "connecting..");

        // allow network access in main thread to keep the example simple
        allowNetworkInMainThread();
        setupSonosSpeaker(SONOS_ZONE);

        // configure buttons at the end to avoid interaction before everything is setup
        setupButtons();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyOledDisplay();
    }

    public void onPlayPauseClicked() {
        try {
            if (sonosDevice != null) {
                if (sonosDevice.getPlayState() == PlayState.PLAYING) {
                    sonosDevice.pause();
                }
                else {
                    sonosDevice.play();
                }
            }
            else {
                // Alternate function: reconnect to Sonos speaker
                setupSonosSpeaker(SONOS_ZONE);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SonosControllerException e) {
            e.printStackTrace();
        }
    }

    public void onVolumeUpClicked() {
        try {
            if (sonosDevice != null) {
                int currentVolume = sonosDevice.getVolume();
                if (currentVolume < 100) {
                    sonosDevice.setVolume(currentVolume + 1);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SonosControllerException e) {
            e.printStackTrace();
        }
    }

    public void onVolumeDownClicked() {
        try {
            if (sonosDevice != null) {
                int currentVolume = sonosDevice.getVolume();
                if (currentVolume > 0) {
                    sonosDevice.setVolume(currentVolume - 1);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SonosControllerException e) {
            e.printStackTrace();
        }
    }

    public void onPreset1Clicked() {
        // fill in your favorite streaming URI and player configuration
        try {
            if (sonosDevice != null) {
                sonosDevice.playUri("x-rincon-mp3radio://www.charivari.de/webradio/955-charivari-stream-webradio.m3u", null);
                sonosDevice.setVolume(35);
                updateView(SONOS_ZONE, "Charivari");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SonosControllerException e) {
            e.printStackTrace();
        }
    }

    public void onPreset2Clicked() {
        // fill in your favorite streaming URI and player configuration
        try {
            if (sonosDevice != null) {
                sonosDevice.playUri("x-rincon-mp3radio://www.radioswisspop.ch/live/aacp.m3u", null);
                sonosDevice.setVolume(20);
                updateView(SONOS_ZONE, "Swiss Pop");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SonosControllerException e) {
            e.printStackTrace();
        }
    }

    private void bindView() {
        View view = getLayoutInflater().inflate(R.layout.activity_main, null);
        setContentView(view);
        textLine1 = findViewById(R.id.mainActivityLine1);
        textLine2 = findViewById(R.id.mainActivityLine2);
    }

    private void updateView(String line1, String line2) {
        textLine1.setText(line1);
        textLine2.setText(line2);
        drawView();
    }

    private void drawView() {
        if (screen != null) {
            Bitmap newContent = generateBitmapFromView();
            BitmapHelper.setBmpData(screen, 0, 0, newContent, false);
        }
    }

    private Bitmap generateBitmapFromView() {
        // we use the XML layout to define our screen content (activity_main.xml) and convert it to a bitmap
        AbsoluteLayout layout = findViewById(R.id.mainActivityScreen);
        layout.setDrawingCacheEnabled(true);
        layout.buildDrawingCache();
        return layout.getDrawingCache();
    }

    private void allowNetworkInMainThread() {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    private void setupSonosSpeaker(String zoneName) {
        List<SonosDevice> availableDevices = discoverSonosDevices();
        for (SonosDevice device : availableDevices) {
            try {
                if (device.getZoneName().equals(zoneName)) {
                    sonosDevice = device;
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (SonosControllerException e) {
                e.printStackTrace();
            }
        }
        if (sonosDevice != null) {
            updateView(zoneName, "connected!");
        } else {
            updateView("Connection error", "> to repeat");
        }
    }

    private void setupOledDisplay() {
        try {
            Ssd1306 screen = new Ssd1306(I2C_BUS);
            screen.setDisplayOn(true);
        } catch (IOException e) {
            Log.e(TAG, "Error while opening screen", e);
        }
        Log.d(TAG, "OLED screen activity created");
    }

    private void destroyOledDisplay() {
        if (screen != null) {
            try {
                screen.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing SSD1306", e);
            } finally {
                screen = null;
            }
        }
    }

    private void setupButtons() {
        PeripheralManagerService pioService = new PeripheralManagerService();
        try {
            Log.i(TAG, "Configuring GPIO pins");

            buttonPlayPause = pioService.openGpio(BUTTON_PLAY_PAUSE);
            setupButton(buttonPlayPause, new GpioCallback() {
                @Override
                public boolean onGpioEdge(Gpio gpio) {
                    onPlayPauseClicked();
                    return true;
                }
            });

            buttonVolumeUp = pioService.openGpio(BUTTON_VOLUME_UP);
            setupButton(buttonVolumeUp, new GpioCallback() {
                @Override
                public boolean onGpioEdge(Gpio gpio) {
                    onVolumeUpClicked();
                    return true;
                }
            });

            buttonVolumeDown = pioService.openGpio(BUTTON_VOLUME_DOWN);
            setupButton(buttonVolumeDown, new GpioCallback() {
                @Override
                public boolean onGpioEdge(Gpio gpio) {
                    onVolumeDownClicked();
                    return true;
                }
            });

            buttonPreset1 = pioService.openGpio(BUTTON_PRESET1);
            setupButton(buttonPreset1, new GpioCallback() {
                @Override
                public boolean onGpioEdge(Gpio gpio) {
                    onPreset1Clicked();
                    return true;
                }
            });

            buttonPreset2 = pioService.openGpio(BUTTON_PRESET2);
            setupButton(buttonPreset2, new GpioCallback() {
                @Override
                public boolean onGpioEdge(Gpio gpio) {
                    onPreset2Clicked();
                    return true;
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "Error configuring GPIO pins", e);
        }
    }

    private void setupButton(Gpio button, GpioCallback callback) throws IOException {
        // Initialize the pin as an input
        button.setDirection(Gpio.DIRECTION_IN);
        // High voltage is considered active
        button.setActiveType(Gpio.ACTIVE_HIGH);
        // Register for rising edge when button has been pressed
        button.setEdgeTriggerType(Gpio.EDGE_RISING);
        button.registerGpioCallback(callback);
    }

    private List<SonosDevice> discoverSonosDevices() {
        List<SonosDevice> devices = null;
        try {
            devices = SonosDiscovery.discover();
            for (SonosDevice device : devices) {
                Log.d(TAG, "Found Sonos " + device.getZoneName() + " with IP " + device.getSpeakerInfo().getIpAddress());
            }

        } catch (IOException e) {
            Log.e(TAG, "Failed to discover Sonos devices", e);
        } catch (SonosControllerException e) {
            Log.e(TAG, "Sonos Controller Exception", e);
        }
        return devices;
    }
}
