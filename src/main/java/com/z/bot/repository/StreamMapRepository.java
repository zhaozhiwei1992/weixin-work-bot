package com.z.bot.repository;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @Title: null.java
 * @Package: com.z.bot.repository
 * @Description: TODO
 * @author: zhaozhiwei
 * @date: 2025/10/29 22:42
 * @version: V1.0
 */
@Component
public class StreamMapRepository {

    // 存储流式数据，方便企业微信后续请求返回
    private static final Map<String, ArrayDeque<String>> streamMap = new HashMap<>();

    public void add(String key, String value){
        ArrayDeque<String> datas = streamMap.get(key);
        if(Objects.isNull(datas) || datas.isEmpty()){
            datas = new ArrayDeque<>();
            datas.add(value);
            streamMap.put(key, datas);
        }else{
            datas.add(value);
        }
    }

    public String poll(String key){
        if(Objects.isNull(streamMap.get(key))){
            return null;
        }else{
            return streamMap.get(key).poll();
        }
    }
}
