
JeecgBoot AI低代码平台
===============

当前最新版本： 3.8.1（发布日期：2025-06-30） 


[![AUR](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg)](https://github.com/jeecgboot/JeecgBoot/blob/master/LICENSE)
[![](https://img.shields.io/badge/Author-北京国炬软件-orange.svg)](http://guojusoft.com)
[![](https://img.shields.io/badge/version-3.8.1-brightgreen.svg)](https://github.com/jeecgboot/JeecgBoot)
[![GitHub stars](https://img.shields.io/github/stars/zhangdaiscott/jeecg-boot.svg?style=social&label=Stars)](https://github.com/jeecgboot/JeecgBoot)
[![GitHub forks](https://img.shields.io/github/forks/zhangdaiscott/jeecg-boot.svg?style=social&label=Fork)](https://github.com/jeecgboot/JeecgBoot)



项目介绍
-----------------------------------

<h3 align="center">企业级AI低代码平台</h3>

JeecgBoot是一款企业级低代码平台集成了AI应用平台功能，旨在帮助开发者快速实现低代码开发和构建、部署个性化的 AI 应用。
前后端分离架构Ant Design&Vue3，SpringBoot，SpringCloud Alibaba，Mybatis-plus，Shiro，强大的代码生成器让前后端代码一键生成，无需写任何代码！
成套AI大模型功能: AI模型管理、AI应用、知识库、AI流程编排、AI对话助手等；
引领AI低代码开发模式: AIGC生成->OnlineCoding-> 代码生成-> 手工MERGE， 帮助Java项目解决80%的重复工作，让开发更多关注业务，快速提高效率 节省成本，同时又不失灵活性！


JeecgBoot 提供了一系列 `低代码能力`，实现`真正的零代码`在线开发：Online表单开发、Online报表、复杂报表设计、打印设计、在线图表设计、仪表盘设计、大屏设计、移动图表能力、表单设计器、在线设计流程、流程自动化配置、插件能力（可插拔）

`AI赋能低代码:` 目前提供了AI应用、AI模型管理、AI流程编排、AI对话助手，AI建表、AI写文章、AI知识库问答、AI字段建议等功能;支持各种AI大模型ChatGPT、DeepSeek、Ollama、智普、千问等.

`JEECG宗旨是:` 简单功能由OnlineCoding配置实现，做到`零代码开发`；复杂功能由代码生成器生成进行手工Merge 实现`低代码开发`，既保证了`智能`又兼顾`灵活`；实现了低代码开发的同时又支持灵活编码，解决了当前低代码产品普遍不灵活的弊端！

`JEECG业务流程:` 采用工作流来实现、扩展出任务接口，供开发编写业务逻辑，表单提供多种解决方案： 表单设计器、online配置表单、编码表单。同时实现了流程与表单的分离设计（松耦合）、并支持任务节点灵活配置，既保证了公司流程的保密性，又减少了开发人员的工作量。





适用项目
-----------------------------------
JeecgBoot AI低代码平台，可以应用在任何J2EE项目的开发中，支持信创国产化。尤其适合SAAS项目、企业信息管理系统（MIS）、内部办公系统（OA）、企业资源计划系统（ERP）、客户关系管理系统（CRM）等，其半智能手工Merge的开发方式，可以显著提高开发效率70%以上，极大降低开发成本。
又是一个全栈式 AI 开发平台，快速帮助企业构建和部署个性化的 AI 应用。


信创国产化
-----------------------------------
JeecgBoot 是一个开源低代码开发平台，支持全信创环境。它兼容多种国产操作系统和数据库，包括：

- 操作系统：国产麒麟、银河麒麟等国产系统几乎都是基于 Linux 内核，因此它们具有良好的兼容性。
- 数据库：达梦、人大金仓、TiDB , [转库文档](https://my.oschina.net/jeecg/blog/4905722)
- 中间件：东方通 TongWeb、TongRDS，宝兰德 AppServer、CacheDB, [信创配置文档](https://help.jeecg.com/java/tongweb-deploy/)

通过这些适配，JeecgBoot 为使用国产软件和硬件的用户提供了高效的开发解决方案。


版本说明
-----------------------------------

|下载    | JDK17 + SpringBoot2.7                              | JDK17 + SpringBoot3.3 + Shiro                                               |JDK17 + SpringBoot3.3+ SpringAuthorizationServer   |
|------|----------------------------------------------------|-----------------------------------------------------------------------------|--------------------------------------------|
| Github | [`master`](https://github.com/jeecgboot/JeecgBoot) | [`springboot3`](https://github.com/jeecgboot/JeecgBoot/tree/springboot3) 分支 | [`springboot3_sas`](https://github.com/jeecgboot/JeecgBoot/tree/springboot3_sas) 分支 |
| Gitee | [`master`](https://gitee.com/jeecg/JeecgBoot)      | [`springboot3`](https://gitee.com/jeecg/JeecgBoot/tree/springboot3/) 分支     | [`springboot3_sas`](https://gitee.com/jeecg/JeecgBoot/tree/springboot3_sas) 分支 |


- `jeecg-boot` 是后端JAVA源码项目（支持单体和微服务切换）.
- `jeecgboot-vue3` 是前端VUE3源码项目（vue3+vite6+ts最新技术栈）.
- `JeecgUniapp`  是[配套APP框架](https://github.com/jeecgboot/JeecgUniapp) 适配多个终端，支持APP、小程序、H5、鸿蒙、鸿蒙Next.
- 参考 [文档](https://help.jeecg.com/ui/2dev/mini) 可以删除不需要的demo，制作一个精简版本





启动项目
-----------------------------------

- [IDEA启动前后端项目](https://help.jeecg.com/java/setup/idea/startup)
- [Docker一键启动前后端](https://help.jeecg.com/java/docker/quick)



技术文档
-----------------------------------

- 官方网站：  [http://www.jeecg.com](http://www.jeecg.com)
- 开发文档：  [文档中心](https://help.jeecg.com) | [AIGC大模块](https://help.jeecg.com/aigc) | [低代码初体验一分钟](https://jeecg.blog.csdn.net/article/details/106079007) 
- 新手指南： [快速入门](http://www.jeecg.com/doc/quickstart) | [入门视频](http://jeecg.com/doc/video) | [反馈问题](https://github.com/jeecgboot/JeecgBoot/issues/new?template=bug_report.md)
- QQ交流群 ： 964611995、⑩716488839(满)、⑨808791225(满)、其他(满)


AI应用平台功能介绍
-----------------------------------

> [AI应用平台详细介绍，点击查看](README-AI.md)

JeecgBoot 平台的AIGC功能模块，是一套类似`Dify`的`AIGC应用开发平台`+`知识库问答`，是一款基于LLM大语言模型AI应用平台和 RAG 的知识库问答系统。
其直观的界面结合了 AI 流程编排、RAG 管道、知识库管理、模型管理、对接向量库、实时运行可观察等，让您可以快速从原型到生产，拥有AI服务能力。



为什么选择JeecgBoot?
-----------------------------------
* 1.采用最新主流前后分离框架（Springboot+Mybatis+antd+vue3），容易上手; 代码生成器依赖性低,灵活的扩展能力，可快速实现二次开发;
* 2.支持微服务SpringCloud Alibaba(Nacos、Gateway、Sentinel、Skywalking)，提供切换机制支持单体和微服务自由切换
* 3.开发效率高,采用代码生成器，单表、树列表、一对多、一对一等数据模型，增删改查功能一键生成，菜单配置直接使用；引入AI能力，支持自动建表等功能；
* 4.代码生成器提供强大模板机制，支持自定义模板，目前提供四套风格模板（单表两套、树模型一套、一对多三套）
* 5.代码生成器非常智能，在线业务建模、在线配置、所见即所得支持23种类控件，一键生成前后端代码，大幅度提升开发效率，不再为重复工作发愁。
* 6.AI能力：目前JeecgBoot支持AI大模型chatgpt和deepseek，现在最新版默认使用deepseek，速度更快质量更高。目前提供了AI对话助手、AI建表、AI报表等功能。
* 6.低代码能力：Online在线表单（无需编码，通过在线配置表单，实现表单的增删改查，支持单表、树、一对多、一对一等模型，实现人人皆可编码）
* 7.低代码能力：Online在线报表、Online在线图表（无需编码，通过在线配置方式，实现数据报表和图形报表，可以快速抽取数据，减轻开发压力，实现人人皆可编码）
* 9.封装完善的用户、角色、菜单、组织机构、数据字典、在线定时任务等基础功能，支持访问授权、按钮权限、数据权限等功能
* 10.常用共通封装，各种工具类(定时任务,短信接口,邮件发送,Excel导入导出等),基本满足80%项目需求
* 11.简易Excel导入导出，支持单表导出和一对多表模式导出，生成的代码自带导入导出功能
* 12.集成简易报表工具，图像报表和数据导出非常方便，可极其方便的生成图形报表、pdf、excel、word等报表；
* 13.采用前后分离技术，页面UI风格精美，针对常用组件做了封装：时间、行表格控件、截取显示控件、报表组件，编辑器等等
* 14.查询过滤器：查询功能自动生成，后台动态拼SQL追加查询条件；支持多种匹配方式（全匹配/模糊查询/包含查询/不匹配查询）；
* 15.数据权限（精细化数据权限控制，控制到行级，列表级，表单字段级，实现不同人看不同数据，不同人对同一个页面操作不同字段
* 16.页面校验自动生成(必须输入、数字校验、金额校验、时间空间等);
* 17.支持SAAS服务模式，提供SaaS多租户架构方案。
* 18.分布式文件服务，集成minio、阿里OSS等优秀的第三方，提供便捷的文件上传与管理，同时也支持本地存储。
* 19.主流数据库兼容，一套代码完全兼容Mysql、Postgresql、Oracle、Sqlserver、MariaDB、达梦、人大金仓等主流数据库。
* 20.集成工作流flowable，并实现了只需在页面配置流程转向，可极大的简化bpm工作流的开发；用bpm的流程设计器画出了流程走向，一个工作流基本就完成了，只需写很少量的java代码；
* 21.低代码能力：在线流程设计，采用开源flowable流程引擎，实现在线画流程,自定义表单,表单挂靠,业务流转
* 22.多数据源：及其简易的使用方式，在线配置数据源配置，便捷的从其他数据抓取数据；
* 23.提供单点登录CAS集成方案，项目中已经提供完善的对接代码
* 24.低代码能力：表单设计器，支持用户自定义表单布局，支持单表，一对多表单、支持select、radio、checkbox、textarea、date、popup、列表、宏等控件
* 25.专业接口对接机制，统一采用restful接口方式，集成swagger-ui在线接口文档，Jwt token安全验证，方便客户端对接
* 26.接口安全机制，可细化控制接口授权，非常简便实现不同客户端只看自己数据等控制
* 27.高级组合查询功能，在线配置支持主子表关联查询，可保存查询历史
* 28.提供各种系统监控，实时跟踪系统运行情况（监控 Redis、Tomcat、jvm、服务器信息、请求追踪、SQL监控）
* 29.消息中心（支持短信、邮件、微信推送等等）
* 30.集成Websocket消息通知机制
* 31.移动自适应效果优秀，提供APP发布方案：
* 32.支持多语言，提供国际化方案；
* 33.数据变更记录日志，可记录数据每次变更内容，通过版本对比功能查看历史变化
* 34.平台UI强大，实现了移动自适应
* 35.平台首页风格，提供多种组合模式，支持自定义风格
* 36.提供简单易用的打印插件，支持谷歌、火狐、IE11+ 等各种浏览器
* 37.示例代码丰富，提供很多学习案例参考
* 38.采用maven分模块开发方式
* 39.支持菜单动态路由
* 40.权限控制采用 RBAC（Role-Based Access Control，基于角色的访问控制）
* 41.提供新行编辑表格JVXETable，轻松满足各种复杂ERP布局，拥有更高的性能、更灵活的扩展、更强大的功能
* 42.提供仪表盘设计器，类大屏设计支持移动端，免费的数据可视化设计工具，支持丰富的数据源连接，能够通过拖拉拽方式快速制作图表和门户设计；目前支持多种图表类型：柱形图、折线图、散点图、饼图、环形图、面积图、漏斗图、进度图、仪表盘、雷达图、地图等等；




技术架构：
-----------------------------------

#### 后端

- IDE建议： IDEA (必须安装lombok插件 )
- 语言：Java 默认jdk17(支持jdk8、jdk21)
- 依赖管理：Maven
- 基础框架：Spring Boot 2.7.18
- 微服务框架： Spring Cloud Alibaba 2021.0.6.2
- 持久层框架：MybatisPlus 3.5.3.2
- 报表工具： JimuReport 1.9.5
- 安全框架：Apache Shiro 1.13.0，Jwt 4.5.0
- 微服务技术栈：Spring Cloud Alibaba、Nacos、Gateway、Sentinel、Skywalking
- 数据库连接池：阿里巴巴Druid 1.1.24
- AI大模型：支持 `ChatGPT` `DeepSeek`切换
- 日志打印：logback
- 缓存：Redis
- 其他：autopoi, fastjson，poi，Swagger-ui，quartz, lombok（简化代码）等。
- 默认数据库脚本：MySQL5.7+
- [其他数据库，需要自己转](https://my.oschina.net/jeecg/blog/4905722)


#### 前端

- 前端IDE建议：WebStorm、Vscode
- 采用 Vue3.0+TypeScript+Vite6+Ant-Design-Vue等新技术方案，包括二次封装组件、utils、hooks、动态菜单、权限校验、按钮级别权限控制等功能
- 最新技术栈：Vue3.0 + TypeScript + Vite6 + ant-design-vue4 + pinia + echarts + unocss + vxe-table + qiankun + es6
- 依赖管理：node、npm、pnpm


#### 前端环境要求

*   本地环境安装 `Node.js 、npm 、pnpm`
*   pnpm 要求`9+` 版本以上
*   Node.js 版本建议`v20.15.0`，要求`Node 20+` 版本以上

 ` ( 因为Vite6 需要 Node.js 18 / 20+ )`


#### 平台支持数据库

> jeecgboot平台支持以下数据库，默认我们只提供mysql脚本，其他数据库可以参考[转库文档](https://my.oschina.net/jeecg/blog/4905722)自己转。

|  数据库   |  支持   |
| --- | --- |
|   MySQL   |  √   |
|  Oracle11g   |  √   |
|  Sqlserver2017   |  √   |
|   PostgreSQL   |  √   |
|   MariaDB   |  √   |
|   达梦   |  √   |
|   人大金仓   |  √   |
|   TiDB     |  √   |
|   kingbase8   |  √   |
  

 
## 微服务解决方案


- 1、服务注册和发现 Nacos √
- 2、统一配置中心 Nacos  √
- 3、路由网关 gateway(三种加载方式) √
- 4、分布式 http feign √
- 5、熔断降级限流 Sentinel √
- 6、分布式文件 Minio、阿里OSS √ 
- 7、统一权限控制 JWT + Shiro √
- 8、服务监控 SpringBootAdmin√
- 9、链路跟踪 Skywalking   [参考文档](https://help.jeecg.com/java/springcloud/super/skywarking)
- 10、消息中间件 RabbitMQ  √
- 11、分布式任务 xxl-job  √ 
- 12、分布式事务 Seata
- 13、轻量分布式日志 Loki+grafana套件
- 14、支持 docker-compose、k8s、jenkins
- 15、CAS 单点登录   √
- 16、路由限流   √


#### 微服务方式启动

- [单体快速切换微服务](https://help.jeecg.com/java/springcloud/switchcloud/monomer)
- [Docker一键启动微服务前后端](https://help.jeecg.com/java/docker/quickcloud)





开源版与企业版区别?
-----------------------------------
> [更多商业功能介绍，点击查看](README-Enterprise.md)

 - JeecgBoot开源版采用[Apache-2.0 license](https://github.com/jeecgboot/JeecgBoot/blob/master/LICENSE)开源协议，允许商用使用，不会造成侵权行为，允许基于本平台软件开展业务系统开发（在任何情况下，您不得使用本软件开发可能被认为与本软件竞争的软件）！
 - 商业版与开源版主要区别在于商业版提供了技术支持 和 更多的企业级功能(例如：Online图表、流程监控、流程设计、流程审批、表单设计器、表单视图、积木报表企业版、OA办公、商业APP、零代码应用、Online模块源码等功能).


#### 微服务架构图
![微服务架构图](https://jeecgos.oss-cn-beijing.aliyuncs.com/files/jeecgboot_springcloud2022.png "在这里输入图片标题")




### Jeecg Boot 产品功能蓝图
![功能蓝图](https://jeecgos.oss-cn-beijing.aliyuncs.com/upload/test/Jeecg-Boot-lantu202005_1590912449914.jpg "在这里输入图片标题")





### 功能模块
```
├─系统管理
│  ├─用户管理
│  ├─角色管理
│  ├─菜单管理
│  ├─权限设置（支持按钮权限、数据权限）
│  ├─表单权限（控制字段禁用、隐藏）
│  ├─部门管理
│  ├─我的部门（二级管理员）
│  └─字典管理
│  └─分类字典
│  └─系统公告
│  └─职务管理
│  └─通讯录
│  ├─多数据源管理
│  └─多租户管理（租户管理、租户角色、我的租户）
├─Online在线开发(低代码)
│  ├─Online在线表单
│  ├─Online代码生成器
│  ├─Online在线报表
│  ├─仪表盘设计器
│  ├─系统编码规则
│  ├─系统校验规则
├─AI开发
│  ├─支持AI大模型ChatGPT和DeepSeek
│  ├─AI对话助手
│  ├─AI建表
│  ├─AI写文章
│  ├─AI流程编排
│  ├─AI知识库问答系统
│  ├─AI应用开发平台
│  ├─AI聊天窗口支持嵌入第三方
├─积木报表设计器
│  ├─打印设计器
│  ├─数据报表设计
│  ├─图形报表设计（支持echart）
├─消息中心
│  ├─消息管理
│  ├─模板管理
├─代码生成器(低代码)
│  ├─代码生成器功能（一键生成前后端代码，生成后无需修改直接用，绝对是后端开发福音）
│  ├─代码生成器模板（提供4套模板，分别支持单表和一对多模型，不同风格选择）
│  ├─代码生成器模板（生成代码，自带excel导入导出）
│  ├─查询过滤器（查询逻辑无需编码，系统根据页面配置自动生成）
│  ├─高级查询器（弹窗自动组合查询条件）
│  ├─Excel导入导出工具集成（支持单表，一对多 导入导出）
│  ├─平台移动自适应支持
├─系统监控
│  ├─Gateway路由网关
│  ├─性能扫描监控
│  │  ├─监控 Redis
│  │  ├─Tomcat
│  │  ├─jvm
│  │  ├─服务器信息
│  │  ├─请求追踪
│  │  ├─磁盘监控
│  ├─定时任务
│  ├─系统日志
│  ├─消息中心（支持短信、邮件、微信推送等等）
│  ├─数据日志（记录数据快照，可对比快照，查看数据变更情况）
│  ├─系统通知
│  ├─SQL监控
│  ├─swagger-ui(在线接口文档)
│─报表示例
│  ├─曲线图
│  └─饼状图
│  └─柱状图
│  └─折线图
│  └─面积图
│  └─雷达图
│  └─仪表图
│  └─进度条
│  └─排名列表
│  └─等等
│─大屏模板
│  ├─作战指挥中心大屏
│  └─物流服务中心大屏
│─常用示例
│  ├─自定义组件
│  ├─对象存储(对接阿里云)
│  ├─JVXETable示例（各种复杂ERP布局示例）
│  ├─单表模型例子
│  └─一对多模型例子
│  └─打印例子
│  └─一对多TAB例子
│  └─内嵌table例子
│  └─常用选择组件
│  └─异步树table
│  └─接口模拟测试
│  └─表格合计示例
│  └─异步树列表示例
│  └─一对多JEditable
│  └─JEditable组件示例
│  └─图片拖拽排序
│  └─图片翻页
│  └─图片预览
│  └─PDF预览
│  └─分屏功能
│─封装通用组件	
│  ├─行编辑表格JEditableTable
│  └─省略显示组件
│  └─时间控件
│  └─高级查询
│  └─用户选择组件
│  └─报表组件封装
│  └─字典组件
│  └─下拉多选组件
│  └─选人组件
│  └─选部门组件
│  └─通过部门选人组件
│  └─封装曲线、柱状图、饼状图、折线图等等报表的组件（经过封装，使用简单）
│  └─在线code编辑器
│  └─上传文件组件
│  └─验证码组件
│  └─树列表组件
│  └─表单禁用组件
│  └─等等
│─更多页面模板
│  ├─各种高级表单
│  ├─各种列表效果
│  └─结果页面
│  └─异常页面
│  └─个人页面
├─高级功能
│  ├─提供单点登录CAS集成方案
│  ├─提供APP发布方案
│  ├─集成Websocket消息通知机制
```



### 系统效果




##### PC端
![](https://oscimg.oschina.net/oscnet/up-000530d95df337b43089ac77e562494f454.png)

![输入图片说明](https://static.oschina.net/uploads/img/201904/14155402_AmlV.png "在这里输入图片标题")

![](https://oscimg.oschina.net/oscnet/up-9d6f36f251e71a0b515a01323474b03004c.png)

![输入图片说明](https://static.oschina.net/uploads/img/201904/14160813_KmXS.png "在这里输入图片标题")

![输入图片说明](https://static.oschina.net/uploads/img/201904/14160935_Nibs.png "在这里输入图片标题")

![输入图片说明](https://static.oschina.net/uploads/img/201904/14161004_bxQ4.png "在这里输入图片标题")

#####  系统交互
![](https://oscimg.oschina.net/oscnet/up-78b151fc888d4319377bf1cc311fe826871.png)

![](https://oscimg.oschina.net/oscnet/up-16c07e000278329b69b228ae3189814b8e9.png)


##### AI功能

AI聊天助手

![](https://oscimg.oschina.net/oscnet//65298d5710b4e6039a5f802b5f8505c5.png)

AI建表

![](https://oscimg.oschina.net/oscnet/up-381423599f219a67def45dfd9a99df8ef3f.png)

![](https://oscimg.oschina.net/oscnet/up-1508c2b0708c365605f68893044ee11f20d.png)

AI写文章

![](https://oscimg.oschina.net/oscnet/up-e3ee5b1fe497308805aa5e324b72994af79.png)


#####  仪表盘设计器

![](https://jeecgos.oss-cn-beijing.aliyuncs.com/files/darg20240726105556.png)

![](https://jeecgos.oss-cn-beijing.aliyuncs.com/files/drag20240724135626.png)

![](https://jeecgos.oss-cn-beijing.aliyuncs.com/files/drag20240724135619.png)

![](https://jeecgos.oss-cn-beijing.aliyuncs.com/files/drag20240724135630.png)

![](https://jeecgos.oss-cn-beijing.aliyuncs.com/files/drag20240726105547.png)

![](https://oscimg.oschina.net/oscnet/up-fad98d42b2cf92f92a903c9cff7579f18ec.png)


##### 报表设计器
![](https://oscimg.oschina.net/oscnet/up-64648de000851f15f6c7b9573d107ebb5f8.png)

![](https://oscimg.oschina.net/oscnet/up-fa52b44445db281c51d3f267dce7450d21b.gif)

![](https://oscimg.oschina.net/oscnet/up-68a19149d640f1646c8ed89ed4375e3326c.png)

![](https://oscimg.oschina.net/oscnet/up-f7e9cb2e3740f2d19ff63b40ec2dd554f96.png)


##### 手机端
![](https://oscimg.oschina.net/oscnet/da543c5d0d57baab0cecaa4670c8b68c521.jpg)
![](https://oscimg.oschina.net/oscnet/fda4bd82cab9d682de1c1fbf2060bf14fa6.jpg)

##### PAD端
![](https://oscimg.oschina.net/oscnet/e90fef970a8c33790ab03ffd6c4c7cec225.jpg)
![](https://oscimg.oschina.net/oscnet/d78218803a9e856a0aa82b45efc49849a0c.jpg)
![](https://oscimg.oschina.net/oscnet/59c23b230f52384e588ee16309b44fa20de.jpg)


##### 图表示例
![](https://oscimg.oschina.net/oscnet/up-218bc6a1669496b241ebb23506440c0083e.png)

![输入图片说明](https://static.oschina.net/uploads/img/201904/14160834_Lo23.png "在这里输入图片标题")
![输入图片说明](https://static.oschina.net/uploads/img/201904/14160842_QK7B.png "在这里输入图片标题")
![输入图片说明](https://static.oschina.net/uploads/img/201904/14160849_GBm5.png "在这里输入图片标题")
![输入图片说明](https://static.oschina.net/uploads/img/201904/14160858_6RAM.png "在这里输入图片标题")

##### 在线接口文档
![输入图片说明](https://static.oschina.net/uploads/img/201908/27095258_M2Xq.png "在这里输入图片标题")
![输入图片说明](https://static.oschina.net/uploads/img/201904/14160957_hN3X.png "在这里输入图片标题")


##### UNIAPP效果

![](https://oscimg.oschina.net/oscnet/up-aac943fbd26561879c57a41f7a406edf274.png)

![](https://oscimg.oschina.net/oscnet/up-9a44ba2e82b09c750629d12fafd7f60f553.png)


##### 大屏设计器
![](https://oscimg.oschina.net/oscnet/up-402a6034124474bfef8dfc5b4b2bac1ce5c.png)

![](https://oscimg.oschina.net/oscnet/up-6f7ba2e2ebbeea0d203db8d69fd87644c9f.png)

![](https://oscimg.oschina.net/oscnet/up-ee8d34f318da466b8a6070a6e3111d12ce7.png)

![](https://oscimg.oschina.net/oscnet/up-6b81781b43086819049c4421206810667c5.png)








## 捐赠 

如果觉得还不错，请作者喝杯咖啡吧 ☺

![](https://static.oschina.net/uploads/img/201903/08155608_0EFX.png)