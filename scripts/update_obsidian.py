#!/usr/bin/env python3
"""Update Obsidian vault notes for Sprint 10."""
import pathlib

BASE = pathlib.Path('/home/oleh/vaults/vaulmain/AgentShell')

# ── Index ─────────────────────────────────────────────────────────────────────
INDEX = """# AgentShell - Index

> Resume-first, approval-gated agent runtime + Android-приложение (Compose + Proot sandbox) + 13 LLM-провайдеров
> **Sprint 10 (Android Integration + Plugin API)** | **160+ тестов** | [GitHub](https://github.com/OlehHavrilko/AgentShell)

---

## 📌 Быстрый доступ

| | |
|---|---|
| 🗺️ **Текущий статус и план** | [[AgentShell — Текущее состояние и следующие шаги]] |
| 🏛️ **Архитектура** | [[AgentShell - Architecture Overview]] |
| 📦 **Общий план** | [[AgentShell — расширенный общий план]] |

---

## 🔧 Ключевые компоненты

| Компонент | Пакет | Sprint |
|---|---|---|
| `AgentRunner` | `runtime` | 1 |
| `SqliteStateStore` | `state.sqlite` | 1 |
| `WorkflowLoader` | `workflow` | 2 |
| `AuditTrail` | `audit` | 2 |
| `ApprovalServer` | `approval` | 2 |
| `LlmGateway` + gateways | `llm` | 3 |
| `McpClient` | `mcp` | 3 |
| `SecretsVault` | `security` | 4 |
| `RuleEngine` | `rules` | 4 |
| `WatchdogService` | `runtime` | 4 |
| `MetricsServer` | `observability` | 5 |
| `RunReportGenerator` | `report` | 5 |
| `AgentPresetLoader` | `workflow` | 6 |
| Web UI (`/ui`) | `resources/ui` | 6 |
| `MemoryStore` | `memory` | 7 |
| `MemoryAugmentedGateway` | `memory` | 7 |
| `Orchestrator` | `orchestrator` | 7 |
| `AgentRuntimeService` | `android-app/service` | 8 |
| `ProotSandbox` | `android-app/service` | 8 |
| `McpChannel` | `android-app/service` | 8 |
| `AgentDatabase` (Room) | `android-app/db` | 8 |
| `LlmProvider` + 13 impl | `llm/provider/impl` | 9 |
| `LlmProviderRegistry` | `llm/provider` | 9 |
| `MultiProviderRouter` | `llm/provider` | 9 |
| `LlmProvidersLoader` | `llm/provider` | 9 |
| **`AndroidLlmGateway`** | **`android-app/service`** | **10** |
| **`SandboxManager` + `SandboxState`** | **`android-app/service`** | **10** |
| **`SandboxViewModel` + `SandboxScreen`** | **`android-app/ui`** | **10** |
| **`InMemoryMemoryStore` (decay)** | **`memory`** | **10** |
| **`Orchestrator` (retry + condition)** | **`orchestrator`** | **10** |
| **`AgentPlugin` + `PluginLoader`** | **`plugin`** | **10** |
| **`DexPluginLoader`** | **`android-app/utils`** | **10** |

---

## 🚀 Запуск за 3 команды

```bash
# CLI (agent-core)
git clone https://github.com/OlehHavrilko/AgentShell
cd AgentShell && ./scripts/demo.sh
# → http://localhost:9090/ui

# Android APK
./scripts/build_proot.sh         # компилирует libproot.so для arm64/armeabi/x86_64 (Docker)
./gradlew :android-app:assembleRelease
```

---

## 📊 Метрики проекта

| Параметр | Значение |
|---|---|
| Модули | 2 (`agent-core` + `android-app`) |
| Kotlin-файлов | ~95 |
| LLM-провайдеров | 13 |
| Спринтов завершено | **10** |
| Текущий спринт | **Sprint 10 ✅** |
"""

# ── Текущее состояние и следующие шаги ───────────────────────────────────────
CURRENT_STATE = """# AgentShell — Текущее состояние и следующие шаги

> Последнее обновление: **Sprint 10 (Android Integration + Plugin API)** | **07.04.2026**
> GitHub: [OlehHavrilko/AgentShell](https://github.com/OlehHavrilko/AgentShell) | Ветка: `main`

---

## ✅ Sprint 10 — Android Integration + Advanced Systems (ЗАВЕРШЁН)

### 🔴 Android: ChatViewModel → AgentRunner через McpChannel

| Файл | Что сделано |
|---|---|
| `AndroidLlmGateway.kt` | OkHttp-based OpenAI-совместимый gateway, обходит `java.net.http.HttpClient` (нет на Android < API 30) |
| `SandboxManager.kt` | `sealed class SandboxState { Idle, Starting, Running, Stopping, Error }` + lifecycle управление |
| `ProotSandbox.kt` | `outputFlow: Flow<String>` + `openMcpChannel()` + параметр `outputToFlow: Boolean` |
| `SandboxViewModel.kt` | Аккумулирует до 500 строк терминала из `outputFlow` |
| `SandboxScreen.kt` | Dracula-тема, diff-подсветка (`+` = зелёный, `-` = красный), sticky header |
| `AgentRuntimeService.kt` | `ACTION_START_JVM`, `buildGateway()` для 5 провайдеров, `EventEmittingAuditTrail` |
| `ChatViewModel.kt` | `AndroidViewModel`, `ServiceConnection` к сервису, `_selectedProvider` из SharedPrefs |
| `ChatScreen.kt` | Dropdown провайдеров (10 шт), OFFLINE badge для ollama/llama_cpp, service status badge |
| `RunsScreen.kt` | Filter pills: All / RUNNING / COMPLETED / FAILED / CRASHED с счётчиками |
| `RunDetailScreen.kt` | `ViewModelFactory`, timeline шагов со статусом, длительностью, input/output |
| `AuditDao.kt` | `recentEvents(limit)` + `searchEvents(query)` Room-запросы |
| `AuditEntity.kt` | `payload: String?` — сделан nullable |
| `MemoryScreen.kt` | `MemoryViewModel` с реальным AuditDao-поиском |
| `SettingsViewModel.kt` | `exportPackage()` / `importPackage(uri: Uri)` через PackageHelper |
| `SettingsScreen.kt` | File picker (`ActivityResultContracts.GetContent`) для импорта ZIP |
| `MainScreen.kt` | 5 вкладок: Chat / Runs / Memory / Settings / **Sandbox**, маршрут `workflow_builder` |
| `AgentDatabase.kt` | **version=2**, `MIGRATION_1_2` пересоздаёт `audit_event` с nullable payload |

### 🔴 rootfs + libproot.so

| Файл | Что сделано |
|---|---|
| `scripts/build_proot.sh` | Docker cross-compile proot v5.4.0 для arm64-v8a / armeabi-v7a / x86_64 |
| `.github/workflows/ci.yml` | 4 job: `agent-core`, `rootfs`, `android-app`, `libproot` |

### 🟠 Advanced Memory

| Файл | Что сделано |
|---|---|
| `MemoryStore.kt` | `decayHalfLifeMs` в `MemoryEntry`, `searchByTag()`, `getAll()` в интерфейсе |
| `InMemoryMemoryStore.kt` | Временной decay: `score * exp(-ln(2) * ageMs / halfLifeMs)` |
| `SqliteMemoryStore.kt` | Колонка `decay_half_life_ms` + ALTER TABLE migration, делегирует `searchByTag`/`getAll` |
| `MetricsServer.kt` | REST: `GET /memory/search`, `GET /memory/entries`, `POST /memory/store`, `DELETE /memory/clear` |

### 🟡 Orchestrator Enhancements

| Файл | Что сделано |
|---|---|
| `OrchestratorPlan.kt` | `RetryConfig(maxAttempts, delayMs, retryOn)`, `condition: String?` в `AgentSpec` |
| `Orchestrator.kt` | `runWithRetry()`, `evaluateCondition()` (regex: `agent.status == X`), статус `SKIPPED` |

### 🟢 Plugin API

| Файл | Что сделано |
|---|---|
| `AgentPlugin.kt` | `interface AgentPlugin { id, version, onLoad(registry, context), onUnload() }` |
| `PluginLoader.kt` | JVM JAR-загрузчик, `URLClassLoader`, читает `Plugin-Class` из MANIFEST.MF |
| `DexPluginLoader.kt` | Android `DexClassLoader`, `filesDir/plugins/`, поддержка `.jar`/`.apk`/`.dex` |

---

## 🏁 Спринты 1–9 (завершены)

### Sprint 9 — Multi-Provider LLM ✅
- 13 LLM-провайдеров: OpenAI, Anthropic, Azure, Vertex AI, Cohere, Mistral, Groq, DeepSeek, HuggingFace, OpenRouter, Gemini, Ollama, LlamaCpp
- `LlmProviderRegistry`, `MultiProviderRouter` (fallback-цепочка), `LlmProvidersLoader` (YAML + env vars)
- `ProvidersScreen` в Android

### Sprint 8 — Android App ✅
- Мультимодульный Gradle: `:agent-core` + `:android-app`
- Compose UI: Chat, Runs, Memory, Settings, WorkflowBuilder
- `AgentRuntimeService` (foreground), `ProotSandbox`, `TermuxConnector`, `McpChannel`
- Room: `AgentDatabase` + 4 Entity + 4 DAO

### Sprint 7 — Memory + Orchestrator ✅
- `MemoryStore` (TF-IDF cosine similarity, SQLite + in-memory)
- `MemoryAugmentedGateway` — автоматически обогащает промпт из памяти
- `Orchestrator` — топологическая сортировка, параллельное выполнение волн

### Sprint 1–6 ✅
- Core Runtime, Workflow+Audit, LLM+MCP, Secrets+Rules+Watchdog, Observability, Web UI

---

## 🔭 Sprint 11 — Планируемые задачи

| Приоритет | Задача | Описание |
|---|---|---|
| 🔴 | Integration tests | E2E тест: ChatScreen → AgentRunner → реальный ответ Ollama |
| 🔴 | WorkflowBuilderScreen | `TODO: save and run workflow` — сериализация в YAML + запуск через сервис |
| 🟠 | `llm_providers.yaml` | Включить cloud-провайдеров (anthropic, groq и т.д.) по умолчанию |
| 🟠 | Plugin Registry UI | Экран управления плагинами в Android-приложении |
| 🟡 | Memory Graph | Визуализация связей между memory entries (знания-граф) |
| 🟡 | Agent chaining enhancements | Поддержка `loop` в Orchestrator (повторяющиеся агенты) |
| 🟢 | Release build | Подпись APK, ProGuard, минификация, Google Play ready |
"""

# ── Architecture Overview (добавляем Sprint 10 блок) ─────────────────────────
arch_path = BASE / '10_Architecture/AgentShell - Architecture Overview.md'
current_arch = arch_path.read_text(encoding='utf-8')

SPRINT10_ARCH = """
---

## Sprint 10 — Android Integration & Plugin System

### Android: Full Integration Stack

```
ChatScreen (Compose)
  └─ ChatViewModel (AndroidViewModel)
       ├─ ServiceConnection → AgentRuntimeService (ForegroundService)
       │    ├─ buildGateway(providerId) → AndroidLlmGateway (OkHttp)
       │    └─ AgentRunner.executeAgentic() [Dispatchers.IO]
       │         └─ EventEmittingAuditTrail → AgentMessage → McpChannel._events
       └─ _selectedProvider (SharedPrefs) → provider dropdown
```

### AndroidLlmGateway
- Реализует `dev.agentshell.llm.LlmGateway`
- Использует OkHttp вместо `java.net.http.HttpClient` (нет на Android < API 30)
- Поддерживает: OpenAI, Ollama (`localhost:11434/v1`), Groq, DeepSeek, Mistral, OpenRouter

### SandboxManager / ProotSandbox
```
SandboxManager
  └─ ProotSandbox
       ├─ start(outputToFlow=true)  → _output: MutableSharedFlow<String> (терминал)
       └─ start(outputToFlow=false) → openMcpChannel() → McpChannel (агент)
```
Два режима взаимоисключающие.

### Advanced Memory: Temporal Decay
```
score_final = score_tfidf * exp(-ln(2) * ageMs / halfLifeMs)
```
- `halfLifeMs = Long.MAX_VALUE` → без decay (default)
- REST: `GET /memory/search?query=&topK=&tag=` | `POST /memory/store` | `DELETE /memory/clear`

### Orchestrator: Condition + Retry
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
- `evaluateCondition()` — regex `(\\w+)\\.(status|summary)\\s*(==|!=|contains)\\s*(\\S+)`
- Статус `SKIPPED` не останавливает план

### Plugin System
```
JVM:     PluginLoader → URLClassLoader → MANIFEST.MF Plugin-Class
Android: DexPluginLoader → DexClassLoader → filesDir/plugins/
```

### Room DB Migration v1→v2
- `audit_event.payload TEXT NOT NULL` → `TEXT` (nullable)
- `MIGRATION_1_2`: пересоздание таблицы через temp table (SQLite не поддерживает ALTER COLUMN)
"""

if 'Sprint 10' not in current_arch:
    arch_path.write_text(current_arch.rstrip() + SPRINT10_ARCH, encoding='utf-8')
    print("Architecture OK (appended)")
else:
    print("Architecture already has Sprint 10")

# Write main files
(BASE / '00_Index/AgentShell - Index.md').write_text(INDEX, encoding='utf-8')
print("Index OK")

(BASE / '40_Roadmap/AgentShell — Текущее состояние и следующие шаги.md').write_text(CURRENT_STATE, encoding='utf-8')
print("Current state OK")

print("\nAll Obsidian notes updated successfully!")
