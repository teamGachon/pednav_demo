package com.example.modeltest;

public class Mode {
    private String name;
    private int imageResId;
    private String description;

    public Mode(String name, int imageResId, String description) {
        this.name = name;
        this.imageResId = imageResId;
        this.description = description;
    }

    public String getName() { return name; }
    public int getImageResId() { return imageResId; }
    public String getDescription() { return description; }
}