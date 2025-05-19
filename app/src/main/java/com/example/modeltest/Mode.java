package com.example.modeltest;

public class Mode {
    private String name;
    private int imageResId;

    public Mode(String name, int imageResId) {
        this.name = name;
        this.imageResId = imageResId;
    }

    public String getName() {
        return name;
    }

    public int getImageResId(){
        return imageResId;
    }
}
