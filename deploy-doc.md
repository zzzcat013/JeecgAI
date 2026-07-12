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

### 10.5 不建议的做法

- 不建议把 `magic-pdf` 混装进主业务容器。
- 不建议依赖宿主机安装的软件去给 Docker 容器提供 `soffice`。
- 不建议把 Office 预览和 AIRag 解析都塞到同一个容器镜像里，后续维护成本会比较高。
