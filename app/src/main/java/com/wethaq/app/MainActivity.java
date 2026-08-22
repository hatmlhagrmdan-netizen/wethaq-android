package com.wethaq.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.media.*;
import android.net.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final String API="https://wethaq-backend-production.up.railway.app";
    private static final String PREFS="wethaq",TOKEN="token",USER_ID="wethaq_id",NAME="name",CONTACTS="saved_contacts",FOUNDER="المؤسس: حاتم حسين الحاج رمضان",CHANNEL="wethaq_messages";
    private static final int PICK_IMAGE=4101,NOTIFY_PERMISSION=4102;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    @Override protected void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences(PREFS,MODE_PRIVATE);}
}