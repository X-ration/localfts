# 适用于网页版客户端的文件传输服务器

----------
适用于网页版客户端的文件传输服务器。
适用浏览器：
- win11 Edge
- win11 Chrome
- win10 Edge
- win10 Chrome
- ubuntu Mozilla firefox
- win7 ie、qq浏览器
- winxp ie、qq浏览器、360安全浏览器
- 安卓/iPadOS浏览器

下载文件支持迅雷下载、多范围下载。

运行release版本时可以通过命令行参数覆盖application.yml配置，如：
`java -jar localfts-server-1.0.3.jar --localfts.root_path=D:\Users\Adam`

## v1.1.0 功能更新：
* 在受支持的浏览器中支持上传文件夹(Chrome和基于Chromium的浏览器、Opera、Firefox)
![localfts-server-1.1.0-upload-directory.png](readme/localfts-server-1.1.0-upload-directory.png)
* 改进了上传页面，当请求路径不存在时页面给予友好提示
* 列表页增加当前目录
* 支持进入压缩页面压缩并下载文件夹(zip)，支持在列表页面下载已经压缩好的文件夹
* 当应用关闭时自动清理压缩文件所在文件夹(`localfts.zip_folder.delete_on_exit`控制)
* 下载文件当文件不存在时跳转到错误页面并展示404状态码
* 列表页面生成可点击链接时不再硬编码，而是根据配置的context-path拼接链接
![localfts-server-1.1.0-list-page.png](readme/localfts-server-1.1.0-list-page.png)
![localfts-server-1.1.0-compress-page.png](readme/localfts-server-1.1.0-compress-page.png)
* 支持在压缩文件夹前检查文件夹大小是否小于指定值，若小于则不进行压缩

## v1.0.5 代码优化：
- 将所有配置和向控制台输出信息的方法整理到单独的bean中：[LocalFtsServerConfig.java](src/main/java/com/adam/localfts/webserver/config/server/LocalFtsServerConfig.java)
- 预留替换Spring Boot jar包中application.yml的方法

## v1.0.4 问题修复 & 功能更新：
- 修复下载文件时文件名由空格变为加号的问题
- 修复中文文件夹上传文件后文件上传成功但是页面报错的问题
- 修复IE6对错误状态码不展示错误页面的问题
- 检查启动选项并输出相关信息
- 支持根路径带空格
- 支持浏览器多线程下载、断点续传
- 展示服务器时间、文件修改时间
  ![localfts-server-1.0.4-add-time-file-last-modified.png](readme/localfts-server-1.0.4-add-time-file-last-modified.png)

## v1.0.3 功能更新：
- 增加上传文件功能

## v1.0.1 功能更新：
- 点击服务器网络信息按钮查看服务器网卡信息。
  ![localfts-server-1.0.1-server-ip-info.png](readme/localfts-server-1.0.1-server-ip-info.png)
- 添加自定义错误页面。
  ![localfts-server-1.0.1-custom-error-page.png](readme/localfts-server-1.0.1-custom-error-page.png)

## v1.0
提供Web服务展示根路径下的文件列表，提供文件下载功能。
![localfts-server-1.0.png](readme/localfts-server-1.0.png)