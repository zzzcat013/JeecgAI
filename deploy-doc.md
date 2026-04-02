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
# 同步前端
rsync -avz --delete jeecgboot-vue3/ root@218.26.173.130:/opt/jeecg/jeecgboot-vue3/

# 同步后端
rsync -avz --delete jeecg-boot/ root@218.26.173.130:/opt/jeecg/jeecg-boot/
```

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
rsync -avz --delete jeecgboot-vue3/ root@218.26.173.130:/opt/jeecg/jeecgboot-vue3/
rsync -avz --delete jeecg-boot/ root@218.26.173.130:/opt/jeecg/jeecg-boot/

# 3. 重新构建并启动
ssh root@218.26.173.130 "cd /opt/jeecg && docker compose up -d --build"
```

## 九、备注

1. 本次部署为**单机部署模式**，不使用 Nacos 配置中心
2. 数据库、Redis、pgvector 使用外部已部署的服务
3. 前端端口使用 8866 而非默认的 80 端口，提高安全性
4. 后端使用 `dev` profile，配置文件为 `application-dev.yml`
