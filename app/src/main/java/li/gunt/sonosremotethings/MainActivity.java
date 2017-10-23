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
import com.google.android.things.contrib.driver.button.Button;
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
    private static final long BUTTON_DEBOUNCE_DELAY_MS = 50;

    // Sonos speaker zone to control
    private static final String SONOS_ZONE = "Kitchen";

    private TextView textLine1;
    private TextView textLine2;

    private Ssd1306 screen;
    private SonosDevice sonosDevice;

    private Button buttonPlayPause;
    private Button buttonVolumeUp;
    private Button buttonVolumeDown;
    private Button buttonPreset1;
    private Button buttonPreset2;

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
        // load the Google Play Music playlist (requires a valid Google Play Music subscription)
        try {
            if (sonosDevice != null) {
                // Google Play Music Playlist: https://play.google.com/music/r/m/Lrujyowx2f6n5bot3lqtjok2n2e?t=Pasta_bei_Mario
                // Get the playlist URI from UPNP favorites
                sonosDevice.playUri("x-sonosapi-radio:vy_wPybY9vRCoaCMySC_D6Ol9WJ1aoH97kTmNIzBZ667Frppf2koUq7ZH7OJ9bXx41s_K0RZBSA?sid=151&flags=8300&sn=3", null);
                sonosDevice.setVolume(30);
                updateView(SONOS_ZONE, "Pasta at Mario");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SonosControllerException e) {
            e.printStackTrace();
        }
    }

    public void onPreset2Clicked() {
        // load the Swiss Pop radio stream
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
            BitmapHelper.setBmpData(screen, 0, 0, newContent, true);
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
        Log.i(TAG, "Configuring GPIO pins");
        try {
            setupButton(buttonPlayPause, BUTTON_PLAY_PAUSE, new Button.OnButtonEventListener() {
                @Override
                public void onButtonEvent(Button button, boolean pressed) {
                    if (pressed) {
                        onPlayPauseClicked();
                    }
                }
            });

            setupButton(buttonVolumeUp, BUTTON_VOLUME_UP, new Button.OnButtonEventListener() {
                @Override
                public void onButtonEvent(Button button, boolean pressed) {
                    if (pressed) {
                        onVolumeUpClicked();
                    }
                }
            });

            setupButton(buttonVolumeDown, BUTTON_VOLUME_DOWN, new Button.OnButtonEventListener() {
                @Override
                public void onButtonEvent(Button button, boolean pressed) {
                    if (pressed) {
                        onVolumeDownClicked();
                    }
                }
            });

            setupButton(buttonPreset1, BUTTON_PRESET1, new Button.OnButtonEventListener() {
                @Override
                public void onButtonEvent(Button button, boolean pressed) {
                    if (pressed) {
                        onPreset1Clicked();
                    }
                }
            });

            setupButton(buttonPreset2, BUTTON_PRESET2, new Button.OnButtonEventListener() {
                @Override
                public void onButtonEvent(Button button, boolean pressed) {
                    if (pressed) {
                        onPreset2Clicked();
                    }
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "Error configuring GPIO pins", e);
        }
    }

    private void setupButton(Button button, String pin, Button.OnButtonEventListener buttonEventListener) throws IOException {
        button = new Button(pin, Button.LogicState.PRESSED_WHEN_HIGH);
        button.setDebounceDelay(BUTTON_DEBOUNCE_DELAY_MS);
        button.setOnButtonEventListener(buttonEventListener);
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
