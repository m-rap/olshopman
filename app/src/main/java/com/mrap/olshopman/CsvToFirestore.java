package com.mrap.olshopman;

import android.os.Environment;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Transaction;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.HashMap;

public class CsvToFirestore {
    public static final String TAG = "CsvToFirestore";
    static final String itemFilename = "Download/olshop transaction - bundle detail.csv";
    static final String transactionFilename = "Download/olshop transaction - bundle detail.csv";
    static final String detailTransactionFilename = "Download/olshop transaction - bundle detail.csv";

    void exportItems() throws IOException {
        final ArrayDeque<Item> items = importItems();
        if (items == null) return;

        final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        final CollectionReference itemRef = firestore.collection("items");

        for (final Item it : items) {
            itemRef.whereEqualTo("name", it.name).get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                @Override
                public void onComplete(@NonNull Task<QuerySnapshot> task) {
                    if (!task.isSuccessful()) {
                        Log.d(TAG, "fail query " + it.name);
                        return;
                    }
                    if (!task.getResult().isEmpty()) {
                        Log.d(TAG, it.name + " already exists");
                        return;
                    }
                    HashMap<String, Object> data = new HashMap<>();
                    data.put("name", it.name);
                    if (it.materials.isEmpty()) {
                        data.put("isbundle", false);
                        data.put("qty", 0.0);
                    } else {
                        data.put("isbundle", true);
                    }
                    itemRef.add(data).addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                        @Override
                        public void onSuccess(final DocumentReference documentReference) {
                            if (it.materials.isEmpty())
                                return;
                            for (Pair<Item, Double> m : it.materials) {
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
                                            + " " + it.name + "!");
                                }
                            }).addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    Log.d(TAG, "fail adding materials of " + documentReference.getId()
                                            + " " + it.name + "!");
                                }
                            });
                        }
                    });
                }
            });
        }

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
    }

    ArrayDeque<Item> importItems() throws IOException {
        File f = new File(Environment.getExternalStorageDirectory(), itemFilename);
        if (!f.exists()) {
            f = new File(Environment.getDataDirectory(), itemFilename);
        }
        if (!f.exists()) {
            return null;
        }

        final ArrayDeque<Item> items = new ArrayDeque<>();
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
                        item.materials.add(new Pair<>(material, Double.parseDouble(mat[2])));
                    }
                }
            }
        }
        return items;
    }

    ArrayDeque<OlshopTransaction> importTransaction() throws IOException, ParseException, NumberFormatException {
        File f = new File(Environment.getExternalStorageDirectory(), transactionFilename);
        if (!f.exists()) {
            f = new File(Environment.getDataDirectory(), transactionFilename);
        }
        if (!f.exists()) {
            return null;
        }

        final ArrayDeque<OlshopTransaction> olTrans = new ArrayDeque<>();
        BufferedReader r = new BufferedReader(new FileReader(f));

        String s;
        boolean first = true;
        ArrayDeque<String> olTranStrs = new ArrayDeque<>();
        while ((s = r.readLine()) != null) {
            if (first) {
                first = false;
                continue;
            }
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy");

            String[] ss = s.split(",");
            OlshopTransaction olTran = new OlshopTransaction();
            olTran.tempId              = Integer.parseInt(ss[0]);
            olTran.isSell              = ss[1].equals("jual") ? true : false;
            olTran.currency            = ss[2];
            olTran.toIdr               = Double.parseDouble(ss[3]);
            olTran.intShippingCost     = Double.parseDouble(ss[4]);
            olTran.localShippingCost   = Double.parseDouble(ss[5]);
            olTran.insurance           = Double.parseDouble(ss[6]);
            olTran.orderDate           = sdf.parse(ss[7]);
            olTran.arriveDate          = sdf.parse(ss[8]);
            olTran.status              = ss[13];
            olTran.platform            = ss[14];
            olTran.user                = ss[15];
            olTran.userId              = ss[16];
            olTran.shippingName        = ss[17];
            olTran.shippingAddress     = ss[18];
            olTran.courier             = ss[19];
            olTran.courierTrackingCode = ss[20];
            olTran.address             = ss[21];
            olTran.city                = ss[22];
            olTran.province            = ss[23];
            olTran.postalCode          = ss[24];
            olTran.phone               = ss[25];
            olTran.fee                 = Double.parseDouble(ss[26]);
            olTrans.add(olTran);
        }
        return olTrans;
    }
}
