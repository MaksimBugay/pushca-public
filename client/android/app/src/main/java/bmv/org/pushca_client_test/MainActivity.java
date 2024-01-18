package bmv.org.pushca_client_test;

import static bmv.org.pushca.client.serialization.json.JsonUtility.toJson;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.MessageFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import bmv.org.pushca.client.PushcaWebSocket;
import bmv.org.pushca.client.PushcaWebSocketApi;
import bmv.org.pushca.client.PushcaWebSocketBuilder;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca_client_test.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    Logger rootLogger = LogManager.getLogManager().getLogger("");

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        rootLogger.info("Application was started!");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            //====================================================================
            PClient client0 = new PClient(
                    "workSpaceMain",
                    "clientJava0@test.ee",
                    "android",
                    "PUSHCA_CLIENT"
            );
            String pushcaApiUrl = "https://app-rc.multiloginapp.net/pushca";
            BiConsumer<PushcaWebSocketApi, String> messageConsumer = (ws, msg) -> {
                rootLogger.info(MessageFormat.format(
                        "{0}: message was received {1}",
                        ws.getClientInfo(), msg)
                );
            };
            try (PushcaWebSocket pushcaWebSocket0 = new PushcaWebSocketBuilder(pushcaApiUrl,
                    client0)
                    .withMessageConsumer(messageConsumer)
                    .withBinaryManifestConsumer((ws, data) -> System.out.println(toJson(data)))
                    //.withDataConsumer(dataConsumer)
                    //.withSslContext(sslContextProvider.getSslContext())
                    .build()) {
                rootLogger.info("ALL GOOD");
            }
            //====================================================================

            handler.post(() -> {
                //UI Thread work here
            });
        });
    }

}