package li.gunt.sonosremotethings;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.os.Bundle;
import android.os.StrictMode;

import java.io.IOException;
import java.util.List;

import com.google.android.things.contrib.driver.ssd1306.BitmapHelper;
import com.google.android.things.contrib.driver.ssd1306.Ssd1306;
import com.google.android.things.contrib.driver.button.Button;
import com.vmichalak.sonoscontroller.PlayState;
import com.vmichalak.sonoscontroller.SonosDevice;
import com.vmichalak.sonoscontroller.SonosDiscovery;
import com.vmichalak.sonoscontroller.exception.SonosControllerException;

import static android.graphics.Bitmap.Config.ARGB_8888;

/**
 * Rapid prototype for a Android Things based Sonos remote control.
 * Customize the hardware mapping based on your schematics - see imx6ul_schematics.fzz
 */
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
    private static final int VOLUME_STEP = 5;

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

        // show startup text
        setupSSD1306OledDisplay();
        updateView("Android-Remote-Things", "connecting..");

        // allow network access in main thread to keep the example simple
        allowNetworkInMainThread();
        setupSonosSpeaker();

        // configure buttons at the end to avoid interaction before everything is setup
        setupButtons();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        destroyButtons();
        destroyOledDisplay();
    }

    private void onPlayPauseClicked() {
        try {
            if (sonosDevice != null) {
                // toggle the state
                if (sonosDevice.getPlayState() == PlayState.PLAYING) {
                    sonosDevice.pause();
                }
                else {
                    sonosDevice.play();
                }
            }
            else {
                // Alternate function: reconnect to Sonos speaker if discovery at startup failed
                setupSonosSpeaker();
            }
        } catch (IOException | SonosControllerException e) {
            Log.e(TAG, "Sonos play-pause command failed", e);
        }
    }

    private void onVolumeUpClicked() {
        try {
            if (sonosDevice != null) {
                int volume = sonosDevice.getVolume() + VOLUME_STEP;
                if (volume > 100) {
                    volume = 100;
                }
                sonosDevice.setVolume(volume);
            }
        } catch (IOException | SonosControllerException e) {
            Log.e(TAG, "Sonos volume up command failed", e);
        }
    }

    private void onVolumeDownClicked() {
        try {
            if (sonosDevice != null) {
                int volume = sonosDevice.getVolume() - VOLUME_STEP;
                if (volume < 0) {
                    volume = 0;
                }
                sonosDevice.setVolume(volume);
            }
        } catch (IOException | SonosControllerException e) {
            Log.e(TAG, "Sonos volume down command failed", e);
        }
    }

    private void onPreset1Clicked() {
        // predefined example to load the Google Play Music playlist (requires a valid Google Play Music subscription)
        try {
            if (sonosDevice != null) {
                // Google Play Music Playlist: https://play.google.com/music/r/m/Lrujyowx2f6n5bot3lqtjok2n2e?t=Pasta_bei_Mario
                // Get the playlist URI from Sonos UPnP command line utility
                sonosDevice.playUri("x-sonosapi-radio:vy_wPybY9vRCoaCMySC_D6Ol9WJ1aoH97kTmNIzBZ667Frppf2koUq7ZH7OJ9bXx41s_K0RZBSA?sid=151&flags=8300&sn=3", null);
                sonosDevice.setVolume(30);
                updateView(SONOS_ZONE, "Pasta at Mario");
            }
        } catch (IOException | SonosControllerException e) {
            Log.e(TAG, "Sonos preset 1 command failed", e);
        }
    }

    private void onPreset2Clicked() {
        // predefined example to load the Swiss Pop radio stream
        try {
            if (sonosDevice != null) {
                sonosDevice.playUri("x-rincon-mp3radio://www.radioswisspop.ch/live/aacp.m3u", null);
                sonosDevice.setVolume(20);
                updateView(SONOS_ZONE, "Swiss Pop");
            }
        } catch (IOException | SonosControllerException e) {
            Log.e(TAG, "Sonos preset 2 command failed", e);
        }
    }

    private void allowNetworkInMainThread() {
        // network operation in main thread are not recommended, since the activity will be blocked an cannot respond to user inputs
        // however, we are create here a rapid prototype and don't care ;-)
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    private void setupSonosSpeaker() {
        List<SonosDevice> availableDevices = discoverSonosDevices();
        for (SonosDevice device : availableDevices) {
            try {
                if (device.getZoneName().equals(SONOS_ZONE)) {
                    sonosDevice = device;
                    break;
                }
            } catch (IOException | SonosControllerException e) {
                Log.e(TAG, "Failed to setup Sonos speaker", e);
            }
        }
        if (sonosDevice != null) {
            updateView(SONOS_ZONE, "connected!");
        } else {
            updateView("Connection error", ">| to repeat");
        }
    }

    private List<SonosDevice> discoverSonosDevices() {
        List<SonosDevice> devices = null;
        try {
            devices = SonosDiscovery.discover();
            for (SonosDevice device : devices) {
                Log.d(TAG, "Found Sonos " + device.getZoneName() + " with IP " + device.getSpeakerInfo().getIpAddress());
            }
        } catch (IOException | SonosControllerException e) {
            Log.e(TAG, "Failed to discover Sonos speakers", e);
        }
        return devices;
    }

    private void setupSSD1306OledDisplay() {
        try {
            screen = new Ssd1306(I2C_BUS);
        } catch (IOException e) {
            Log.e(TAG, "Error while opening SSD1306", e);
        }
        Log.d(TAG, "SSD1306 screen created");
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

    private void updateView(String line1, String line2) {
        if (screen != null) {
            Bitmap newContent = generateBitmapFromView(line1, line2);
            screen.clearPixels();
            BitmapHelper.setBmpData(screen, 0, 0, newContent, true);
            try {
                screen.show();
            } catch (IOException e) {
                Log.e(TAG, "Error updating SSD1306", e);
            }
        }
    }

    private Bitmap generateBitmapFromView(String line1, String line2) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(24f);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.LEFT);
        Bitmap textAsBitmap = Bitmap.createBitmap(screen.getLcdWidth(), screen.getLcdHeight(), ARGB_8888);
        Canvas canvas = new Canvas(textAsBitmap);
        canvas.drawText(line1, 0, 32, paint);
        canvas.drawText(line2, 0, 64, paint);
        return textAsBitmap;
    }

    private void setupButtons() {
        // use the button driver to debounce the signal in software and register callbacks
        Log.i(TAG, "Configuring GPIO pins");
        try {
            buttonPlayPause = setupButton(BUTTON_PLAY_PAUSE, new Button.OnButtonEventListener() {
                @Override
                public void onButtonEvent(Button button, boolean pressed) {
                    if (pressed) {
                        onPlayPauseClicked();
                    }
                }
            });

            buttonVolumeUp = setupButton(BUTTON_VOLUME_UP, new Button.OnButtonEventListener() {
                @Override
                public void onButtonEvent(Button button, boolean pressed) {
                    if (pressed) {
                        onVolumeUpClicked();
                    }
                }
            });

            buttonVolumeDown = setupButton(BUTTON_VOLUME_DOWN, new Button.OnButtonEventListener() {
                @Override
                public void onButtonEvent(Button button, boolean pressed) {
                    if (pressed) {
                        onVolumeDownClicked();
                    }
                }
            });

            buttonPreset1 = setupButton(BUTTON_PRESET1, new Button.OnButtonEventListener() {
                @Override
                public void onButtonEvent(Button button, boolean pressed) {
                    if (pressed) {
                        onPreset1Clicked();
                    }
                }
            });

            buttonPreset2 = setupButton(BUTTON_PRESET2, new Button.OnButtonEventListener() {
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

    private Button setupButton(String pin, Button.OnButtonEventListener buttonEventListener) throws IOException {
        Button button = new Button(pin, Button.LogicState.PRESSED_WHEN_HIGH);
        button.setDebounceDelay(BUTTON_DEBOUNCE_DELAY_MS);
        button.setOnButtonEventListener(buttonEventListener);
        return button;
    }

    private void destroyButtons() {
        try {
            buttonPlayPause.close();
            buttonVolumeUp.close();
            buttonVolumeDown.close();
            buttonPreset1.close();
            buttonPreset2.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
