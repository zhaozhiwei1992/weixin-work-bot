#!/bin/bash
#ARGS="--spring.config.location=./bootstrap.yml,bootstrap-dify.yml"
export LANG=zh_CN.UTF-8
i=0
for x in `ps -ef | grep java | grep weixin-work-bot |awk '{print $2}'`
do
kill -9 "$x"
let "i++"
done
if [ $i -eq 0 ]; then
echo "No Running weixin-work-bot Found!"
else
echo "weixin-work-bot already shutdown successfully!"
fi

nohup java -Dfile.encoding=UTF-8 -Xms256m  -Xmx256m -Dserver.port=8080 -jar  weixin-work-bot-*-SNAPSHOT.jar &> weixin-work-bot-nohup.out &