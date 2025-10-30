# 项目描述

企业微信智能机器人使用自定义API，可以自定义比如百炼，dify等。

# 待办事项

- [x] 简单回复测试tag:v0.1

- [x] 对接百炼

- [] 对接dify

# 安装要求
1. java 17+
2. springboot 3.5.5.RELEASE

# 安装部署

1. git clone 当前项目到你喜欢的目录
2. 用你喜欢的 ide 引入该项目，并加载好依赖
3. 调整一些必要参数，配置文件在application.yml下, 看注释即可。
4. 打包后将jar包上传自己的服务器上，必须有域名并且是https方式请求。我是通过nginx代理转发的。
5. 测试接口: curl -X GET https://你的域名/wx/work/robot/ , 返回success表示部署成功。

# 版本控制

该项目使用 git 进行版本管理。您可以在 tags 参看当前可用版本。

# 参考

https://developer.work.weixin.qq.com/community/question/detail?content_id=16740110965903826290

https://blog.csdn.net/qq_52011411/article/details/150932697

# 版权说明
