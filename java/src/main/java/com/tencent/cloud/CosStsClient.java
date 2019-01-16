package com.tencent.cloud;

import com.tencent.cloud.cos.util.Request;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.TreeMap;

public class CosStsClient {

    private static final int DEFAULT_DURATION_SECONDS = 1800;

    public static JSONObject getCredential(TreeMap<String, Object> config) throws IOException {
        TreeMap<String, Object> params = new TreeMap<String, Object>();

        String policy = (String) config.get("policy");
        if (policy != null) {
            params.put("Policy", policy);
        } else {
            params.put("Policy", getPolicy(config).toString());
        }

        int durationSeconds = DEFAULT_DURATION_SECONDS;
        if (config.get("durationSeconds") != null) {
            durationSeconds = (Integer) config.get("durationSeconds");
        }
        params.put("DurationSeconds", durationSeconds);

        params.put("Name", "cos-sts-java");
        params.put("Action", "GetFederationToken");
        params.put("Version", "2018-08-13");
        params.put("Region", config.get("region"));

        String host = "sts.tencentcloudapi.com";
        String path = "/";

        String result = null;
        try {
            result = Request.send(params, (String) config.get("SecretId"),
                    (String) config.get("SecretKey"),
                    "POST", host, path);
            JSONObject jsonResult = new JSONObject(result);
            JSONObject data = jsonResult.optJSONObject("Response");
            if (data == null) {
                data = jsonResult;
            }
            long expiredTime = data.getLong("ExpiredTime");
            data.put("startTime", expiredTime - durationSeconds);
            return downCompat(data);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("result = " + result, e);
        }
    }

    // v2接口的key首字母小写，v3改成大写，此处做了向下兼容
    private static JSONObject downCompat(JSONObject resultJson) {
        JSONObject dcJson = new JSONObject();

        for (String key : resultJson.keySet()) {
            Object value = resultJson.get(key);
            if (value instanceof JSONObject) {
                dcJson.put(key.toLowerCase(), downCompat((JSONObject) value));
            } else {
                dcJson.put(key.toLowerCase(), resultJson.get(key));
            }
        }

        return dcJson;
    }

    private static JSONObject getPolicy(TreeMap<String, Object> config) {
        String bucket = (String) config.get("bucket");
        String region = (String) config.get("region");
        String allowPrefix = (String) config.get("allowPrefix");
        String[] allowActions = (String[]) config.get("allowActions");

        JSONObject policy = new JSONObject();
        policy.put("version", "2.0");

        JSONObject statement = new JSONObject();
        policy.put("statement", statement);

        statement.put("effect", "allow");
        JSONObject principal = new JSONObject();
        principal.put("qcs", "*");
        statement.put("principal", principal);

        JSONArray actions = new JSONArray();
        for (String action : allowActions) {
            actions.put(action);
        }
        statement.put("action", actions);

        int lastSplit = bucket.lastIndexOf("-");
        String shortBucketName = bucket.substring(0, lastSplit);
        String appId = bucket.substring(lastSplit + 1);

        String resource = String.format("qcs::cos:%s:uid/%s:prefix//%s/%s/%s",
                region, appId, appId, shortBucketName, allowPrefix);
        statement.put("resource", resource);

        return policy;

    }
}
