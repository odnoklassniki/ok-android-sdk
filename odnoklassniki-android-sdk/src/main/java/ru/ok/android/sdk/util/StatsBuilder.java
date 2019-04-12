package ru.ok.android.sdk.util;

import java.util.Collection;
import java.util.Collections;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@SuppressWarnings("unused")
public class StatsBuilder {

    public enum Type {
        /**
         * simple counter, fe: "10"
         */
        COUNTER,
        /**
         * funnel identification, fe: "payment.card"
         */
        SELECT,
        /**
         * duration time in ms, fe: "30000"
         */
        INTERVAL,
        /**
         * progress, fe: "1" as level number, and id being "start" or "complete"
         */
        STATUS,
        //
        ;
    }

    private String version = "1.0.0";
    private long time = System.currentTimeMillis();

    private final JSONArray stats = new JSONArray();

    public StatsBuilder() {
    }

    public StatsBuilder withVersion(String version) {
        this.version = version;
        return this;
    }

    public StatsBuilder withTime(long time) {
        this.time = time;
        return this;
    }

    public StatsBuilder addCounter(Type type, String id, long stamp, Collection<String> values) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("type", type.name().toLowerCase());
        obj.put("id", id);
        obj.put("time", stamp);
        obj.put("data", new JSONArray(values));
        stats.put(obj);
        return this;
    }

    public StatsBuilder addCounter(Type type, String id, long stamp, String value) throws JSONException {
        return addCounter(type, id, stamp, Collections.singleton(value));
    }

    public JSONObject build() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("version", version);
        obj.put("time", time);
        obj.put("stats", stats);
        return obj;
    }

}
