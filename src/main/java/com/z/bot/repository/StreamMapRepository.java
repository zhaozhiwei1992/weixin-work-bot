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

    // 按照企业微信的报文格式，这流得自己拼。绝了
    private static final Map<String, StringBuilder> streamTextBuilder = new HashMap<>();


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
            return "";
        }else{
            String poll = streamMap.get(key).poll();
            if("null".equals(poll)){
                poll = "";
            }
            if(!"messageend".equals(poll)){
                // 累积文本
                StringBuilder stringBuilder = streamTextBuilder.get(key);
                if(Objects.isNull(stringBuilder)){
                    stringBuilder = new StringBuilder();
                    stringBuilder.append(poll);
                    streamTextBuilder.put(key, stringBuilder);
                }else{
                    stringBuilder.append(poll);
                }
            }

            return poll;
        }
    }

    public String getStreamText(String key){
        StringBuilder stringBuilder = streamTextBuilder.get(key);
        if(Objects.isNull(stringBuilder)){
            return "";
        }else {
            return stringBuilder.toString();
        }
    }

    public String delete(String key){
        if(!Objects.isNull(streamMap.get(key))){
            ArrayDeque<String> strings = streamMap.get(key);
            strings.clear();
        }
        streamMap.remove(key);
        streamTextBuilder.remove(key);
        return "success";
    }
}
