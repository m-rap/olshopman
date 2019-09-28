package com.mrap.olshopman;

import android.util.Pair;

import java.util.ArrayDeque;

public class Item {
    public String id = null;
    public String name;
    public ArrayDeque<Pair<Item, Double>> materials = new ArrayDeque<>();
}
