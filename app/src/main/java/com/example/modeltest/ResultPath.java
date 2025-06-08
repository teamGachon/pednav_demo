package com.example.modeltest;

import java.util.List;

public class ResultPath {
    private ResultTrackOption route;
    private String message;
    private int code;

    public ResultTrackOption getRoute() { return route; }
    public void setRoute(ResultTrackOption route) { this.route = route; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
}

class ResultTrackOption {
    private List<ResultPathDetail> traoptimal;

    public List<ResultPathDetail> getTraoptimal() { return traoptimal; }
    public void setTraoptimal(List<ResultPathDetail> traoptimal) { this.traoptimal = traoptimal; }
}

class ResultPathDetail {
    private ResultDistance summary;
    private List<List<Double>> path;

    public ResultDistance getSummary() { return summary; }
    public void setSummary(ResultDistance summary) { this.summary = summary; }

    public List<List<Double>> getPath() { return path; }
    public void setPath(List<List<Double>> path) { this.path = path; }
}

class ResultDistance {
    private int distance;

    public int getDistance() { return distance; }
    public void setDistance(int distance) { this.distance = distance; }
}