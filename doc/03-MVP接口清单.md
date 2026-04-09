# MVP 接口清单

本文档面向第一阶段最小可用版本，目标是先把“统一拉起、统一查看、统一存档”跑通。

## 1. 统一约定

### API 前缀

- REST：`/api/v1`
- WebSocket：`/ws`

### 响应体建议

```json
{
  "code": 0,
  "message": "OK",
  "data": {}
}
```

### 分页响应建议

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "items": [],
    "pageNo": 1,
    "pageSize": 20,
    "total": 0
  }
}
```

## 2. 应用实例管理

### `GET /api/v1/instances`

用途：

- 查询应用实例列表

查询参数建议：

- `appType`
- `enabled`
- `keyword`

### `GET /api/v1/instances/{id}`

用途：

- 查看单个实例详情

### `POST /api/v1/instances`

用途：

- 新建应用实例

请求体建议：

```json
{
  "name": "Codex-Wsl-Default",
  "appType": "codex",
  "adapterType": "codex-cli",
  "runtimeEnv": "wsl",
  "launchMode": "external",
  "executablePath": "wsl.exe",
  "launchCommand": "codex",
  "args": ["--cd", "/mnt/d/Project/ali/260409"],
  "workdir": "D:\\Project\\ali\\260409",
  "env": {
    "TERM": "xterm-256color"
  },
  "enabled": true,
  "autoRestart": false
}
```

### `PUT /api/v1/instances/{id}`

用途：

- 修改实例配置

### `POST /api/v1/instances/{id}/enable`

用途：

- 启用实例

### `POST /api/v1/instances/{id}/disable`

用途：

- 禁用实例

### `POST /api/v1/instances/{id}/test-launch`

用途：

- 测试启动命令是否合法
- 只做轻量校验，不真正创建业务会话

## 3. 会话管理

### `GET /api/v1/sessions`

用途：

- 查询会话列表

查询参数建议：

- `appInstanceId`
- `status`
- `keyword`
- `pageNo`
- `pageSize`

### `GET /api/v1/sessions/{id}`

用途：

- 获取会话详情

### `POST /api/v1/sessions`

用途：

- 创建并启动会话

请求体建议：

```json
{
  "appInstanceId": "ins_001",
  "title": "Codex 修复前端接口",
  "projectPath": "D:\\Project\\ali\\260409",
  "interactionMode": "raw",
  "initInput": "请先分析当前目录结构",
  "tags": ["codex", "project-init"]
}
```

### `POST /api/v1/sessions/{id}/input`

用途：

- 向运行中会话发送输入

请求体建议：

```json
{
  "content": "继续",
  "appendNewLine": true
}
```

### `POST /api/v1/sessions/{id}/stop`

用途：

- 停止会话

请求体建议：

```json
{
  "stopMode": "graceful"
}
```

### `POST /api/v1/sessions/{id}/restart`

用途：

- 使用原配置重启会话

### `GET /api/v1/sessions/running`

用途：

- 查询当前运行中的会话

## 4. 消息与历史记录

### `GET /api/v1/sessions/{id}/messages`

用途：

- 分页读取会话消息

查询参数建议：

- `pageNo`
- `pageSize`
- `direction`

### `GET /api/v1/sessions/{id}/raw-log`

用途：

- 获取原始终端日志文件信息
- 后续可配合下载接口

### `GET /api/v1/history/search`

用途：

- 搜索历史会话与消息

查询参数建议：

- `keyword`
- `appType`
- `projectPath`
- `dateFrom`
- `dateTo`

### `GET /api/v1/history/sessions/{id}/timeline`

用途：

- 获取某会话的时间线

## 5. 配置管理

### `GET /api/v1/configs`

用途：

- 读取系统配置

### `PUT /api/v1/configs`

用途：

- 批量更新配置

请求体建议：

```json
{
  "items": [
    {
      "configGroup": "runtime",
      "configKey": "defaultProjectPath",
      "valueType": "string",
      "valueText": "D:\\Project\\ali\\260409"
    },
    {
      "configGroup": "storage",
      "configKey": "sessionLogRetentionDays",
      "valueType": "number",
      "valueText": "30"
    }
  ]
}
```

## 6. 运行态与诊断

### `GET /api/v1/runtime/health`

用途：

- 查看本地服务状态、数据库状态、WebSocket 状态

### `GET /api/v1/runtime/processes`

用途：

- 查看当前受控进程列表

### `GET /api/v1/runtime/statistics`

用途：

- 获取控制台首页统计数据

## 7. WebSocket 设计

### 建议连接地址

- `/ws/sessions`

### 订阅方式

MVP 可以直接建立单一 WebSocket，后端向所有前端推送运行事件。  
事件中带 `sessionId`，前端自行过滤。

### 事件类型

- `session.created`
- `session.status.changed`
- `session.output.raw`
- `session.message.created`
- `session.closed`
- `runtime.process.updated`

### 原始输出事件示例

```json
{
  "event": "session.output.raw",
  "sessionId": "ses_001",
  "timestamp": "2026-04-09T15:00:00",
  "payload": {
    "stream": "stdout",
    "chunk": "Analyzing repository...\r\n"
  }
}
```

### 结构化消息事件示例

```json
{
  "event": "session.message.created",
  "sessionId": "ses_001",
  "timestamp": "2026-04-09T15:00:10",
  "payload": {
    "messageId": "msg_1001",
    "role": "assistant",
    "messageType": "text",
    "contentText": "我先检查目录结构。",
    "isStructured": true
  }
}
```

## 8. 第一阶段必须先做的接口

如果你想尽快出可演示版本，优先级建议如下：

### P0

- `POST /api/v1/instances`
- `GET /api/v1/instances`
- `POST /api/v1/sessions`
- `GET /api/v1/sessions`
- `POST /api/v1/sessions/{id}/stop`
- `GET /api/v1/sessions/{id}/messages`
- `/ws/sessions`

### P1

- `POST /api/v1/sessions/{id}/input`
- `GET /api/v1/history/search`
- `GET /api/v1/runtime/statistics`
- `GET /api/v1/configs`
- `PUT /api/v1/configs`

### P2

- `POST /api/v1/instances/{id}/test-launch`
- `POST /api/v1/sessions/{id}/restart`
- `GET /api/v1/runtime/processes`

## 9. 明确不建议第一阶段做的接口

- 多用户接口
- 登录鉴权接口
- 复杂权限体系接口
- 云同步接口
- 插件市场接口

这些都会让 MVP 偏离你的核心目标。
