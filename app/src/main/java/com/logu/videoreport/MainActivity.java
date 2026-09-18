package com.logu.videoreport;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    static final int REQ_RECORD = 11;
    static final int REQ_PICK = 12;
    static final int REQ_RECORD_DEST = 13;
    static final int REQ_PICK_DEST = 14;
    static final int REQ_RESTORE_OLD = 15;

    EditText empNo, empName, unit, line, remarks;
    Spinner operation;
    TextView machine, videoLabel;

    Uri localVideoUri;
    Uri pendingDriveUri;
    boolean driveCopyReady=false;

    String restoreEmpNo="";
    String restoreDate="";
    String restoreEmployee="";
    String restoreOperation="";

    SharedPreferences prefs;

    final String[] operations = {
      "ELASTIC READY / BACK TACK","LABEL READY / RUNSTITCH OPERATIONS","NECK TAPE CLOSE","BADGE ATTACH / PATCH PKT ATT","V-RIB TACK","SLIT ATTACH / CLOSE","VELT POCKET MAKE","ZIP ATTACH/ TOPSTITCH","SIDE POCKET ATTACH","COLLAR ATTACH / CLOSE",
      "RISE /SHOULDER ATTACH","SERGING / JOIN STRAIGHT SEAMS","SLEEVE ATTACH","SIDE / INSEAM ATTACH","ROUND SLEEVE","BOTTOM RIB / SLEEVE CUFF","NECK RIB ATTACH / ELASTIC ATTACH",
      "DECORATION/ TOPSTITCH","NECK BINDING","SLEEVE HEM","BOTTOM HEM","WAIST TOPSTITCH","BELT ELASTIC ATTACH",
      "BT/BH/BU","HEAT TRANSFER","FLAT SEAMER","TOP ELASTIC","MULTI NEEDLE/ NET FOLDING"
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("reports",0);
        showForm();
    }

    TextView title(String s,int size){
        TextView v=new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(Color.rgb(25,35,50));
        v.setPadding(0,12,0,8);
        return v;
    }

    EditText field(String hint){
        EditText e=new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        return e;
    }

    Button button(String t){
        Button b=new Button(this);
        b.setText(t);
        return b;
    }

    LinearLayout root(){
        LinearLayout r=new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(30,24,30,30);
        return r;
    }

    ScrollView scroll(LinearLayout r){
        ScrollView s=new ScrollView(this);
        s.addView(r);
        return s;
    }

    @Override public void onBackPressed(){
        showForm();
    }

    void showForm(){
        localVideoUri=null;
        pendingDriveUri=null;
        driveCopyReady=false;

        LinearLayout r=root();

        TextView h=title("VIDEO REPORT",24);
        h.setTextColor(Color.rgb(31,111,235));
        r.addView(h);

        r.addView(title(
            "Reliable mode: the app keeps a local playback copy and also saves the same video to Google Drive.",
            14
        ));

        r.addView(title("Employee Details",18));
        empNo=field("Emp No");
        empName=field("Employee Name");
        unit=field("Unit");
        line=field("Line No");
        r.addView(empNo);
        r.addView(empName);
        r.addView(unit);
        r.addView(line);

        r.addView(title("Operation Details",18));
        operation=new Spinner(this);
        ArrayAdapter<String> opAdapter=new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            operations
        );
        operation.setAdapter(opAdapter);
        r.addView(operation);

        machine=title("Machine: SINGLE NEEDLE LOCK STITCH",16);
        r.addView(machine);

        operation.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                machine.setText("Machine: "+machineFor(pos));
            }
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });

        remarks=field("Remarks");
        remarks.setSingleLine(false);
        remarks.setMinLines(3);
        r.addView(remarks);

        r.addView(title("Video",18));
        videoLabel=title("No video selected",14);
        r.addView(videoLabel);

        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button record=button("RECORD VIDEO");
        Button choose=button("CHOOSE VIDEO");

        row.addView(record,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(choose,new LinearLayout.LayoutParams(0,-2,1));
        r.addView(row);

        record.setOnClickListener(v->startRecordFlow());
        choose.setOnClickListener(v->pickExistingVideo());

        Button retryDrive=button("SAVE / RETRY GOOGLE DRIVE COPY");
        retryDrive.setOnClickListener(v->{
            if(localVideoUri==null){
                Toast.makeText(this,"Record or choose a video first",Toast.LENGTH_SHORT).show();
                return;
            }
            chooseDriveDestination(REQ_PICK_DEST);
        });
        r.addView(retryDrive);

        Button submit=button("SUBMIT REPORT");
        submit.setOnClickListener(v->submitReport());
        r.addView(submit);

        int count=0;
        try{
            count=new JSONArray(prefs.getString("list","[]")).length();
        }catch(Exception ignored){}

        Button history=button("VIEW REPORT HISTORY ("+count+")");
        history.setOnClickListener(v->showHistory());
        r.addView(history);

        setContentView(scroll(r));
    }

    String machineFor(int pos){
        if(pos<=9) return "SINGLE NEEDLE LOCK STITCH";
        if(pos<=16) return "OVER LOCK";
        if(pos<=22) return "FLAT LOCK";
        return "SPECIAL MACHINE";
    }

    boolean validateNameFields(){
        if(empNo==null || empName==null) return false;

        if(empNo.getText().toString().trim().isEmpty() ||
           empName.getText().toString().trim().isEmpty()){
            Toast.makeText(
                this,
                "Enter Emp No and Employee Name before recording",
                Toast.LENGTH_LONG
            ).show();
            return false;
        }

        return true;
    }

    String safe(String s,String fallback){
        String t=s==null ? "" : s.trim();
        if(t.isEmpty()) t=fallback;
        return t.replaceAll("[^a-zA-Z0-9_-]","_").replaceAll("_+","_");
    }

    String makeVideoFileName(){
        String eno=empNo==null ? "" : empNo.getText().toString();
        String ename=empName==null ? "" : empName.getText().toString();

        String op="Operation";
        if(operation!=null && operation.getSelectedItemPosition()>=0){
            op=operations[operation.getSelectedItemPosition()];
        }

        return safe(eno,"NoEmp")+"_"+safe(ename,"NoName")+"_"+safe(op,"Operation")+".mp4";
    }

    void startRecordFlow(){
        if(!validateNameFields()) return;
        chooseDriveDestination(REQ_RECORD_DEST);
    }

    void chooseDriveDestination(int requestCode){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/mp4");
        i.putExtra(Intent.EXTRA_TITLE,makeVideoFileName());
        i.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION |
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        Toast.makeText(
            this,
            "Choose Google Drive on the Save screen",
            Toast.LENGTH_LONG
        ).show();

        startActivityForResult(i,requestCode);
    }

    void pickExistingVideo(){
        if(!validateNameFields()) return;

        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("video/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION |
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        startActivityForResult(i,REQ_PICK);
    }

    Uri createLocalVideoUri(String fileName) throws Exception{
        ContentValues values=new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME,fileName);
        values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");

        if(Build.VERSION.SDK_INT>=29){
            values.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/VideoReport");
            values.put(MediaStore.Video.Media.IS_PENDING,1);
        }

        Uri uri=getContentResolver().insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values
        );

        if(uri==null) throw new IOException("Could not create local video file");
        return uri;
    }

    void finalizeLocalVideo(Uri uri){
        if(uri==null || Build.VERSION.SDK_INT<29) return;

        try{
            ContentValues values=new ContentValues();
            values.put(MediaStore.Video.Media.IS_PENDING,0);
            getContentResolver().update(uri,values,null,null);
        }catch(Exception ignored){}
    }

    void launchCamera(Uri outputUri){
        try{
            Intent i=new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT,outputUri);
            i.setClipData(ClipData.newRawUri("video",outputUri));
            i.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );

            startActivityForResult(i,REQ_RECORD);
        }catch(Exception e){
            Toast.makeText(
                this,
                "Unable to open camera: "+e.getMessage(),
                Toast.LENGTH_LONG
            ).show();
        }
    }

    void persistReadPermission(Intent data,Uri uri){
        if(data==null || uri==null) return;

        int flags=data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;

        try{
            if(flags!=0){
                getContentResolver().takePersistableUriPermission(uri,flags);
            }
        }catch(Exception ignored){}
    }

    void persistReadWritePermission(Intent data,Uri uri){
        if(data==null || uri==null) return;

        int wanted=
            Intent.FLAG_GRANT_READ_URI_PERMISSION |
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

        int flags=data.getFlags() & wanted;

        try{
            if(flags!=0){
                getContentResolver().takePersistableUriPermission(uri,flags);
            }
        }catch(Exception ignored){}
    }

    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);

        if(req==REQ_RECORD_DEST){
            if(res==RESULT_OK && data!=null && data.getData()!=null){
                pendingDriveUri=data.getData();
                persistReadWritePermission(data,pendingDriveUri);

                try{
                    localVideoUri=createLocalVideoUri(makeVideoFileName());
                    videoLabel.setText("Google Drive selected. Opening camera...");
                    launchCamera(localVideoUri);
                }catch(Exception e){
                    Toast.makeText(
                        this,
                        "Could not create local recording: "+e.getMessage(),
                        Toast.LENGTH_LONG
                    ).show();
                }
            }
            return;
        }

        if(req==REQ_RECORD){
            if(res==RESULT_OK && localVideoUri!=null){
                finalizeLocalVideo(localVideoUri);
                videoLabel.setText("Recording complete. Saving Drive copy...");
                copyLocalToDrive(localVideoUri,pendingDriveUri,false);
            }else{
                if(localVideoUri!=null){
                    try{
                        getContentResolver().delete(localVideoUri,null,null);
                    }catch(Exception ignored){}
                }

                localVideoUri=null;
                pendingDriveUri=null;
                driveCopyReady=false;
                videoLabel.setText("Recording cancelled");
            }
            return;
        }

        if(req==REQ_PICK){
            if(res==RESULT_OK && data!=null && data.getData()!=null){
                Uri source=data.getData();
                persistReadPermission(data,source);
                copyChosenVideoToLocal(source);
            }
            return;
        }

        if(req==REQ_PICK_DEST){
            if(res==RESULT_OK && data!=null && data.getData()!=null){
                pendingDriveUri=data.getData();
                persistReadWritePermission(data,pendingDriveUri);

                if(localVideoUri!=null){
                    copyLocalToDrive(localVideoUri,pendingDriveUri,false);
                }
            }
            return;
        }

        if(req==REQ_RESTORE_OLD){
            if(res==RESULT_OK && data!=null && data.getData()!=null){
                Uri source=data.getData();
                persistReadPermission(data,source);
                restoreOldReportVideo(source);
            }
        }
    }

    void copyChosenVideoToLocal(Uri source){
        final ProgressDialog pd=new ProgressDialog(this);
        pd.setTitle("Preparing video");
        pd.setMessage("Creating reliable local playback copy...");
        pd.setCancelable(false);
        pd.show();

        new Thread(()->{
            Uri created=null;

            try{
                created=createLocalVideoUri(makeVideoFileName());
                copyUri(source,created);
                finalizeLocalVideo(created);

                Uri finalCreated=created;

                runOnUiThread(()->{
                    pd.dismiss();
                    localVideoUri=finalCreated;
                    driveCopyReady=false;
                    pendingDriveUri=null;
                    videoLabel.setText("Local playback copy ready ✓");

                    chooseDriveDestination(REQ_PICK_DEST);
                });
            }catch(Exception e){
                if(created!=null){
                    try{
                        getContentResolver().delete(created,null,null);
                    }catch(Exception ignored){}
                }

                runOnUiThread(()->{
                    pd.dismiss();
                    Toast.makeText(
                        this,
                        "Could not prepare selected video: "+e.getMessage(),
                        Toast.LENGTH_LONG
                    ).show();
                });
            }
        }).start();
    }

    void copyLocalToDrive(Uri local,Uri drive,boolean submitAfter){
        if(local==null){
            Toast.makeText(this,"Local video is missing",Toast.LENGTH_LONG).show();
            return;
        }

        if(drive==null){
            driveCopyReady=false;
            videoLabel.setText("Local video ready. Google Drive copy not selected.");
            return;
        }

        final ProgressDialog pd=new ProgressDialog(this);
        pd.setTitle("Google Drive");
        pd.setMessage("Saving video copy to Google Drive...");
        pd.setCancelable(false);
        pd.show();

        new Thread(()->{
            try{
                copyUri(local,drive);

                runOnUiThread(()->{
                    pd.dismiss();
                    driveCopyReady=true;
                    videoLabel.setText("Local playback ✓   Google Drive copy ✓");
                    Toast.makeText(
                        this,
                        "Video saved to Google Drive ✓",
                        Toast.LENGTH_LONG
                    ).show();

                    if(submitAfter) saveCurrentReport();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    pd.dismiss();
                    driveCopyReady=false;
                    videoLabel.setText("Local playback ✓   Drive copy failed");

                    Toast.makeText(
                        this,
                        "Drive copy failed. Your local video is safe. Tap SAVE / RETRY GOOGLE DRIVE COPY.",
                        Toast.LENGTH_LONG
                    ).show();
                });
            }
        }).start();
    }

    void copyUri(Uri source,Uri dest) throws Exception{
        try(
            InputStream in=getContentResolver().openInputStream(source);
            OutputStream out=getContentResolver().openOutputStream(dest,"w")
        ){
            if(in==null || out==null){
                throw new IOException("Could not open video stream");
            }

            byte[] buf=new byte[1024*128];
            int n;

            while((n=in.read(buf))>0){
                out.write(buf,0,n);
            }

            out.flush();
        }
    }

    void submitReport(){
        if(!validateNameFields()) return;

        if(localVideoUri==null){
            Toast.makeText(
                this,
                "Please record or choose a video",
                Toast.LENGTH_LONG
            ).show();
            return;
        }

        if(!driveCopyReady || pendingDriveUri==null){
            Toast.makeText(
                this,
                "Google Drive copy is not ready. Please save the Drive copy first.",
                Toast.LENGTH_LONG
            ).show();
            chooseDriveDestination(REQ_PICK_DEST);
            return;
        }

        saveCurrentReport();
    }

    void saveCurrentReport(){
        try{
            JSONObject o=new JSONObject();

            o.put("empNo",empNo.getText().toString().trim());
            o.put("name",empName.getText().toString().trim());
            o.put("unit",unit.getText().toString().trim());
            o.put("line",line.getText().toString().trim());
            o.put("operation",operations[operation.getSelectedItemPosition()]);
            o.put("machine",machineFor(operation.getSelectedItemPosition()));
            o.put("remarks",remarks.getText().toString().trim());
            o.put(
                "date",
                new SimpleDateFormat("dd-MM-yyyy HH:mm",Locale.getDefault())
                    .format(new Date())
            );

            o.put("localVideo",localVideoUri.toString());
            o.put("driveVideo",pendingDriveUri.toString());
            o.put("video",pendingDriveUri.toString());
            o.put("storage","Local + Google Drive");

            JSONArray arr=new JSONArray(prefs.getString("list","[]"));
            arr.put(o);

            prefs.edit().putString("list",arr.toString()).commit();

            Toast.makeText(this,"Report saved ✓",Toast.LENGTH_LONG).show();
            showHistory();
        }catch(Exception e){
            Toast.makeText(
                this,
                "Could not save report: "+e.getMessage(),
                Toast.LENGTH_LONG
            ).show();
        }
    }

    void showHistory(){
        showHistory(prefs.getString("sortMode","Date - Newest First"));
    }

    void showHistory(String sortMode){
        LinearLayout r=root();

        Button back=button("← ADD NEW REPORT");
        back.setOnClickListener(v->showForm());
        r.addView(back);

        r.addView(title("REPORT HISTORY",24));

        try{
            JSONArray arr=new JSONArray(prefs.getString("list","[]"));
            r.addView(title("Total reports: "+arr.length(),15));

            final String[] sortOptions={
                "Date - Newest First",
                "Date - Oldest First",
                "Emp No - Low to High",
                "Emp No - High to Low",
                "Name - A to Z",
                "Name - Z to A",
                "Unit - Low to High",
                "Unit - High to Low",
                "Line - Low to High",
                "Line - High to Low",
                "Operation - A to Z",
                "Operation - Z to A",
                "Machine - A to Z",
                "Machine - Z to A"
            };

            r.addView(title("Sort Reports",16));

            Spinner sortSpinner=new Spinner(this);
            ArrayAdapter<String> sortAdapter=new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                sortOptions
            );

            sortSpinner.setAdapter(sortAdapter);

            int selected=0;

            for(int i=0;i<sortOptions.length;i++){
                if(sortOptions[i].equals(sortMode)){
                    selected=i;
                    break;
                }
            }

            sortSpinner.setSelection(selected,false);
            r.addView(sortSpinner);

            List<JSONObject> reports=new ArrayList<>();

            for(int i=0;i<arr.length();i++){
                reports.add(arr.getJSONObject(i));
            }

            Collections.sort(reports,reportComparator(sortMode));

            sortSpinner.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener(){
                    public void onItemSelected(
                        android.widget.AdapterView<?> p,
                        View v,
                        int pos,
                        long id
                    ){
                        String chosen=sortOptions[pos];

                        if(!chosen.equals(sortMode)){
                            prefs.edit().putString("sortMode",chosen).apply();
                            showHistory(chosen);
                        }
                    }

                    public void onNothingSelected(
                        android.widget.AdapterView<?> p
                    ){}
                }
            );

            if(reports.isEmpty()){
                r.addView(title("No reports yet",16));
            }

            for(JSONObject o:reports){
                LinearLayout c=new LinearLayout(this);
                c.setOrientation(LinearLayout.VERTICAL);
                c.setPadding(18,18,18,18);
                GradientDrawableCompat.bg(c);

                c.addView(title(
                    o.optString("empNo")+" - "+o.optString("name"),
                    18
                ));

                c.addView(title(o.optString("date"),13));

                c.addView(title(
                    "Unit: "+o.optString("unit")+
                    "   Line: "+o.optString("line"),
                    14
                ));

                c.addView(title(
                    "Operation: "+o.optString("operation"),
                    15
                ));

                c.addView(title(
                    "Machine: "+o.optString("machine"),
                    14
                ));

                if(!o.optString("remarks").isEmpty()){
                    c.addView(title(
                        "Remarks: "+o.optString("remarks"),
                        14
                    ));
                }

                String local=o.optString("localVideo","");
                String drive=o.optString("driveVideo",o.optString("video",""));

                if(!local.isEmpty()){
                    c.addView(title("Local playback copy: ✓",14));
                }

                if(!drive.isEmpty()){
                    c.addView(title("Google Drive copy: ✓",14));
                }

                if(!local.isEmpty()){
                    Button play=button("PLAY VIDEO");

                    play.setOnClickListener(v->playLocalVideo(
                        Uri.parse(local),
                        o.optString("empNo")+" - "+o.optString("name"),
                        o.optString("operation")
                    ));

                    c.addView(play);
                }else{
                    Button restore=button("RESTORE OLD VIDEO");

                    restore.setOnClickListener(v->{
                        restoreEmpNo=o.optString("empNo");
                        restoreDate=o.optString("date");
                        restoreEmployee=o.optString("empNo")+" - "+o.optString("name");
                        restoreOperation=o.optString("operation");

                        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.setType("video/*");
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        );

                        Toast.makeText(
                            this,
                            "Select this report's video from Google Drive once",
                            Toast.LENGTH_LONG
                        ).show();

                        startActivityForResult(i,REQ_RESTORE_OLD);
                    });

                    c.addView(restore);
                }

                LinearLayout.LayoutParams lp=
                    new LinearLayout.LayoutParams(-1,-2);

                lp.setMargins(0,0,0,18);
                r.addView(c,lp);
            }
        }catch(Exception e){
            r.addView(title(
                "Unable to load report history: "+e.getMessage(),
                16
            ));
        }

        setContentView(scroll(r));
    }

    Comparator<JSONObject> reportComparator(String sortMode){
        switch(sortMode){
            case "Date - Oldest First":
                return (a,b)->compareDate(
                    a.optString("date"),
                    b.optString("date")
                );

            case "Emp No - Low to High":
                return (a,b)->naturalCompare(
                    a.optString("empNo"),
                    b.optString("empNo")
                );

            case "Emp No - High to Low":
                return (a,b)->naturalCompare(
                    b.optString("empNo"),
                    a.optString("empNo")
                );

            case "Name - A to Z":
                return (a,b)->a.optString("name")
                    .compareToIgnoreCase(b.optString("name"));

            case "Name - Z to A":
                return (a,b)->b.optString("name")
                    .compareToIgnoreCase(a.optString("name"));

            case "Unit - Low to High":
                return (a,b)->naturalCompare(
                    a.optString("unit"),
                    b.optString("unit")
                );

            case "Unit - High to Low":
                return (a,b)->naturalCompare(
                    b.optString("unit"),
                    a.optString("unit")
                );

            case "Line - Low to High":
                return (a,b)->naturalCompare(
                    a.optString("line"),
                    b.optString("line")
                );

            case "Line - High to Low":
                return (a,b)->naturalCompare(
                    b.optString("line"),
                    a.optString("line")
                );

            case "Operation - A to Z":
                return (a,b)->a.optString("operation")
                    .compareToIgnoreCase(b.optString("operation"));

            case "Operation - Z to A":
                return (a,b)->b.optString("operation")
                    .compareToIgnoreCase(a.optString("operation"));

            case "Machine - A to Z":
                return (a,b)->a.optString("machine")
                    .compareToIgnoreCase(b.optString("machine"));

            case "Machine - Z to A":
                return (a,b)->b.optString("machine")
                    .compareToIgnoreCase(a.optString("machine"));

            case "Date - Newest First":
            default:
                return (a,b)->compareDate(
                    b.optString("date"),
                    a.optString("date")
                );
        }
    }

    void playLocalVideo(Uri uri,String employee,String operationName){
        LinearLayout r=root();

        Button back=button("← BACK TO REPORT HISTORY");
        back.setOnClickListener(v->showHistory());
        r.addView(back);

        r.addView(title("VIDEO PLAYER",24));
        r.addView(title(employee,18));
        r.addView(title(operationName,15));

        TextView status=title("Loading local video...",14);
        r.addView(status);

        VideoView vv=new VideoView(this);
        MediaController controls=new MediaController(this);
        controls.setAnchorView(vv);
        vv.setMediaController(controls);

        int h=(int)(
            getResources().getDisplayMetrics().density*360
        );

        LinearLayout.LayoutParams vp=
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                h
            );

        vp.setMargins(0,12,0,12);
        r.addView(vv,vp);

        Button retry=button("RETRY VIDEO");
        retry.setVisibility(View.GONE);
        r.addView(retry);

        setContentView(scroll(r));

        vv.setOnPreparedListener(mp->{
            status.setText("Video ready ✓");
            mp.setLooping(false);
            vv.start();
        });

        vv.setOnErrorListener((mp,what,extra)->{
            status.setText("Local video could not be played.");
            retry.setVisibility(View.VISIBLE);
            return true;
        });

        Runnable load=()->{
            retry.setVisibility(View.GONE);
            status.setText("Loading local video...");

            try{
                vv.setVideoURI(uri);
                vv.requestFocus();
            }catch(Exception e){
                status.setText("Unable to open local video.");
                retry.setVisibility(View.VISIBLE);
            }
        };

        retry.setOnClickListener(v->load.run());
        load.run();
    }

    void restoreOldReportVideo(Uri source){
        final ProgressDialog pd=new ProgressDialog(this);
        pd.setTitle("Restoring report video");
        pd.setMessage("Creating permanent local playback copy...");
        pd.setCancelable(false);
        pd.show();

        new Thread(()->{
            Uri created=null;

            try{
                String filename=
                    safe(restoreEmpNo,"Report")+"_"+safe(
                        restoreOperation,
                        "Operation"
                    )+".mp4";

                created=createLocalVideoUri(filename);
                copyUri(source,created);
                finalizeLocalVideo(created);

                updateReportLocalVideo(
                    restoreEmpNo,
                    restoreDate,
                    created,
                    source
                );

                Uri finalCreated=created;

                runOnUiThread(()->{
                    pd.dismiss();
                    Toast.makeText(
                        this,
                        "Old report video restored ✓",
                        Toast.LENGTH_LONG
                    ).show();

                    playLocalVideo(
                        finalCreated,
                        restoreEmployee,
                        restoreOperation
                    );
                });
            }catch(Exception e){
                if(created!=null){
                    try{
                        getContentResolver().delete(
                            created,
                            null,
                            null
                        );
                    }catch(Exception ignored){}
                }

                runOnUiThread(()->{
                    pd.dismiss();
                    Toast.makeText(
                        this,
                        "Could not restore video: "+e.getMessage(),
                        Toast.LENGTH_LONG
                    ).show();
                });
            }
        }).start();
    }

    void updateReportLocalVideo(
        String emp,
        String date,
        Uri local,
        Uri drive
    ){
        try{
            JSONArray arr=
                new JSONArray(prefs.getString("list","[]"));

            for(int i=0;i<arr.length();i++){
                JSONObject o=arr.getJSONObject(i);

                if(o.optString("empNo").equals(emp) &&
                   o.optString("date").equals(date)){
                    o.put("localVideo",local.toString());
                    o.put("driveVideo",drive.toString());
                    o.put("video",drive.toString());
                    o.put("storage","Local + Google Drive");
                    break;
                }
            }

            prefs.edit().putString("list",arr.toString()).commit();
        }catch(Exception ignored){}
    }

    int compareDate(String a,String b){
        try{
            SimpleDateFormat f=
                new SimpleDateFormat(
                    "dd-MM-yyyy HH:mm",
                    Locale.getDefault()
                );

            Date da=f.parse(a);
            Date db=f.parse(b);

            if(da==null || db==null){
                return a.compareToIgnoreCase(b);
            }

            return da.compareTo(db);
        }catch(Exception e){
            return a.compareToIgnoreCase(b);
        }
    }

    int naturalCompare(String a,String b){
        try{
            String ad=a.replaceAll("[^0-9]","");
            String bd=b.replaceAll("[^0-9]","");

            if(!ad.isEmpty() && !bd.isEmpty()){
                long av=Long.parseLong(ad);
                long bv=Long.parseLong(bd);

                if(av!=bv){
                    return Long.compare(av,bv);
                }
            }
        }catch(Exception ignored){}

        return a.compareToIgnoreCase(b);
    }

    public static class GradientDrawableCompat{
        static void bg(View v){
            android.graphics.drawable.GradientDrawable g=
                new android.graphics.drawable.GradientDrawable();

            g.setColor(Color.rgb(245,247,250));
            g.setCornerRadius(20);
            g.setStroke(1,Color.rgb(220,225,232));
            v.setBackground(g);
        }
    }
}
