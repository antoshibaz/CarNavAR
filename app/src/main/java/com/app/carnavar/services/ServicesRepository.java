package com.app.carnavar.services;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;

import java.util.HashMap;

public class ServicesRepository {

    private static final ServicesRepository servicesRepository = new ServicesRepository();

    private static HashMap<Class<? extends Service>, ? super Service> serviceRepositoryRegistry = new HashMap<>();
    private static HashMap<Class<? extends Service>, ServiceConnection> serviceConnectionCallbacksRegistry = new HashMap<>();

    private ServicesRepository() {
    }

    public static ServicesRepository getInstance() {
        return servicesRepository;
    }

    public interface ServiceStartedCallback {
        void onServiceStarted();
    }

    public interface RepositoryQueryCallback<T extends Service> {
        void onCall(T serviceInstance);
    }

    public synchronized <T1 extends Service,
            T2 extends Binder & ServiceBinder> void startService(Context context,
                                                                 Class<T1> serviceCls) {

        ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                T1 retrievedService = (T1) ((T2) service).getService();
                serviceRepositoryRegistry.put(serviceCls, retrievedService);
                serviceConnectionCallbacksRegistry.put(serviceCls, this);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };


        if (!serviceRepositoryRegistry.containsKey(serviceCls) && !serviceConnectionCallbacksRegistry.containsKey(serviceCls)) {
            Intent serviceIntent = new Intent(context.getApplicationContext(), serviceCls);
            context.getApplicationContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    public synchronized <T1 extends Service,
            T2 extends Binder & ServiceBinder> void startService(Context context,
                                                                 Class<T1> serviceCls,
                                                                 ServiceStartedCallback serviceStartedCallback) {

        ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                T1 retrievedService = (T1) ((T2) service).getService();
                serviceRepositoryRegistry.put(serviceCls, retrievedService);
                serviceConnectionCallbacksRegistry.put(serviceCls, this);
                serviceStartedCallback.onServiceStarted();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };


        if (!serviceRepositoryRegistry.containsKey(serviceCls) && !serviceConnectionCallbacksRegistry.containsKey(serviceCls)) {
            Intent serviceIntent = new Intent(context.getApplicationContext(), serviceCls);
            context.getApplicationContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    public synchronized <T extends Service> void stopService(Context context, Class<T> serviceCls) {
        if (serviceRepositoryRegistry.containsKey(serviceCls) && serviceConnectionCallbacksRegistry.containsKey(serviceCls)) {
            T service = (T) serviceRepositoryRegistry.get(serviceCls);
            ServiceConnection serviceConnection = serviceConnectionCallbacksRegistry.get(serviceCls);
            if (service != null && serviceConnection != null) {
                context.getApplicationContext().unbindService(serviceConnection);
                serviceRepositoryRegistry.remove(serviceCls);
                serviceConnectionCallbacksRegistry.remove(serviceCls);
            }
        }
    }

    public synchronized <T extends Service> void getService(Class<T> serviceCls, RepositoryQueryCallback<T> queryCallback) {
        if (serviceRepositoryRegistry.containsKey(serviceCls)) {
            T service = (T) serviceRepositoryRegistry.get(serviceCls);
            queryCallback.onCall(service);
        }
    }
}
