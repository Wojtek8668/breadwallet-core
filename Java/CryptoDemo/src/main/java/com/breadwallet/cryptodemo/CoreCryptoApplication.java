package com.breadwallet.cryptodemo;

import android.app.Activity;
import android.app.Application;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.content.Intent;
import android.os.StrictMode;

import com.breadwallet.corecrypto.CryptoApiProvider;
import com.breadwallet.crypto.Account;
import com.breadwallet.crypto.CryptoApi;
import com.breadwallet.crypto.WalletManagerMode;
import com.breadwallet.crypto.blockchaindb.BlockchainDb;
import com.breadwallet.crypto.System;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;

import static com.google.common.base.Preconditions.checkState;

public class CoreCryptoApplication extends Application {

    private static final String BDB_BASE_URL = BuildConfig.BDB_BASE_URL;
    private static final String API_BASE_URL = BuildConfig.API_BASE_URL;
    private static final boolean IS_MAINNET = BuildConfig.IS_MAINNET;

    private static final String EXTRA_WIPE = "WIPE";
    private static final String EXTRA_TIMESTAMP = "TIMESTAMP";
    private static final String EXTRA_PAPER_KEY = "PAPER_KEY";
    private static final String EXTRA_MODE = "MODE";

    private static final boolean DEFAULT_WIPE = true;
    private static final long DEFAULT_TIMESTAMP = 0;
    private static final String DEFAULT_PAPER_KEY = "boring head harsh green empty clip fatal typical found crane dinner timber";
    private static final WalletManagerMode DEFAULT_MODE = WalletManagerMode.API_ONLY;

    private static System system;
    private static CoreSystemListener listener;
    private static byte[] paperKey;

    private static AtomicBoolean runOnce = new AtomicBoolean(false);

    private static LifecycleObserver observer = new LifecycleObserver() {
        @OnLifecycleEvent(Lifecycle.Event.ON_START)
        void onEnterForeground() {
            system.start();
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
        void onEnterBackground() {
            system.stop();
        }
    };

    public static void initialize(Activity startingActivity) {
        if (!runOnce.getAndSet(true)) {
            Intent intent = startingActivity.getIntent();

            paperKey = (intent.hasExtra(EXTRA_PAPER_KEY) ? intent.getStringExtra(EXTRA_PAPER_KEY) : DEFAULT_PAPER_KEY)
                    .getBytes(StandardCharsets.UTF_8);

            boolean wipe = intent.getBooleanExtra(EXTRA_WIPE, DEFAULT_WIPE);
            long timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, DEFAULT_TIMESTAMP);
            WalletManagerMode mode = intent.hasExtra(EXTRA_MODE) ? WalletManagerMode.valueOf(intent.getStringExtra(EXTRA_MODE)) : DEFAULT_MODE;

            File storageFile = new File(startingActivity.getFilesDir(), "core");
            if (wipe) {
                if (storageFile.exists()) deleteRecursively(storageFile);
                checkState(storageFile.mkdirs());
            }

            CryptoApi.initialize(CryptoApiProvider.getInstance());

            listener = new CoreSystemListener(mode);

            String uids = UUID.nameUUIDFromBytes(paperKey).toString();
            Account account = Account.createFromPhrase(paperKey, new Date(TimeUnit.SECONDS.toMillis(timestamp)), uids);

            BlockchainDb query = new BlockchainDb(new OkHttpClient(), BDB_BASE_URL, API_BASE_URL);
            system = System.create(Executors.newSingleThreadExecutor(), listener, account, storageFile.getAbsolutePath(), query);
            system.initialize(getNetworks(IS_MAINNET), IS_MAINNET);

            ProcessLifecycleOwner.get().getLifecycle().addObserver(observer);
        }
    }

    public static System getSystem() {
        return system;
    }

    public static CoreSystemListener getListener() {
        return listener;
    }

    public static byte[] getPaperKey() {
        return paperKey;
    }

    public static boolean isIsMainnet() {
        return IS_MAINNET;
    }

    private static void deleteRecursively (File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private static List<String> getNetworks(boolean isMainnet) {
        String suffix = isMainnet ? "mainnet" : "testnet";
        return Arrays.asList("bitcoin-" + suffix, "ethereum-" + suffix);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        StrictMode.enableDefaults();
    }
}