# JeecgBoot AI 应用部署文档

## 一、基本信息

| 项目 | 值 |
|------|-----|
| 远程主机 | 218.26.173.130 |
| 登录方式 | 密钥登录 |
| 前端访问地址 | http://218.26.173.130:8866 |
| 后端API地址 | http://218.26.173.130:8080/jeecg-boot |
| Swagger文档 | http://218.26.173.130:8080/jeecg-boot/doc.html |
| 数据库 | MySQL: 218.26.173.130:3366/jeecgai |
| Redis | 218.26.173.130:3360 |
| pgvector | 218.26.173.130:15432 |

## 二、目录结构

远程服务器部署目录：`/opt/jeecg/`

```
/opt/jeecg/
├── docker-compose.yml              # Docker编排文件
├── uploads/                        # 上传文件目录（挂载到容器）
│   ├── doc/                        # 文档文件
│   ├── kb/                         # 知识库文件
│   ├── mineru/                     # MinerU模型缓存
│   └── temp/                       # 临时文件
├── jeecgboot-vue3/                 # 前端项目
│   ├── dist/                       # 前端构建产物
│   └── docker/
│       └── Dockerfile              # 前端Dockerfile
├── jeecg-boot/                     # 后端项目
│   ├── jeecg-module-system/
│   │   └── jeecg-system-start/
│   │       ├── src/main/resources/
│   │       │   └── application-dev.yml  # 开发环境配置
│   │       └── target/
│   │           └── jeecg-system-start-3.9.1.jar  # 后端JAR包
│   └── db/                         # 数据库初始化脚本（未使用）
└── docker-compose-cloud.yml        # 云版本编排文件（未使用）
```

## 三、部署步骤

### 3.1 本地构建

#### 前端构建
```bash
cd /Users/zhangxj/source/java/jeecgAI/JeecgAI/jeecgboot-vue3
pnpm install
pnpm build
```

构建产物：`dist/` 目录

#### 后端构建
```bash
cd /Users/zhangxj/source/java/jeecgAI/JeecgAI/jeecg-boot
mvn clean package -DskipTests
```

构建产物：`jeecg-module-system/jeecg-system-start/target/jeecg-system-start-3.9.1.jar`

### 3.2 同步到远程服务器

```bash
# 只同步前端构建产物和 Dockerfile
rsync -avz --delete jeecgboot-vue3/dist/ root@218.26.173.130:/opt/jeecg/jeecgboot-vue3/dist/
rsync -avz jeecgboot-vue3/Dockerfile root@218.26.173.130:/opt/jeecg/jeecgboot-vue3/Dockerfile

# 只同步后端目标 JAR 和 Dockerfile
rsync -avz jeecg-boot/jeecg-module-system/jeecg-system-start/target/jeecg-system-start-3.9.3.jar root@218.26.173.130:/opt/jeecg/jeecg-boot/jeecg-module-system/jeecg-system-start/target/jeecg-system-start-3.9.3.jar
rsync -avz jeecg-boot/jeecg-module-system/jeecg-system-start/Dockerfile root@218.26.173.130:/opt/jeecg/jeecg-boot/jeecg-module-system/jeecg-system-start/Dockerfile
```

说明：
- 不要直接同步整个 `jeecgboot-vue3/` 目录，里面的 `node_modules/` 会导致同步体积巨大且容易失败。
- 这次部署只需要前端 `dist/`、前端 `Dockerfile`、后端 `jeecg-system-start-3.9.3.jar` 和后端 `Dockerfile`。

### 3.3 启动服务

```bash
ssh root@218.26.173.130 "cd /opt/jeecg && docker compose up -d --build"
```

### 3.4 停止服务

```bash
ssh root@218.26.173.130 "cd /opt/jeecg && docker compose down"
```

### 3.5 查看日志

```bash
# 后端日志
ssh root@218.26.173.130 "docker logs jeecg-boot-system --tail 100 -f"

# 前端日志
ssh root@218.26.173.130 "docker logs jeecgboot-vue3-nginx --tail 100 -f"
```

## 四、配置文件

### 4.1 docker-compose.yml

位置：`/opt/jeecg/docker-compose.yml`

```yaml
version: '2'
services:
  jeecg-boot-system:
    build:
      context: ./jeecg-boot/jeecg-module-system/jeecg-system-start
    restart: on-failure
    container_name: jeecg-boot-system
    image: jeecg-boot-system
    hostname: jeecg-boot-system
    ports:
      - 8080:8080
    environment:
      - SPRING_PROFILES_ACTIVE=dev
    volumes:
      - ./uploads:/jeecg-boot/uploads
    networks:
      - jeecg-boot

  jeecg-vue:
    build:
      context: ./jeecgboot-vue3
    container_name: jeecgboot-vue3-nginx
    image: jeecgboot-vue3
    depends_on:
      - jeecg-boot-system
    networks:
      - jeecg-boot
    ports:
      - 8866:80

networks:
  jeecg-boot:
    name: jeecg_boot
```

**说明：**
- 使用外部数据库、Redis、pgvector（不包含在docker-compose中）
- 后端使用 `dev` profile，读取 `application-dev.yml` 配置
- 前端端口映射为 8866（非默认80端口）
- uploads 目录挂载到容器，用于存储上传文件（约3.4G）

### 4.2 application-dev.yml 关键配置

位置：`/opt/jeecg/jeecg-boot/jeecg-module-system/jeecg-system-start/src/main/resources/application-dev.yml`

关键配置项：

```yaml
spring:
  datasource:
    dynamic:
      datasource:
        master:
          url: jdbc:mysql://218.26.173.130:3366/jeecgai?characterEncoding=UTF-8&useUnicode=true&useSSL=false&tinyInt1isBit=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
          username: root
          password: Qaz_1234
  redis:
    host: 218.26.173.130
    port: 3360
    password: '71b5ae132d028b15871b1f71b2dc5b63032502142e658b47d4365e81aa520c5d'

jeecg:
  ai-rag:
    embed-store:
      host: 218.26.173.130
      port: 15432
      database: postgres
      user: postgres
      password: Qaz_1234
      table: embeddings
```

### 4.3 前端 Nginx 配置

前端 Dockerfile 内置的 Nginx 配置：

```nginx
server {
    listen 80;

    # 后端API代理
    location /jeecgboot/ {
        proxy_pass http://jeecg-boot-system:8080/jeecg-boot/;
        proxy_redirect off;
        proxy_set_header Host jeecg-boot-system;
        proxy_set_header X-Forwarded-Host $http_host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # 前端静态资源
    location / {
        root /var/www/html/;
        index index.html index.htm;
        if (!-e $request_filename) {
            rewrite ^(.*)$ /index.html?s=$1 last;
            break;
        }
    }
}
```

## 五、容器信息

| 容器名 | 镜像 | 端口 | 说明 |
|--------|------|------|------|
| jeecg-boot-system | jeecg-boot-system | 8080:8080 | 后端服务 |
| jeecgboot-vue3-nginx | jeecgboot-vue3 | 8866:80 | 前端Nginx |

## 六、常用命令

```bash
# 查看容器状态
docker ps

# 重启服务
cd /opt/jeecg && docker compose restart

# 重新构建并启动
cd /opt/jeecg && docker compose up -d --build

# 进入后端容器
docker exec -it jeecg-boot-system bash

# 查看容器资源使用
docker stats

# 清理未使用的镜像
docker image prune -f
```

## 七、故障排查

### 7.1 验证码不显示

检查后端API是否正常：
```bash
curl -I http://218.26.173.130:8080/jeecg-boot/sys/randomImage/test
```

应返回 200 状态码。

### 7.2 数据库连接失败

检查数据库配置和连通性：
```bash
# 进入容器测试
docker exec -it jeecg-boot-system bash
# 容器内测试（需安装mysql客户端）
mysql -h 218.26.173.130 -P 3366 -u root -p jeecgai
```

### 7.3 前端无法访问后端

检查 Nginx 代理配置：
```bash
docker exec -it jeecgboot-vue3-nginx cat /etc/nginx/conf.d/default.conf
```

### 7.4 文档预览或 Markdown 图片地址异常

先确认前端代理是否把端口透传给后端：
```nginx
proxy_set_header X-Forwarded-Host $http_host;
proxy_set_header X-Forwarded-Proto $scheme;
```

如果这里用的是 `$host`，会丢掉 `8866` 端口，导致后端生成的文档图片地址不正确。

## 八、更新部署

当有代码更新需要重新部署时：

```bash
# 1. 本地构建
cd /Users/zhangxj/source/java/jeecgAI/JeecgAI
# 前端
cd jeecgboot-vue3 && pnpm build && cd ..
# 后端
cd jeecg-boot && mvn clean package -DskipTests && cd ..

# 2. 同步到服务器
rsync -avz --delete jeecgboot-vue3/dist/ root@218.26.173.130:/opt/jeecg/jeecgboot-vue3/dist/
rsync -avz jeecgboot-vue3/Dockerfile root@218.26.173.130:/opt/jeecg/jeecgboot-vue3/Dockerfile
rsync -avz jeecg-boot/jeecg-module-system/jeecg-system-start/target/jeecg-system-start-3.9.3.jar root@218.26.173.130:/opt/jeecg/jeecg-boot/jeecg-module-system/jeecg-system-start/target/jeecg-system-start-3.9.3.jar
rsync -avz jeecg-boot/jeecg-module-system/jeecg-system-start/Dockerfile root@218.26.173.130:/opt/jeecg/jeecg-boot/jeecg-module-system/jeecg-system-start/Dockerfile

# 3. 同步 uploads 目录（如有新增文件）
rsync -avz jeecg-boot/uploads/ root@218.26.173.130:/opt/jeecg/uploads/

# 4. 重新构建并启动
ssh root@218.26.173.130 "cd /opt/jeecg && docker compose up -d --build"
```

或使用一键部署脚本：
```bash
cd /Users/zhangxj/source/java/jeecgAI/JeecgAI
./deploy.sh
```

注意：
- 这份脚本当前会按仓库目录同步，前端目录里如果带着 `node_modules/`，同步体积会非常大。
- 真正部署时，优先使用上面的“产物级同步”命令。

## 九、备注

1. 本次部署为**单机部署模式**，不使用 Nacos 配置中心
2. 数据库、Redis、pgvector 使用外部已部署的服务
3. 前端端口使用 8866 而非默认的 80 端口，提高安全性
4. 后端使用 `dev` profile，配置文件为 `application-dev.yml`
5. 文档相关的图片地址由后端根据前端请求头生成，部署时必须保留 `X-Forwarded-Host` 和 `X-Forwarded-Proto`

## 十、AI5G 文档能力的 Docker 部署建议

### 10.1 容器职责划分

- `jeecg-boot-system`：后端业务容器，负责文档上传、预览、AI 转 MD、知识库导入等业务逻辑。
- `mineru`：独立的 MinerU 服务容器或外部服务，负责文档结构化解析。
- `jeecg-vue`：前端静态资源与反向代理。

### 10.2 必要依赖

- `jeecg-boot-system` 容器内需要安装 `LibreOffice`，因为文档管理页的 Office 预览会在后端本地执行 `soffice` 转 PDF。
- `magic-pdf` 不建议安装在主业务容器里，应该放在独立 MinerU 服务侧。
- 如果继续使用当前配置里的 `mineru-mode: api`, 主业务容器只需要能访问 `mineru-url`，不需要本地安装 `magic-pdf`。

### 10.3 推荐部署方式

1. 保持 MySQL、Redis、pgvector 与现有编排一致。
2. 修改后端镜像，在 `jeecg-boot-system` 中安装 `LibreOffice` 和字体包。
3. 维持 `jeecg.airag.know.mineru-url` 指向独立 MinerU 服务。
4. 前端继续走 Nginx 静态部署，不引入文档转换依赖。

### 10.4 结果边界

- 文档管理“预览”可在后端容器内直接转 PDF。
- 文档管理“导入知识库”继续使用当前已生成的 Markdown。
- AIRag 的文档解析继续由远程 MinerU API 处理，不受主业务容器是否安装 `magic-pdf` 影响。
- AI5G 转换结果只上传主 Markdown 和 Markdown 引用的图片，MinerU 生成的 PDF/JSON 中间文件不上传 MinIO。
- 当前环境 `spring.flyway.enabled=false`，数据库迁移采用手工执行。2026-08-27 已依次应用 `V20260820_1` 至 `V20260820_8`、`V20260826_1`、`V3.9.5_0`，并已补齐 `flyway_schema_history`。
- 如果部署到全新环境，仍需要按上述顺序应用数据库脚本；已有环境不要重复执行非幂等的 `V3.9.5_0__all_upgrade.sql`。
- AI5G 智能体结构化查询依赖 `jeecg-boot/db/ai5g/ai5g_domain_query_plugin.sql`，ToB/ToC 业务表脚本位于 `org/jeecg/modules/biz/ai5g/sql/`，首次部署时需一并应用。

### 10.5 5G专网运维智能体

- 正式应用：`2083017548267618305`
- 正式流程：`2082795096418247001`
- 分流规则：非5G专网问题直接提示不在受理范围；ToB/ToC 现场问题先查对应数据库，再结合知识库回答；非现场知识问题直接使用对应知识库回答。
- 结构化查询：`AI5G专网查询插件`，按 `biz_ai5g_query_scope` 限制为 ToB/ToC 只读查询，禁止模型编造项目名、固定IP、ICCID、MSISDN、DNN、ServiceID等数据。
- 严格回答：数据库未命中时回复“未找到相关记录”，知识库未命中时回复“知识库中暂未找到相关记录”，并要求补充项目名、固定IP、ICCID、MSISDN等关键信息。
- 知识库检索：当前为 pgvector 向量检索，未启用 rerank；流程非现场知识节点 `topNumber=10`、`similarity=0.7`，平台聚合上下文上限 4000 字符。
- 后续优化：可评估 pgvector 混合检索（`SearchMode.HYBRID` + RRF）和 DashScope `gte-rerank-v2` 精排，先在 AI5G 流程内做效果对比，再决定是否下沉到通用知识库链路。

### 10.6 不建议的做法

- 不建议把 `magic-pdf` 混装进主业务容器。
- 不建议依赖宿主机安装的软件去给 Docker 容器提供 `soffice`。
- 不建议把 Office 预览和 AIRag 解析都塞到同一个容器镜像里，后续维护成本会比较高。

## 十一、数据库变更记录（2026-08-27）

### 11.1 已执行的迁移

| 脚本 | 内容 | 状态 |
|------|------|------|
| `V20260820_1__ai5g_docfile_mineru_async.sql` | `biz_ai5g_docfile` 增加 MinerU 异步转换字段和任务索引 | 已执行 |
| `V20260820_2__ai5g_docfile_manifest_remark.sql` | `asset_manifest` 调整为 `LONGTEXT`，`remark` 调整为 `TEXT` | 已执行 |
| `V20260820_3__ai5g_doc_overview_menu.sql` | 新增“文档管理概览”菜单及 AI5G 角色授权 | 已执行 |
| `V20260820_4__ai5g_docfile_fix_size.sql` | 修正 `biz_ai5g_docfile.size` 为原始上传文件大小 | 已执行 |
| `V20260820_5__ai5g_docfile_tombstone.sql` | 新增文档删除后的 MinIO 清理墓碑表 | 已执行 |
| `V20260820_6__ai5g_home_menu.sql` | 新增 AI5G 角色独立首页及角色首页配置 | 已执行 |
| `V20260820_7__ai5g_ops_agent_flow.sql` | 新增 5G 专网运维智能体正式流程并切换正式应用 | 已执行 |
| `V20260820_8__ai5g_ops_agent_db_source.sql` | 为 ToB/ToC 数据库查询和最终回答补充数据库来源标注 | 已执行 |
| `V20260826_1__fix_suixing_retrieval_app_flow.sql` | 随行专网数据检索应用切换到 AI5G 新流程 | 已执行 |
| `V3.9.5_0__all_upgrade.sql` | main 合并带来的系统升级 SQL | 已执行 |

### 11.2 V3.9.5 系统升级主要内容

- 删除 `airag_prompts.prompt_key`。
- `onl_cgform_head`、`onl_cgform_enhance_sql` 增加动态数据源字段。
- `sys_depart` 增加飞书部门标识字段。
- `open_api` 将 `comment` 改为 `remarks`、`body` 改为 `request_body`，并增加接口描述字段。
- `open_api_log` 增加调用 AK、请求方法、请求路径、来源 IP、请求参数、响应码、错误信息、请求头等日志字段。
- `airag_app` 增加 `share_token` 分享令牌及唯一索引。
- `jimu_dict` 重建字典检索索引。
- 补齐 AI 模型、知识库、应用等接口按钮权限。
- 删除旧 Chat2BI 应用、流程、插件和旧报表/大屏 AI 流程。

### 11.3 Flyway 记录同步

- 2026-08-27 手工执行升级后，已补齐 `V20260820_1~8`、`V20260826_1`、`V3.9.5_0` 的 `flyway_schema_history` 记录。
- 使用 Flyway `7.15.0` 执行 `repair`，回填所有迁移的 `checksum` 和描述。
- 执行 `validate` 通过：共校验 29 条迁移，失败 0 条，迁移脚本 checksum 为空 0 条。
- 当前 `spring.flyway.enabled` 仍为 `false`，后续如需启用 Flyway，可直接 validate，不会重复执行已手工应用的脚本。

### 11.4 升级后 AI5G 核对结果

- `5G专网运维智能体`：`2083017548267618305`，仍绑定正式流程 `2082795096418247001`。
- `随行专网项目数据检索智能体`：`2077722659066368002`，仍绑定正式流程 `2082795096418247001`。
- `AI5G专网查询插件`：`2078729600000000001`，升级后保留正常。
- 旧流程 `2077719401256538114 随行专网数据检索` 已禁用，避免引用已删除的通用数据库插件。

### 11.5 备份文件

- `/Users/zhangxj/backups/jeecgai-before-v3.9.5-20260827.sql`
- `/Users/zhangxj/backups/jeecgai-before-flyway-sync-20260827.sql`
