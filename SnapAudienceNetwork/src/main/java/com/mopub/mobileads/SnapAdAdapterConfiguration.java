package com.mopub.mobileads;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mopub.common.BaseAdapterConfiguration;
import com.mopub.common.OnNetworkInitializationFinishedListener;
import com.mopub.common.Preconditions;
import com.mopub.common.logging.MoPubLog;
import com.mopub.mobileads.snapaudiencenetwork.BuildConfig;
import com.snap.adkit.dagger.AdKitApplication;
import com.snap.adkit.external.SnapAdEventListener;
import com.snap.adkit.external.SnapAdInitSucceeded;
import com.snap.adkit.external.SnapAdKit;
import com.snap.adkit.external.SnapAdKitEvent;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CUSTOM;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CUSTOM_WITH_THROWABLE;
import static com.mopub.mobileads.MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR;
import static com.mopub.mobileads.MoPubErrorCode.ADAPTER_INITIALIZATION_SUCCESS;

public class SnapAdAdapterConfiguration extends BaseAdapterConfiguration {
    private static final String ADAPTER_NAME = SnapAdAdapterConfiguration.class.getSimpleName();
    private static final String ADAPTER_VERSION = BuildConfig.VERSION_NAME;
    private static final String APP_ID_KEY = "appId";
    private static final String MOPUB_NETWORK_NAME = BuildConfig.NETWORK_NAME;
    private static final String TEST_MODE_KEY = "enableTestMode";
    private static final String TEST_MODE_ENABLE_VALUE = "true";

    private SnapAdKit snapAdKit;
    private final AtomicReference<String> tokenReference = new AtomicReference((Object)null);
    private final AtomicBoolean isComputingToken = new AtomicBoolean(false);

    @NonNull
    @Override
    public String getAdapterVersion() {
        return ADAPTER_VERSION;
    }

    @Nullable
    @Override
    public String getBiddingToken(@NonNull Context context) {
        this.refreshBidderToken();
        return tokenReference.get();
    }

    @NonNull
    @Override
    public String getMoPubNetworkName() {
        return MOPUB_NETWORK_NAME;
    }

    @NonNull
    @Override
    public String getNetworkSdkVersion() {
        final String adapterVersion = getAdapterVersion();
        return adapterVersion.substring(0, adapterVersion.lastIndexOf('.'));
    }

    @Override
    public void initializeNetwork(@NonNull Context context, @Nullable Map<String, String> configuration,
                                  @NonNull OnNetworkInitializationFinishedListener listener) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(listener);

        boolean networkInitializationSucceeded = false;

        synchronized (SnapAdAdapterConfiguration.class) {
            try {
                if (configuration != null && !configuration.isEmpty()) {
                    AdKitApplication.init(context);

                    final String appId = configuration.get(APP_ID_KEY);
                    Boolean isTestModeEnable = false;
                    String testModeSetting = (String)configuration.get(TEST_MODE_KEY);
                    if (!TextUtils.isEmpty(testModeSetting)) {
                        if (testModeSetting.equalsIgnoreCase(TEST_MODE_ENABLE_VALUE)) {
                            isTestModeEnable = true;
                        }
                    }

                    snapAdKit = AdKitApplication.getSnapAdKit();
                    snapAdKit.setupListener(new SnapAdEventListener() {
                        @Override
                        public void onEvent(SnapAdKitEvent snapAdKitEvent, String slotId) {
                            if (snapAdKitEvent instanceof SnapAdInitSucceeded) {
                                tokenReference.set(snapAdKit.requestBidToken());
                            }
                        }
                    });
                    snapAdKit.init(isTestModeEnable);

                    if (!TextUtils.isEmpty(appId)) {
                        MoPubLog.log(CUSTOM, ADAPTER_NAME, "Initializing Snap Ad Kit.");

                        snapAdKit.register(appId, null);
                        networkInitializationSucceeded = true;
                    } else {
                        MoPubLog.log(CUSTOM, ADAPTER_NAME, "Snap Ad Kit's initialization not " +
                                "started the app ID is null/empty. Make sure you pass in a valid " +
                                "app ID via .withMediatedNetworkConfiguration() when initializing " +
                                "the MoPub SDK.");
                    }
                }
            } catch (Exception e) {
                MoPubLog.log(CUSTOM_WITH_THROWABLE, "Initializing Snap Ad Kit has encountered " +
                        "an exception.", e);
            }
        }

        if (networkInitializationSucceeded) {
            listener.onNetworkInitializationFinished(SnapAdAdapterConfiguration.class,
                    MoPubErrorCode.ADAPTER_INITIALIZATION_SUCCESS);
            MoPubLog.log(CUSTOM, ADAPTER_NAME, ADAPTER_INITIALIZATION_SUCCESS);
        } else {
            listener.onNetworkInitializationFinished(SnapAdAdapterConfiguration.class,
                    MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
            MoPubLog.log(CUSTOM, ADAPTER_NAME, ADAPTER_CONFIGURATION_ERROR);
        }
    }


    private void refreshBidderToken() {
        if (this.isComputingToken.compareAndSet(false, true)) {
            (new Thread(new Runnable() {
                public void run() {
                    String token = snapAdKit.requestBidToken();
                    if (token != null) {
                        SnapAdAdapterConfiguration.this.tokenReference.set(token);
                    }

                    SnapAdAdapterConfiguration.this.isComputingToken.set(false);
                }
            })).start();
        }

    }
}
