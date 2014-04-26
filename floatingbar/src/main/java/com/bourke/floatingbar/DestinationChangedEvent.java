package com.bourke.floatingbar;

public class DestinationChangedEvent {

    public final double lat;
    public final double lon;

    public DestinationChangedEvent(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    @Override public String toString() {
        return new StringBuilder("(") //
                .append(lat) //
                .append(", ") //
                .append(lon) //
                .append(")") //
                .toString();
    }
}

