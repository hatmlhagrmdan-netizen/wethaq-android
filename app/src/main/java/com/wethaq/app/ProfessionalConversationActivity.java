package com.wethaq.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public final class ProfessionalConversationActivity extends Activity {
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        String target=getIntent().getStringExtra("notification_target");
        String name=getIntent().getStringExtra("notification_name");
        Intent i=new Intent(this,MainActivity.class);
        if(target!=null&&!target.trim().isEmpty())i.putExtra("notification_target",target.trim());
        if(name!=null&&!name.trim().isEmpty())i.putExtra("notification_name",name.trim());
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }
}