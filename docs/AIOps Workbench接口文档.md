# AIOps Workbench 当前页面接口文档

更新时间：2026-04-02

本文档不是按 Swagger 理想定义写的，而是直接对照以下代码整理出的“当前页面真实联调接口”：

- 前端：`aiops-workbench-front`
- 后端：`/Users/tianlanxu/Documents/staryea/code/aiops-workbench`
- 当前页面路由：`/`
- 页面入口：`src/views/Home.vue`

## 1. 文档范围

当前页面实际由 3 块能力组成：

1. 左侧 AI 助手区
2. 右侧个人工作项区
3. 远程浏览器区

对应前端调用文件：

- `src/api/chat.js`
- `src/api/workbench.js`
- `src/api/browser.js`

对应前端组件：

- `src/views/Home.vue`
- `src/components/MainContent.vue`
- `src/components/BrowserLiveView.vue`
- `src/components/LeftSidebar.vue`

## 2. 请求链路总览

开发环境下，前端统一通过 `/api` 发请求，Vite 代理到 Java 后端：

- 前端开发端口：`3000`
- Java 后端端口：`8089`
- Vite 代理目标：`http://192.168.3.166:8089`

Java 后端内部又分两类转发：

1. 聊天接口转发到 CoPaw
   - 默认地址：`http://192.168.3.166:8088`
2. 远程浏览器接口转发到 Browser Session Hub
   - 默认地址：`http://192.168.3.166:8091`

## 3. 当前页面接口清单

| 页面区域 | 前端调用 | 方法 | 接口 | 返回风格 |
| --- | --- | --- | --- | --- |
| AI 助手 | `createChat` | `POST` | `/api/chat/chats` | CoPaw 原始 JSON，后端直接透传 |
| AI 助手 | `listChats` | `GET` | `/api/chat/chats` | CoPaw 原始 JSON 数组，后端直接透传 |
| AI 助手 | `getChat` | `GET` | `/api/chat/chats/{chatId}` | CoPaw 原始 JSON，后端直接透传 |
| AI 助手 | `deleteChat` | `DELETE` | `/api/chat/chats/{chatId}` | CoPaw 原始 JSON，后端直接透传 |
| AI 助手 | `stopChat` | `POST` | `/api/chat/stop` | `Result<Boolean>` |
| AI 助手 | `streamChat` | `POST` | `/api/chat/stream` | `text/event-stream` |
| 工作项 | `listTodoTasks` | `GET` | `/api/workbench/todo-tasks` | `Result<List<TodoTask>>` |
| 工作项 | `listFocusEvents` | `GET` | `/api/workbench/focus-events` | `Result<List<FocusEvent>>` |
| 浏览器状态 | `getBrowserStatus` | `GET` | `/api/browser/status` | `BrowserSessionView` |
| 浏览器会话 | `getBrowserPreview` | `GET` | `/api/browser/preview` | `BrowserSessionView` |
| 浏览器保活 | `touchBrowserSession` | `POST` | `/api/browser/touch` | `BrowserSessionView` |
| 浏览器导航 | `navigateBrowser` | `POST` | `/api/browser/navigate` | `BrowserSessionView` |

## 4. 返回结构差异说明

这部分很关键，当前页面不是所有接口都一种返回格式。

### 4.1 Chat 相关接口

`/api/chat/chats`、`/api/chat/chats/{chatId}`、`/api/chat/agents` 这类接口在 Java 后端里返回值类型是 `String`，本质上是把 CoPaw 的原始响应直接透传给前端。

特点：

- 不包 `Result`
- 返回内容本质上是 JSON 字符串
- 前端部分位置做了 `JSON.parse` 兼容，部分位置依赖 axios 自动解析

### 4.2 Workbench 相关接口

`/api/workbench/*` 统一返回：

```json
{
  "code": 200,
  "message": "success",
  "data": []
}
```

### 4.3 Browser 相关接口

`/api/browser/*` 返回的是后端自定义对象 `BrowserSessionView`，不是 `Result` 包装。

典型结构：

```json
{
  "agentId": "default",
  "running": true,
  "status": "running",
  "sessionId": "session_xxx",
  "ownerId": "agent:default",
  "previewUrl": "http://host:port/vnc.html?path=websockify&autoconnect=1",
  "rfbUrl": "ws://host:port/websockify",
  "cdpHttpEndpoint": "http://host:port"
}
```

## 5. 详细接口说明

### 5.1 新建会话

### 接口

`POST /api/chat/chats`

### 前端调用场景

- 首次发送消息前，如果 `currentSessionId` 为空，则先调用该接口创建会话
- 调用位置：`Home.vue` 中 `ensureCurrentChat()`

### 请求参数

请求体：无

Query 参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `userId` | string | 否 | `default` | 当前页面固定传 `default` |
| `name` | string | 否 | `New Chat` | 会话名称 |
| `agentId` | string | 否 | `default` | 代理/智能体 ID |

### 后端实际处理

Java 后端不会自己生成复杂会话信息，而是组装一份 CoPaw 建会话请求：

```json
{
  "session_id": "console:default",
  "user_id": "default",
  "channel": "console",
  "name": "New Chat"
}
```

说明：

- `session_id = defaultChannel + ":" + userId`
- 当前配置里 `defaultChannel` 默认是 `console`
- 如果前端不传 `name`，后端会补成 `New Chat`

### 响应

后端直接透传 CoPaw 返回值，前端期望至少包含：

```json
{
  "id": "chat_xxx",
  "name": "New Chat",
  "session_id": "console:default",
  "user_id": "default",
  "channel": "console",
  "status": "idle"
}
```

前端实际依赖字段：

- `id`
- `session_id`

### 5.2 会话列表

### 接口

`GET /api/chat/chats`

### 前端调用场景

- 左侧历史会话面板展开时调用
- 调用位置：`Home.vue` 中 `handleLoadHistory()`

### Query 参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `userId` | string | 否 | `default` | 当前页面固定传 `default` |
| `channel` | string | 否 | 无 | 前端当前未传 |
| `agentId` | string | 否 | `default` | 智能体 ID |

### 响应

后端直接透传 CoPaw 返回的数组，示例：

```json
[
  {
    "id": "chat_001",
    "name": "New Chat",
    "session_id": "console:default",
    "user_id": "default",
    "channel": "console",
    "created_at": "2026-04-02T14:10:00Z"
  }
]
```

前端实际依赖字段：

- `id`
- `name`
- `session_id`
- `created_at`

前端处理说明：

- 左侧边栏会再次按 `created_at` 做倒序排序
- 如果 `created_at` 解析失败，会被当成时间戳 `0`

### 5.3 获取单个会话详情

### 接口

`GET /api/chat/chats/{chatId}`

### 前端调用场景

- 点击历史会话后加载消息记录
- 调用位置：`Home.vue` 中 `handleSwitchChat()`

### 路径参数

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `chatId` | string | 是 | 会话 ID |

### Query 参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `agentId` | string | 否 | `default` | 智能体 ID |

### 响应

后端直接透传 CoPaw 会话详情，前端重点读取：

说明：

- 这里的消息体示例是根据当前前端解析逻辑和 CoPaw 透传方式整理出的“联调用示例”
- Java 本地代码只确认了透传，不对 CoPaw 的完整字段结构做二次约束

```json
{
  "id": "chat_001",
  "messages": [
    {
      "id": "msg_user_1",
      "role": "user",
      "type": "message",
      "content": [
        { "type": "text", "text": "帮我看看今天的待办" }
      ]
    },
    {
      "id": "msg_assistant_1",
      "role": "assistant",
      "type": "reasoning",
      "content": [
        { "type": "text", "text": "我先整理一下工作项..." }
      ]
    },
    {
      "id": "msg_assistant_2",
      "role": "assistant",
      "type": "function_call",
      "content": [
        {
          "type": "data",
          "data": {
            "name": "browser_use",
            "arguments": {
              "url": "https://example.com"
            }
          }
        }
      ]
    }
  ],
  "status": "idle"
}
```

### 前端消息映射规则

前端按以下规则把 CoPaw 消息映射成 UI 段落：

| CoPaw `type` | 前端展示类型 |
| --- | --- |
| `reasoning` | thinking |
| `plugin_call` / `mcp_call` / `function_call` / `component_call` | tool_call |
| `message` | text |

以下类型会被前端忽略：

- `heartbeat`
- `plugin_call_output`
- `mcp_call_output`
- `function_call_output`
- `component_call_output`
- `mcp_list_tools`
- `mcp_approval_request`
- `mcp_approval_response`

### 5.4 删除会话

### 接口

`DELETE /api/chat/chats/{chatId}`

### 前端调用场景

- 左侧历史会话删除按钮

### 参数

路径参数：

- `chatId`: 会话 ID

Query 参数：

- `agentId`: 智能体 ID，默认 `default`

### 响应

后端直接透传 CoPaw 删除结果，前端当前不依赖具体响应体，只要请求成功就直接把本地 `chatList` 中对应项移除。

### 5.5 停止对话

### 接口

`POST /api/chat/stop`

### 前端调用场景

- 左侧输入框在流式回复中点击停止

### Query 参数

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `chatId` | string | 是 | 当前聊天 ID |
| `agentId` | string | 否 | 默认 `default` |

### 响应

```json
{
  "code": 200,
  "message": "success",
  "data": true
}
```

说明：

- `data=true` 表示 CoPaw 返回 `stopped=true`
- 之后前端还会主动 `AbortController.abort()` 断开当前 SSE

### 5.6 流式聊天

### 接口

`POST /api/chat/stream`

### Content-Type

请求：`application/json`

响应：`text/event-stream`

### 前端调用场景

- 用户发送消息后发起流式请求
- 调用位置：`Home.vue` 中 `handleSend()`

### 请求体

```json
{
  "message": "帮我分析今天的重点风险",
  "sessionId": "console:default",
  "userId": "default",
  "agentId": "default",
  "stream": true,
  "reconnect": false
}
```

### 后端实际转发逻辑

Java 后端会把上面的请求重组后再发给 CoPaw：

```json
{
  "input": [
    {
      "role": "user",
      "type": "message",
      "content": [
        {
          "type": "text",
          "text": "帮我分析今天的重点风险"
        }
      ]
    }
  ],
  "session_id": "console:default",
  "user_id": "default",
  "channel": "console",
  "stream": true,
  "reconnect": false
}
```

### SSE 事件说明

Java 后端会逐行读取 CoPaw 的 SSE，再把 `data:` 内容转发给前端。当前前端按下面几类事件解析：

说明：

- 下面的事件示例是按当前前端 `createEventProcessor()` 的解析逻辑整理
- Java 后端本身只负责转发 SSE，不重新定义 CoPaw 事件协议

#### 1. 元信息事件

```text
data: {"object":"response", ...}
```

前端忽略。

#### 2. 消息块事件

```text
data: {"object":"message","id":"msg_1","type":"reasoning","status":"in_progress","content":[{"type":"text","text":"正在分析..."}]}
```

或者：

```text
data: {"object":"message","id":"msg_2","type":"function_call","status":"in_progress","content":[{"type":"data","data":{"name":"browser_use","arguments":{"url":"https://example.com"}}}]}
```

#### 3. 内容增量事件

```text
data: {"object":"content","msg_id":"msg_1","delta":true,"text":"继续输出内容"}
```

说明：

- `message` 事件用于创建一个新的展示段
- `content` 事件用于往已存在段落里追加内容
- 如果工具名是 `browser_use` 或 `browser_visible`，前端会自动切换到“远程浏览器”标签页

### 当前前端对 SSE 的真实依赖

前端依赖这些字段：

- `object`
- `id`
- `msg_id`
- `type`
- `status`
- `content`
- `data.name`
- `data.arguments`
- `text`
- `delta`

### 5.7 待办任务列表

### 接口

`GET /api/workbench/todo-tasks`

### 前端调用场景

- 右侧“个人工作项”页面加载时并行获取
- 调用位置：`MainContent.vue` 中 `fetchData()`

### Query 参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `userId` | string | 否 | `default` | 当前页面未改写，固定走默认值 |
| `systemCode` | string | 否 | 无 | 当前页面未传，可作为系统过滤条件 |

### 响应

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "userId": "default",
      "systemCode": "CRM",
      "taskName": "处理高优告警",
      "taskLevel": "P1",
      "taskDetail": "检查核心告警并完成升级",
      "taskLink": "https://example.com/alarm/1",
      "taskSuggestion": "告警,升级,复核",
      "estimatedTime": "15min",
      "createdAt": "2026-04-02 09:00:00",
      "updatedAt": "2026-04-02 09:10:00"
    }
  ]
}
```

### 数据库排序规则

SQL 排序不是简单时间倒序，而是：

1. `P1`
2. `P2`
3. `P3`
4. 其他
5. 同优先级下 `created_at DESC`

### 前端实际依赖字段

- `taskName`
- `estimatedTime`
- `taskLevel`
- `taskDetail`
- `taskSuggestion`
- `taskLink`

前端映射规则：

- `taskSuggestion` 会按英文逗号 `,` 分割成标签
- `taskLevel` 会被映射成 `P1 高优先级 / P2 建议跟进 / P3 推荐复核`
- 点击卡片时，如果存在 `taskLink`，会跳转到远程浏览器并打开链接

### 5.8 重点关注事项列表

### 接口

`GET /api/workbench/focus-events`

### 前端调用场景

- 右侧“重点关注事项”区页面加载时并行获取

### Query 参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `userId` | string | 否 | `default` | 当前页面固定默认值 |
| `systemCode` | string | 否 | 无 | 当前页面未传 |

### 响应

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "userId": "default",
      "systemCode": "CRM",
      "eventType": "pending_upgrade",
      "eventCount": 12,
      "ratio": "+18%",
      "eventLink": "https://example.com/event/1",
      "createdAt": "2026-04-02 09:00:00",
      "updatedAt": "2026-04-02 09:10:00"
    }
  ]
}
```

### 数据库排序规则

后端 SQL 固定按事件类型排序：

1. `pending_upgrade`
2. `completed`
3. `not_started`
4. `to_be_determined`
5. 其他

### 前端实际依赖字段

- `eventType`
- `eventCount`
- `ratio`
- `eventLink`

前端映射规则：

- `eventCount` 会拼接成 `xx个`
- `completed` 会显示为绿色趋势，其余类型走普通高亮
- 点击卡片时，如果存在 `eventLink`，会切换到远程浏览器并打开链接

### 5.9 浏览器状态查询

### 接口

`GET /api/browser/status`

### 前端调用场景

- 右上“远程浏览器”标签的小绿点状态轮询
- 每 2 秒请求一次

### Query 参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `agent_id` | string | 否 | `default` | 浏览器会话归属的 agent |

### 响应

```json
{
  "agentId": "default",
  "running": false,
  "status": "stopped",
  "sessionId": null,
  "ownerId": "agent:default",
  "previewUrl": null,
  "rfbUrl": null,
  "cdpHttpEndpoint": null
}
```

### 后端实际逻辑

- 先按 `agent_id` 查缓存 session
- 查不到则到 Browser Session Hub 拉所有 session，再按 `owner_id = agent:{agentId}` 匹配
- 仅当状态是 `running` 或 `starting` 时，`running=true`
- 找不到会话时直接返回 stopped 视图，不报错

### 5.10 获取或创建浏览器会话

### 接口

`GET /api/browser/preview`

### 前端调用场景

- 打开“远程浏览器”标签页时自动调用
- `BrowserLiveView` 挂载时调用

### Query 参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `agent_id` | string | 否 | `default` | agent 维度的浏览器隔离键 |
| `start_url` | string | 否 | 无 | 当前页面默认不传；需要时可指定首次打开页面 |

### 响应

```json
{
  "agentId": "default",
  "running": true,
  "status": "running",
  "sessionId": "sess_123",
  "ownerId": "agent:default",
  "previewUrl": "http://192.168.3.166:8091/vnc.html?path=websockify&autoconnect=1",
  "rfbUrl": "ws://192.168.3.166:8091/websockify",
  "cdpHttpEndpoint": "http://192.168.3.166:9222"
}
```

### 后端实际逻辑

1. 先查当前 agent 是否已有可用 session
2. 如果有，返回该 session 视图
3. 如果没有，则向 Browser Session Hub 发起建会话请求

建会话时后端会传：

```json
{
  "owner_id": "agent:default",
  "start_url": null,
  "persist_profile": true,
  "kiosk": true,
  "metadata": {
    "agent_id": "default"
  }
}
```

说明：

- `owner_id` 是浏览器会话归属键
- `persist_profile` 和 `kiosk` 由后端配置控制
- `rfbUrl` 是后端根据 `previewUrl` 推导出来的 noVNC 连接地址

### 5.11 浏览器保活

### 接口

`POST /api/browser/touch`

### 前端调用场景

- 浏览器页面已连接后，每 20 秒保活一次

### Query 参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `agent_id` | string | 否 | `default` | 当前 agent |

### 响应

返回值仍然是 `BrowserSessionView`。

### 后端实际逻辑

- 如果 session 存在，则调用 Browser Session Hub 的 `POST /api/sessions/{sessionId}/touch`
- 如果不存在，则返回 stopped 视图
- 前端如果发现 `sessionId` 变化，会主动重连 noVNC

### 5.12 浏览器打开链接

### 接口

`POST /api/browser/navigate`

### 前端调用场景

- 点击待办卡片/关注事项卡片时
- `BrowserLiveView` 内部主动跳转地址时

### Query 参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `agent_id` | string | 否 | `default` | 浏览器会话所属 agent |

### 请求体

```json
{
  "url": "https://example.com"
}
```

### 后端实际逻辑

1. 若 `url` 为空，后端会抛异常
2. 若 `url` 没有协议头，后端自动补 `https://`
3. 如果当前 agent 没有会话，则直接以该 URL 创建一个新浏览器 session
4. 如果已有会话，则走对应 session 的 `cdp_http_endpoint/json/new?{url}` 打开页面
5. 成功后返回最新的 `BrowserSessionView`

### 响应

```json
{
  "agentId": "default",
  "running": true,
  "status": "running",
  "sessionId": "sess_123",
  "ownerId": "agent:default",
  "previewUrl": "http://192.168.3.166:8091/vnc.html?path=websockify&autoconnect=1",
  "rfbUrl": "ws://192.168.3.166:8091/websockify",
  "cdpHttpEndpoint": "http://192.168.3.166:9222"
}
```

## 6. 当前页面依赖的数据模型

### 6.1 ChatRequest

```json
{
  "message": "string",
  "sessionId": "string",
  "userId": "string",
  "stream": true,
  "reconnect": false,
  "agentId": "string"
}
```

### 6.2 Result<T>

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

### 6.3 TodoTask

```json
{
  "id": 1,
  "userId": "default",
  "systemCode": "CRM",
  "taskName": "string",
  "taskLevel": "P1",
  "taskDetail": "string",
  "taskLink": "string",
  "taskSuggestion": "标签1,标签2",
  "estimatedTime": "15min",
  "createdAt": "2026-04-02 09:00:00",
  "updatedAt": "2026-04-02 09:10:00"
}
```

### 6.4 FocusEvent

```json
{
  "id": 1,
  "userId": "default",
  "systemCode": "CRM",
  "eventType": "pending_upgrade",
  "eventCount": 12,
  "ratio": "+18%",
  "eventLink": "string",
  "createdAt": "2026-04-02 09:00:00",
  "updatedAt": "2026-04-02 09:10:00"
}
```

### 6.5 BrowserSessionView

```json
{
  "agentId": "default",
  "running": true,
  "status": "running",
  "sessionId": "sess_123",
  "ownerId": "agent:default",
  "previewUrl": "string",
  "rfbUrl": "string",
  "cdpHttpEndpoint": "string"
}
```

## 7. 联调注意事项

1. Chat 接口和 Workbench 接口返回格式不一样，不能按同一套解析。
2. `chat/chats` 系列接口是 CoPaw 透传，字段名基本是下游风格，例如 `session_id`、`created_at`。
3. `workbench/*` 是 Java 本地接口，字段名会被 Jackson 输出成驼峰，例如 `createdAt`、`taskName`。
4. 当前页面里 `userId` 和 `agentId` 基本都写死为 `default`。
5. 浏览器能力当前前端走的是 `previewUrl/rfbUrl + noVNC` 方案，并没有直接使用 Java 后端的 `/api/browser/ws`。
6. 后端没有看到统一异常处理器，异常时大概率会直接走 Spring Boot 默认错误响应，而不是统一 `Result.error(...)`。

## 8. 后端已有但当前页面未直接使用的接口

### 8.1 获取智能体列表

`GET /api/chat/agents`

说明：

- 后端已实现
- 当前页面没有实际调用
- 返回值同样是 CoPaw 原始 JSON 透传

### 8.2 浏览器标签页列表

`GET /api/browser/tabs`

说明：

- 后端已实现
- 当前固定返回空数组 `[]`
- 当前页面没有调用

### 8.3 浏览器 WebSocket 代理

`WS /api/browser/ws?agent_id=default`

说明：

- Java 后端提供了一个 WebSocket 代理，会把前端消息转发到 CoPaw 的 `/api/browser/ws`
- 支持文本和二进制消息转发
- 当前页面的 `BrowserLiveView.vue` 没有直接使用这个接口，当前使用的是 noVNC `RFB` 直连 `rfbUrl`

## 9. 主要代码定位

前端：

- `src/views/Home.vue`
- `src/components/MainContent.vue`
- `src/components/BrowserLiveView.vue`
- `src/components/LeftSidebar.vue`
- `src/api/chat.js`
- `src/api/workbench.js`
- `src/api/browser.js`
- `vite.config.js`

后端：

- `src/main/java/com/staryea/aiops/controller/ChatController.java`
- `src/main/java/com/staryea/aiops/controller/WorkbenchController.java`
- `src/main/java/com/staryea/aiops/controller/BrowserController.java`
- `src/main/java/com/staryea/aiops/service/CopawService.java`
- `src/main/java/com/staryea/aiops/service/WorkbenchService.java`
- `src/main/java/com/staryea/aiops/service/BrowserSessionHubService.java`
- `src/main/java/com/staryea/aiops/websocket/BrowserWebSocketHandler.java`
- `src/main/resources/application.yml`
- `src/main/resources/mapper/TodoTaskMapper.xml`
- `src/main/resources/mapper/FocusEventMapper.xml`
