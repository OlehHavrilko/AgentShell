# AgentShell — Текущее состояние проекта

> Последнее обновление: **07 апреля 2026**
> GitHub: [OlehHavrilko/AgentShell](https://github.com/OlehHavrilko/AgentShell)

---

## 📊 Общая статистика проекта

| Параметр | Значение |
|---|---|
| Модули | 2 (`:agent-core` + `:android-app`) |
| Kotlin-файлов | 134 (72 в agent-core, 33 в android-app, 29 тестов) |
| LLM-провайдеров | 13 |
| UI экранов (Android) | 10 |
| Спринтов завершено | **10** |
| Текущий статус | **Sprint 10 завершён ✅** |

---

## ✅ Sprint 10 — Android Integration + Plugin API + Advanced Systems (ЗАВЕРШЁН)

### 🎯 Основные достижения

Sprint 10 завершён полностью. Все ключевые компоненты реализованы и интегрированы.

### 🔴 Android: Полная интеграция стека

#### Сервисный слой
| Компонент | Файл | Статус |
|---|---|---|
| **AgentRuntimeService** | `service/AgentRuntimeService.kt` | ✅ Foreground service с полным жизненным циклом |
| **AndroidLlmGateway** | `service/AndroidLlmGateway.kt` | ✅ OkHttp-based gateway (обход java.net.http) |
| **SandboxManager** | `service/SandboxManager.kt` | ✅ Управление состоянием sandbox (Idle/Starting/Running/Stopping/Error) |
| **ProotSandbox** | `service/ProotSandbox.kt` | ✅ Streaming output через Flow, MCP channel |
| **McpChannel** | `service/McpChannel.kt` | ✅ JSON-RPC коммуникация с агентом |
| **TermuxConnector** | `service/TermuxConnector.kt` | ✅ Интеграция с Termux окружением |

#### UI слой (Compose)
| Экран | Файл | Статус |
|---|---|---|
| **ChatScreen** | `ui/ChatScreen.kt` | ✅ Dropdown провайдеров, OFFLINE badge, service status |
| **RunsScreen** | `ui/RunsScreen.kt` | ✅ Filter pills (All/RUNNING/COMPLETED/FAILED/CRASHED) |
| **RunDetailScreen** | `ui/RunDetailScreen.kt` | ✅ Timeline шагов со статусом, длительностью, input/output |
| **MemoryScreen** | `ui/MemoryScreen.kt` | ✅ Реальный поиск через AuditDao |
| **SandboxScreen** | `ui/SandboxScreen.kt` | ✅ Dracula-тема, diff-подсветка, 500 строк терминала |
| **SettingsScreen** | `ui/SettingsScreen.kt` | ✅ Export/import package через ZIP |
| **ProvidersScreen** | `ui/ProvidersScreen.kt` | ✅ Управление LLM провайдерами |
| **WorkflowBuilderScreen** | `ui/WorkflowBuilderScreen.kt` | ✅ Визуальный редактор workflow |
| **MainScreen** | `ui/MainScreen.kt` | ✅ 5 вкладок навигации |
| **MainActivity** | `ui/MainActivity.kt` | ✅ Entry point |

#### ViewModel слой
| ViewModel | Файл | Статус |
|---|---|---|
| **ChatViewModel** | `viewmodel/ChatViewModel.kt` | ✅ AndroidViewModel, ServiceConnection, provider selection |
| **RunsViewModel** | `viewmodel/RunsViewModel.kt` | ✅ Управление списком запусков |
| **SandboxViewModel** | `viewmodel/SandboxViewModel.kt` | ✅ Аккумуляция до 500 строк терминала |
| **SettingsViewModel** | `viewmodel/SettingsViewModel.kt` | ✅ Export/import через PackageHelper |

#### База данных (Room)
| Компонент | Файл | Статус |
|---|---|---|
| **AgentDatabase** | `db/AgentDatabase.kt` | ✅ Version 2, Migration 1→2 |
| **RunEntity + RunDao** | `db/RunEntity.kt`, `db/RunDao.kt` | ✅ |
| **StepEntity + StepDao** | `db/StepEntity.kt`, `db/StepDao.kt` | ✅ |
| **AuditEntity + AuditDao** | `db/AuditEntity.kt`, `db/AuditDao.kt` | ✅ |
| **SecretEntity + SecretDao** | `db/SecretEntity.kt`, `db/SecretDao.kt` | ✅ |

**Migration 1→2**: `audit_event.payload` сделан nullable (пересоздание таблицы через temp table)

#### Утилиты
| Утилита | Файл | Статус |
|---|---|---|
| **DexPluginLoader** | `utils/DexPluginLoader.kt` | ✅ DexClassLoader для Android плагинов (.jar/.apk/.dex) |
| **PackageHelper** | `utils/PackageHelper.kt` | ✅ Export/import ZIP пакетов |
| **WorkflowDraft** | `workflow/WorkflowDraft.kt` | ✅ Сериализация workflow в YAML |

### 🔴 Agent Core: LLM провайдеры

#### Реализовано 13 провайдеров

| Провайдер | Файл | Статус по умолчанию |
|---|---|---|
| **OpenAI** | `llm/provider/impl/OpenAiProvider.kt` | ✅ Enabled (gpt-4o-mini) |
| **Ollama** | `llm/provider/impl/OllamaProvider.kt` | ✅ Enabled (llama3.2) |
| **Anthropic** | `llm/provider/impl/AnthropicProvider.kt` | ⚪ Disabled |
| **Azure OpenAI** | `llm/provider/impl/AzureOpenAiProvider.kt` | ⚪ Disabled |
| **Vertex AI** | `llm/provider/impl/VertexAiProvider.kt` | ⚪ Disabled |
| **Cohere** | `llm/provider/impl/CohereProvider.kt` | ⚪ Disabled |
| **Mistral** | `llm/provider/impl/MistralProvider.kt` | ⚪ Disabled |
| **Groq** | `llm/provider/impl/GroqProvider.kt` | ⚪ Disabled |
| **DeepSeek** | `llm/provider/impl/DeepSeekProvider.kt` | ⚪ Disabled |
| **HuggingFace** | `llm/provider/impl/HuggingFaceProvider.kt` | ⚪ Disabled |
| **LlamaCpp** | `llm/provider/impl/LlamaCppProvider.kt` | ⚪ Disabled |
| **OpenRouter** | `llm/provider/impl/OpenRouterProvider.kt` | ⚪ Disabled |
| **Gemini** | `llm/provider/impl/GeminiProvider.kt` | ⚪ Disabled |

#### Инфраструктура провайдеров
- ✅ `LlmProviderRegistry` — регистрация и управление
- ✅ `MultiProviderRouter` — fallback-цепочка
- ✅ `LlmProvidersConfig` — YAML конфигурация
- ✅ `BaseHttpProvider` — базовый HTTP-клиент

### 🟠 Advanced Memory

| Компонент | Файл | Статус |
|---|---|---|
| **MemoryStore (interface)** | `memory/MemoryStore.kt` | ✅ searchByTag(), getAll(), decay support |
| **InMemoryMemoryStore** | `memory/InMemoryMemoryStore.kt` | ✅ Temporal decay: `score * exp(-ln(2) * ageMs / halfLifeMs)` |
| **SqliteMemoryStore** | `memory/SqliteMemoryStore.kt` | ✅ `decay_half_life_ms` колонка, ALTER TABLE migration |
| **MemoryIndexer** | `memory/MemoryIndexer.kt` | ✅ TF-IDF cosine similarity |
| **MemoryAugmentedGateway** | `memory/MemoryAugmentedGateway.kt` | ✅ Автоматическое обогащение промпта |

**REST API (MetricsServer)**:
- ✅ `GET /memory/search?query=&topK=&tag=`
- ✅ `GET /memory/entries`
- ✅ `POST /memory/store`
- ✅ `DELETE /memory/clear`

### 🟡 Orchestrator: Conditional Branching + Retry

| Компонент | Файл | Статус |
|---|---|---|
| **OrchestratorPlan** | `orchestrator/OrchestratorPlan.kt` | ✅ RetryConfig, condition support |
| **Orchestrator** | `orchestrator/Orchestrator.kt` | ✅ runWithRetry(), evaluateCondition(), SKIPPED status |

**Возможности**:
```yaml
agents:
  - id: lint
  - id: fix
    condition: "lint.status == FAILED"
    retry:
      maxAttempts: 3
      delayMs: 1000
      retryOn: [FAILED, CRASHED]
```

**Поддержка**:
- ✅ Условное выполнение (regex: `(\w+)\.(status|summary)\s*(==|!=|contains)\s*(\S+)`)
- ✅ Retry с задержкой и фильтрацией по статусу
- ✅ Статус `SKIPPED` не останавливает план
- ✅ Топологическая сортировка + параллельное выполнение волн

### 🟢 Plugin System

| Компонент | Файл | Статус |
|---|---|---|
| **AgentPlugin (interface)** | `plugin/AgentPlugin.kt` | ✅ id, version, onLoad(), onUnload() |
| **PluginLoader (JVM)** | `plugin/PluginLoader.kt` | ✅ URLClassLoader, MANIFEST.MF Plugin-Class |
| **DexPluginLoader (Android)** | `android-app/utils/DexPluginLoader.kt` | ✅ DexClassLoader, filesDir/plugins/ |

**Поддерживаемые форматы**:
- JVM: `.jar` (через URLClassLoader)
- Android: `.jar`, `.apk`, `.dex` (через DexClassLoader)

### 🔴 Build & Infrastructure

| Компонент | Файл | Статус |
|---|---|---|
| **CI/CD Pipeline** | `.github/workflows/ci.yml` | ✅ 4 jobs: agent-core, rootfs, android-app, libproot |
| **build_proot.sh** | `scripts/build_proot.sh` | ✅ Docker cross-compile для arm64/arm/x86 |
| **build_rootfs.sh** | `scripts/build_rootfs.sh` | ✅ Alpine rootfs.tar.gz |
| **demo.sh** | `scripts/demo.sh` | ✅ Quickstart: Ollama + preset запуск |
| **Dockerfile** | `Dockerfile` | ✅ Multi-stage build |
| **docker-compose.yml** | `docker-compose.yml` | ✅ Agent + Web UI на порту 9090 |

### 📦 Core Runtime Components (Sprints 1-9)

| Категория | Компоненты | Статус |
|---|---|---|
| **Runtime** | AgentRunner, AgentRuntime, WatchdogService, HeartbeatService | ✅ |
| **State Management** | StateStore, SqliteStateStore, InMemoryStateStore | ✅ |
| **Approval & Risk** | ApprovalGate, ApprovalServer, RiskScorer, RuleEngine | ✅ |
| **Execution** | ToolDispatcher, ShellToolExecutor, FileToolExecutor, GitToolExecutor | ✅ |
| **Security** | SecretsVault, SandboxGuard, SchemaValidator | ✅ |
| **LLM Integration** | LlmGateway (4 impl), ContextBudgetManager | ✅ |
| **MCP** | McpClient, McpToolExecutor | ✅ |
| **Audit & Observability** | AuditTrail, SqliteAuditTrail, MetricsServer, MetricsCollector | ✅ |
| **Workflow** | WorkflowLoader, AgentPresetLoader, WorkflowDefinition | ✅ |
| **Utilities** | RetryExecutor, IdempotencyService, RunReportGenerator | ✅ |

### 📋 Agent Presets

3 встроенных пресета:

| Пресет | Файл | Описание |
|---|---|---|
| **code-review** | `resources/agents/code-review.yaml` | git diff + анализ изменений + inline review |
| **git-workflow** | `resources/agents/git-workflow.yaml` | conventional-commit message + PR description |
| **project-scan** | `resources/agents/project-scan.yaml` | сканирование TODOs/FIXMEs + REPORT.md |

### 🧪 Тестовое покрытие

**29 тестовых файлов** покрывают все ключевые компоненты:

- ✅ Runtime: AgentRuntimeTest, ErrorPathTest, WatchdogServiceTest
- ✅ Approval: ApprovalServerTest, ApprovalGateTest
- ✅ LLM: AgentLoopTest, ContextBudgetManagerTest, GatewayConfigTest
- ✅ Providers: LlmProviderRegistryTest, MultiProviderRouterTest, ProviderHttpTest
- ✅ Executor: ToolDispatcherTest
- ✅ Memory: MemoryStoreTest, MemoryIndexerTest, MemoryAugmentedGatewayTest
- ✅ Orchestrator: OrchestratorTest, OrchestratorPlanTest
- ✅ State: SqliteStateStoreTest
- ✅ Security: SecretsVaultTest
- ✅ Audit: SqliteAuditTrailTest
- ✅ Rules: RuleEngineTest
- ✅ Workflow: WorkflowLoaderTest, AgentPresetLoaderTest
- ✅ Observability: MetricsServerTest, MetricsCollectorTest
- ✅ Report: RunReportGeneratorTest
- ✅ Integration: AgentRunnerIntegrationTest

---

## 🔭 Sprint 11 — Планируемые задачи

| Приоритет | Задача | Описание |
|---|---|---|
| 🔴 | **Integration tests** | E2E тест: ChatScreen → AgentRunner → реальный ответ Ollama |
| 🔴 | **WorkflowBuilderScreen save** | `TODO: save and run workflow` — сериализация в YAML + запуск через сервис |
| 🟠 | **Cloud providers** | Включить anthropic, groq, deepseek и т.д. по умолчанию в `llm_providers.yaml` |
| 🟠 | **Plugin Registry UI** | Экран управления плагинами в Android-приложении |
| 🟡 | **Memory Graph** | Визуализация связей между memory entries (знания-граф) |
| 🟡 | **Agent chaining enhancements** | Поддержка `loop` в Orchestrator (повторяющиеся агенты) |
| 🟡 | **EmbeddingProvider** | Заменить TF-IDF на OllamaEmbeddingProvider в MemoryIndexer |
| 🟢 | **Release build** | Подпись APK, ProGuard, минификация, Google Play ready |
| 🟢 | **ConfigRegistry** | runtime.yaml, memory.yaml, plugins/, presets/, policy-packs/ |

---

## 🏗️ Архитектура проекта

### Структура модулей

```
AgentShell/
├── agent-core/              # Pure Kotlin/JVM library
│   ├── runtime/             # AgentRunner, lifecycle, watchdog
│   ├── llm/                 # LlmGateway + 13 providers
│   ├── executor/            # Tool executors (shell, file, git, MCP)
│   ├── state/               # StateStore (SQLite + in-memory)
│   ├── memory/              # MemoryStore + TF-IDF indexing
│   ├── orchestrator/        # Multi-agent coordination
│   ├── approval/            # ApprovalGate + HTTP API
│   ├── rules/               # RuleEngine + YAML risk rules
│   ├── security/            # SecretsVault + SandboxGuard
│   ├── audit/               # AuditTrail + SQLite persistence
│   ├── mcp/                 # MCP client (JSON-RPC stdio)
│   ├── observability/       # MetricsServer + MetricsCollector
│   ├── report/              # RunReportGenerator
│   ├── workflow/            # WorkflowLoader + presets
│   └── plugin/              # PluginLoader (JAR)
│
└── android-app/             # Android Compose UI
    ├── ui/                  # 10 Compose screens
    ├── viewmodel/           # 4 ViewModels
    ├── service/             # AgentRuntimeService + Proot + MCP
    ├── db/                  # Room DB (4 entities + 4 DAOs)
    ├── utils/               # DexPluginLoader + PackageHelper
    └── workflow/            # WorkflowDraft
```

### Коммуникация Android → Agent

```
ChatScreen (Compose)
  └─ ChatViewModel (AndroidViewModel)
       ├─ ServiceConnection → AgentRuntimeService (ForegroundService)
       │    ├─ buildGateway(providerId) → AndroidLlmGateway (OkHttp)
       │    └─ AgentRunner.executeAgentic() [Dispatchers.IO]
       │         └─ EventEmittingAuditTrail → McpChannel._events
       └─ _selectedProvider (SharedPrefs) → provider dropdown
```

### Два режима Sandbox

```
SandboxManager
  └─ ProotSandbox
       ├─ start(outputToFlow=true)  → _output: MutableSharedFlow<String> (терминал)
       └─ start(outputToFlow=false) → openMcpChannel() → McpChannel (агент)
```

Режимы взаимоисключающие.

---

## 🚀 Быстрый старт

### CLI (agent-core)

```bash
git clone https://github.com/OlehHavrilko/AgentShell
cd AgentShell
./scripts/demo.sh
# → http://localhost:9090/ui
```

### Android APK

```bash
./scripts/build_proot.sh         # компилирует libproot.so для arm64/armeabi/x86_64
./gradlew :android-app:assembleRelease
```

### Docker

```bash
docker compose up
# → http://localhost:9090/ui
```

---

## 📈 Прогресс по спринтам

| Sprint | Фокус | Статус |
|---|---|---|
| 1-6 | Core Runtime, Workflow, LLM, MCP, Security, Observability, Web UI | ✅ |
| 7 | Memory Store + Orchestrator | ✅ |
| 8 | Android App (Compose + Room + Service) | ✅ |
| 9 | Multi-Provider LLM (13 провайдеров) | ✅ |
| **10** | **Android Integration + Plugin API + Advanced Systems** | **✅** |
| 11 | Integration tests, Cloud providers, Plugin UI | 📅 Planned |

---

## 🎯 Ключевые возможности

✅ **13 LLM провайдеров** с унифицированным API
✅ **Resume-first** — автоматическое восстановление после краша
✅ **Approval-gated** — YAML-конфигурируемые risk rules
✅ **MCP integration** — динамическая регистрация tools
✅ **SQLite persistence** — полный audit trail с секретами
✅ **Android UI** — 10 Compose screens + Room DB
✅ **Proot sandbox** — изолированное выполнение на Android
✅ **Web UI** — dark-theme dashboard на порту 9090
✅ **Memory system** — TF-IDF search + temporal decay
✅ **Orchestrator** — топологическая сортировка + conditional branching + retry
✅ **Plugin system** — JAR (JVM) + DEX (Android)

---

## 📝 Заключение

**Sprint 10 полностью завершён.** Все критические компоненты реализованы и интегрированы. Проект готов к переходу на Sprint 11.

**Основные достижения Sprint 10**:
- Полная интеграция Android stack (UI → ViewModel → Service → agent-core)
- 13 LLM провайдеров с fallback-routing
- Advanced Memory с temporal decay
- Orchestrator с условным выполнением и retry
- Plugin system для расширяемости
- CI/CD pipeline с 4 build jobs

**Следующие шаги** (Sprint 11):
- E2E integration тесты
- Сохранение и запуск workflow из UI
- Включение cloud-провайдеров по умолчанию
- Plugin Registry UI
