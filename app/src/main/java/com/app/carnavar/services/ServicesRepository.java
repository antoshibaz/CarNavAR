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
    private ServiceConnection connectionService = null;

    private ServicesRepository() {
    }

    public static ServicesRepository getInstance() {
        return servicesRepository;
    }

    public interface RepositoryQueryCallback<T extends Service> {
        void onCall(T value);
    }

    public synchronized <T1 extends Service,
            T2 extends Binder & ServiceBinder> void startService(Context context,
                                                                 Class<T1> serviceCls) {
        if (connectionService == null) {
            connectionService = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    T1 retrievedService = (T1) ((T2) service).getService();
                    serviceRepositoryRegistry.put(serviceCls, retrievedService);
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                }
            };
        }

        if (!serviceRepositoryRegistry.containsKey(serviceCls)) {
            Intent serviceIntent = new Intent(context.getApplicationContext(), serviceCls);
            context.getApplicationContext().bindService(serviceIntent, connectionService, Context.BIND_AUTO_CREATE);
        }
    }

    public synchronized <T extends Service> void stopService(Context context, Class<T> serviceCls) {
        if (serviceRepositoryRegistry.containsKey(serviceCls)) {
            T service = (T) serviceRepositoryRegistry.remove(serviceCls);
            if (service != null) {
                context.getApplicationContext().unbindService(connectionService);
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
