package com.mrap.olshopman;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.Pair;
import android.widget.TextView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "aasd";
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        readFile();
        //mAuth = FirebaseAuth.getInstance();
        ////AuthCredential mAuthCr =
        ////mAuth.signInWithCredential()
        //FirebaseUser user = mAuth.getCurrentUser();
        //TextView txtHello = (TextView) findViewById(R.id.txtHello);
        //if (user != null) {
        //    txtHello.setText("Hello " + user.getDisplayName());
        //} else {
        //    txtHello.setText("Hello World");
        //}
    }

    void readFile() {
        TextView txtHello = (TextView) findViewById(R.id.txtHello);

        File f = new File(Environment.getExternalStorageDirectory(), "Download/olshop transaction - bundle detail.csv");
        if (!f.exists())
            return;

        final ArrayDeque<Item> items = new ArrayDeque<>();
        try {
            BufferedReader r = new BufferedReader(new FileReader(f));

            String s;
            boolean first = true;
            ArrayDeque<String> itemsStr = new ArrayDeque<>();
            ArrayDeque<String[]> mats = new ArrayDeque<>();
            while ((s = r.readLine()) != null) {
                if (first) {
                    first = false;
                    continue;
                }
                String[] strs = s.split(",");
                if (strs.length > 0 && !strs[0].isEmpty() && !itemsStr.contains(strs[0])) {
                    itemsStr.add(strs[0]);
                }
                if (strs.length > 2) {
                    mats.add(new String[] {strs[0], strs[1], strs[2]});
                }
            }
            for (String itemStr : itemsStr) {
                Item item = new Item();
                item.name = itemStr;
                items.add(item);
            }
            for (String[] mat : mats) {
                Item material = null;
                for (Item item : items) {
                    if (item.name.equals(mat[1]) && !item.name.equals(mat[0])) {
                        material = item;
                        break;
                    }
                }
                if (material != null) {
                    for (Item item : items) {
                        if (item.name.equals(mat[0])) {
                            item.materials.add(new Pair<>(material, Float.parseFloat(mat[2])));
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return;
        }

        try {
            FirebaseFirestore.setLoggingEnabled(true);
            final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            final CollectionReference itemRef = firestore.collection("items");
            final HashMap<String, String> itemRefMap = new HashMap<>();

            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (final Item s : items) {
                HashMap<String, Object> data = new HashMap<>();
                data.put("name", s.name);
                if (s.materials.isEmpty()) {
                    data.put("isbundle", false);
                    data.put("qty", 0.0f);
                } else {
                    data.put("isbundle", true);
                }
                itemRef.add(data).addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(final DocumentReference documentReference) {
                        if (s.materials.isEmpty())
                            return;
                        for (Pair<Item, Float> m : s.materials) {
                            HashMap<String, Object> data = new HashMap<>();
                            data.put("itemId", documentReference.getId());
                            data.put("ammount", m.second);
                            documentReference.collection("materials").add(data);
                        }
                        firestore.runTransaction(new Transaction.Function<Void>() {
                            @Nullable
                            @Override
                            public Void apply(@NonNull Transaction transaction) throws FirebaseFirestoreException {
                                return null;
                            }
                        }).addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                Log.d(TAG, "success adding materials of " + documentReference.getId()
                                        + " " + s.name + "!");
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.d(TAG, "fail adding materials of " + documentReference.getId()
                                        + " " + s.name + "!");
                            }
                        });
                    }
                });

                if (i > 0)
                    sb.append("\n").append(s.name);
                else
                    sb.append(s.name);
                i++;
            }

            if (i > 0)
                txtHello.setText(sb.toString());

            firestore.runTransaction(new Transaction.Function<Void>() {
                @Nullable
                @Override
                public Void apply(@NonNull Transaction transaction) throws FirebaseFirestoreException {
                    return null;
                }
            }).addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void o) {
                    Log.d(TAG, "success!");
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.d(TAG, "fail!");
                }
            });

            //System.gc();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
